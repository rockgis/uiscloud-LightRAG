# LiteLLM 프록시를 통한 로컬 모델 연동 가이드

LiteLLM 프록시로 서빙되는 로컬/온프레미스 모델(예: Qwen3, Llama 등)을 LightRAG에 연결하는 방법과, 특히 **thinking(추론) 모드 모델** 사용 시 필요한 설정을 설명합니다.

---

## 개요

LiteLLM은 OpenAI 호환 API 형식으로 다양한 모델을 서빙하는 프록시입니다. LightRAG는 `LLM_BINDING=openai`로 설정하면 LiteLLM 프록시를 포함한 모든 OpenAI 호환 엔드포인트를 사용할 수 있습니다.

---

## 기본 설정 (`.env`)

```env
###########################
### LLM Configuration
###########################
LLM_BINDING=openai
LLM_BINDING_HOST=http://<프록시-IP>:4000/v1
LLM_BINDING_API_KEY=<프록시-API-키>
LLM_MODEL=<모델명>
LLM_TIMEOUT=300
MAX_ASYNC=1

###########################
### Embedding Configuration
###########################
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=http://<프록시-IP>:4000/v1
EMBEDDING_BINDING_API_KEY=<프록시-API-키>
EMBEDDING_MODEL=<임베딩-모델명>
EMBEDDING_DIM=1024
EMBEDDING_MAX_TOKEN_SIZE=8192
```

### 설정 값 가이드

| 항목 | 권장값 | 설명 |
|------|--------|------|
| `LLM_TIMEOUT` | 300 | LLM 단일 호출 타임아웃(초). Thinking 모드 모델은 200s 이상 필요 |
| `MAX_ASYNC` | 1 | 동시 LLM 요청 수. 로컬 GPU 1대 기준 1 권장 |
| `MAX_GLEANING` | 0 | 추출 후 보완 호출 횟수. Thinking 모델은 0으로 설정해 호출 수 최소화 |
| `ENTITY_EXTRACTION_USE_JSON` | false | JSON 모드는 일부 모델에서 빈 응답 반환. false 권장 |

---

## Thinking 모드 모델 전용 설정

Qwen3, DeepSeek-R1 등 내부 추론(thinking)을 수행하는 모델은 **응답 생성 시간이 매우 길 수 있습니다.** 아래 설정으로 안정성을 확보하세요.

### Thinking Budget 설정

`thinking_budget`은 LiteLLM을 통해 Qwen3 모델의 최대 thinking 토큰 수를 제한합니다. 제한 없이 실행하면 1200자 청크 처리 시 300초 이상 소요될 수 있습니다.

```env
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"thinking_budget": 8000}}
```

| thinking_budget | 예상 thinking 시간 | 비고 |
|----------------|-------------------|------|
| 4000 토큰 | ~67s | 간단한 문서에 적합 |
| 8000 토큰 | ~133s | 일반 문서 처리 권장값 |
| 16000 토큰 | ~267s | 복잡한 기술 문서 |
| 설정 안 함 | 300s 초과 가능 | 타임아웃 위험 |

> **주의:** `enable_thinking: false`로 thinking을 완전히 비활성화하면 엔티티 추출 결과가 빈 `<DONE>`만 반환될 수 있습니다. 완전 비활성화는 권장하지 않습니다.

---

## LiteLLM Cooldown 문제 대응

LiteLLM은 모델에 오류가 발생하면 60초 간 해당 모델을 **cooldown(대기)** 상태로 전환하고 HTTP 429를 반환합니다. 기본 LightRAG 설정은 이를 처리하기에 재시도 간격이 너무 짧습니다.

### 코드 수정 필요 사항

**`lightrag/llm/openai.py`** (line 211-213):

```python
# 변경 전 (기본값)
@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=4, max=10),  # 최대 10초 대기

# 변경 후 (LiteLLM cooldown 60초 대응)
@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=2, min=30, max=70),  # 최소 30초 대기
```

이 변경으로:
- 재시도 횟수: 3회 → 5회
- 최소 대기: 4s → 30s (cooldown 60s 동안 2회 재시도 후 성공)
- 최대 대기: 10s → 70s

**`lightrag/utils.py`** (line 895-901):

```python
# 변경 전
max_execution_timeout = llm_timeout * 2   # LLM_TIMEOUT=300 → 600s

# 변경 후
max_execution_timeout = llm_timeout * 8   # LLM_TIMEOUT=300 → 2400s
```

재시도 포함 전체 처리 시간(5회 × 300s + 4회 × 70s = 1780s)이 Worker 타임아웃 내에 들어오도록 배수를 8로 변경합니다.

### `remove_think_tags()` 개선

**`lightrag/utils.py`** `remove_think_tags` 함수에 truncated thinking 처리 추가:

```python
def remove_think_tags(text: str) -> str:
    # (기존 로직)
    text = re.sub(r"^((?!<think>).)*?</think>", "", text, flags=re.DOTALL)
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.DOTALL)

    # 추가: <think>는 있지만 </think>가 없는 경우 (타임아웃으로 잘린 경우)
    if "<think>" in text and "</think>" not in text:
        pre_think = text[: text.index("<think>")].strip()
        return pre_think  # <think> 이전 내용만 반환 (보통 빈 문자열)
    return text.strip()
```

타임아웃으로 thinking이 잘리면 `</think>` 태그가 없어서 22,000자 이상의 thinking 내용이 캐시에 저장되는 문제를 방지합니다.

---

## 전체 권장 `.env` (Qwen3 + bge-m3 예시)

```env
###########################
### Server Configuration
###########################
HOST=0.0.0.0
PORT=9621
WEBUI_TITLE='My Graph KB'
WEBUI_DESCRIPTION='Simple and Fast Graph Based RAG System'

###########################
### LLM Configuration
###########################
LLM_BINDING=openai
LLM_BINDING_HOST=http://192.168.0.210:4000/v1
LLM_BINDING_API_KEY=sk-dgx-proxy
LLM_MODEL=Qwen3-30B-A3B
LLM_TIMEOUT=300
MAX_ASYNC=1

###########################
### Embedding Configuration
###########################
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=http://192.168.0.210:4000/v1
EMBEDDING_BINDING_API_KEY=sk-dgx-proxy
EMBEDDING_MODEL=bge-m3
EMBEDDING_DIM=1024
EMBEDDING_MAX_TOKEN_SIZE=8192

###########################
### Storage Configuration (lightweight, file-based)
###########################
LIGHTRAG_KV_STORAGE=JsonKVStorage
LIGHTRAG_VECTOR_STORAGE=NanoVectorDBStorage
LIGHTRAG_GRAPH_STORAGE=NetworkXStorage
LIGHTRAG_DOC_STATUS_STORAGE=JsonDocStatusStorage

###########################
### Pipeline Configuration
###########################
MAX_PARALLEL_INSERT=2
MAX_GLEANING=0
SUMMARY_LANGUAGE=Korean
ENTITY_EXTRACTION_USE_JSON=false

###########################
### Parser Configuration
###########################
LIGHTRAG_PARSER=*:native-teP,*:legacy-R
VLM_PROCESS_ENABLE=false

###########################
### Rerank (disabled)
###########################
RERANK_BINDING=null

###########################
### Qwen3 Thinking Budget
###########################
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"thinking_budget": 8000}}
```

---

## Docker 빌드 및 실행

코드 수정 후 이미지를 재빌드해야 합니다.

```bash
# 이미지 빌드
docker compose build

# 서비스 시작
docker compose up -d

# 상태 확인
curl http://localhost:9621/health | python3 -m json.tool
```

---

## 문서 처리 운영

```bash
# 문서 업로드
curl -X POST http://localhost:9621/documents/upload \
  -F "file=@/path/to/document.md"

# 처리 상태 확인
curl http://localhost:9621/documents

# LLM 캐시 초기화 (캐시 오염 시)
curl -X POST http://localhost:9621/documents/clear_cache \
  -H "Content-Type: application/json" \
  -d '{"doc_ids": []}'

# 실패 문서 재처리
curl -X POST http://localhost:9621/documents/reprocess_failed \
  -H "Content-Type: application/json"
```

---

## 문제 해결

### 증상: Worker execution timeout after 600s

**원인:** `max_execution_timeout = llm_timeout * 2`(기본값)가 너무 짧음.  
**해결:** `lightrag/utils.py`에서 배수를 8로 변경 후 재빌드.

### 증상: 0 entities extracted, LLM output format error

**원인 1:** Thinking이 타임아웃으로 잘려 `</think>` 없는 가비지가 캐시에 저장됨.  
**해결:** 캐시 초기화 → `remove_think_tags()` 개선 코드 적용 → 재빌드 → 재처리.

**원인 2:** `ENTITY_EXTRACTION_USE_JSON=true` 시 Qwen3가 빈 JSON `{}` 반환.  
**해결:** `.env`에서 `ENTITY_EXTRACTION_USE_JSON=false`로 변경.

### 증상: Rate limit error (HTTP 429) / RetryError

**원인:** LiteLLM 60초 cooldown 동안 모든 재시도 실패.  
**해결:** `lightrag/llm/openai.py`의 `wait_exponential(min=30, max=70)`으로 변경 후 재빌드.

### 증상: 응답 시간이 300초 이상

**원인:** Thinking 모델이 제한 없이 thinking 토큰을 생성.  
**해결:** `.env`에 `OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"thinking_budget": 8000}}` 추가.
