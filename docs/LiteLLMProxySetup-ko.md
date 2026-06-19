# LiteLLM 프록시를 통한 로컬 모델 연동 가이드

LiteLLM 프록시로 서빙되는 로컬/온프레미스 모델(예: Qwen3, EXAONE, Llama 등)을 LightRAG에 연결하는 방법과, 특히 **thinking(추론) 모드 모델** 및 **AWQ 양자화 모델** 사용 시 필요한 설정을 설명합니다.

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

> **주의 (LiteLLM 경유 시):** LiteLLM 프록시의 `drop_params: true` 설정이 활성화된 경우, `chat_template_kwargs`가 제거되어 `enable_thinking: false`가 적용되지 않습니다. 이 경우 아래 "AWQ 모델 직접 vLLM 연결" 섹션을 참고하세요.
>
> **직접 vLLM 연결 시:** `enable_thinking: false`는 안전하게 동작합니다. `/no_think` prefix와 함께 사용하면 더욱 안정적입니다.

---

## AWQ 양자화 모델 전용 설정 (Qwen3-AWQ)

Qwen3-30B-A3B-awq 등 AWQ 양자화 모델은 **긴 system prompt에서 garbage 출력** 문제가 있습니다.

### 문제 설명

| 조건 | 결과 |
|------|------|
| System prompt ≤ ~4,000 토큰 | 정상 출력 |
| System prompt > ~5,000 토큰 | Garbage 출력 (한/영 혼합 무의미 문자열) |

LightRAG의 기본 설정(`MAX_TOTAL_TOKENS=30,000`)에서는 KG 컨텍스트가 포함된 system prompt가 22,000 토큰을 초과할 수 있어 모든 쿼리 응답이 garbage로 반환됩니다.

### 해결책 1: 컨텍스트 예산 및 생성 토큰 제한

`.env`에 다음을 추가합니다:

```env
MAX_TOTAL_TOKENS=4000      # 총 컨텍스트 예산 (기본 30,000 → 4,000)
MAX_ENTITY_TOKENS=1000     # 엔티티 컨텍스트 상한 (기본 6,000 → 1,000)
MAX_RELATION_TOKENS=1000   # 관계 컨텍스트 상한 (기본 8,000 → 1,000)
OPENAI_LLM_MAX_TOKENS=700  # 생성 토큰 상한 (반복 루프 방지)
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}, "repetition_penalty": 1.15}
```

이 설정으로 system prompt를 ~4,000 토큰 이하로 유지하고, 생성 토큰 상한을 700으로 제한해 총 시퀀스 길이(입력+출력)가 AWQ 모델의 안정 임계값(~4,600 토큰)을 넘지 않도록 합니다.

> **왜 700인가?** Qwen3-AWQ는 총 토큰 수(입력+출력)가 ~3,500~5,000 범위를 넘으면 반복 루프(garbage 또는 동일 문구 반복) 증상이 나타납니다. naive 모드 기준 입력 ~2,950 토큰 + 출력 700 토큰 = ~3,650 토큰으로 안전 범위 내에 유지됩니다. 600 토큰에서도 안전하나 응답이 중간에 잘리는 경우가 있어 700으로 상향했습니다.

> **`repetition_penalty`란?** vLLM `extra_body`로 전달되는 반복 억제 파라미터입니다. 1.15는 동일 토큰이 반복될수록 생성 확률을 낮춥니다. 1.0(기본)에서는 반복이 가속되는 경향이 있으며, 1.2 이상은 출력 품질이 저하될 수 있습니다. LiteLLM 경유 시 `drop_params: true`에 의해 무시될 수 있으므로 **직접 vLLM 연결에서만** 안정적으로 적용됩니다.

### 해결책 2: Thinking 완전 비활성화 (AWQ 권장)

AWQ 모델은 thinking 예산(`thinking_budget`)보다 완전 비활성화(`enable_thinking: false`)가 안정적입니다. 단, **LiteLLM을 우회하여 vLLM에 직접 연결**해야 합니다.

```env
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}}
```

### 해결책 3: `/no_think` prefix (프롬프트 레벨 방어)

`lightrag/prompt.py`(서버에서는 override 디렉토리)의 주요 프롬프트 시작에 `/no_think`를 추가합니다:

```python
PROMPTS["rag_response"] = """/no_think
---Role---
You are an expert AI assistant...
```

대상 프롬프트:
- `rag_response`
- `naive_rag_response`
- `keywords_extraction`

`enable_thinking: false`와 함께 사용하면 defense-in-depth 효과를 냅니다.

### 해결책 4: 출력 품질 개선 (`prompt.py` 수정)

AWQ 모델에서 발생하는 한국어 일관성 저하 및 References 형식 문제는 프롬프트 지시로 일부 완화할 수 있습니다. `rag_response`와 `naive_rag_response` 양쪽에 아래 섹션을 추가합니다.

**한국어 일관성 지시 추가 (Section 3):**
```
3. Formatting & Language:
  - The response MUST be in the same language as the user query.
  - Do NOT mix Korean and English for the same concept (e.g., write '운영절차' not '운Operating절차').
  - Technical acronyms (ITSM, CAB, RFC, etc.) may remain in English.
```

**References 형식 지시 추가 (Section 4):**
```
4. References Section Format:
  - The References section MUST use the exact heading: `### References`
  - Use markdown list format only: `- [n] 파일명` — do NOT use a code block.
  - The filename MUST be copied EXACTLY as it appears in the Reference Document List.
  - Provide maximum of 5 most relevant citations.
  - Do not generate anything after the References section.
```

> **한계:** AWQ 양자화 모델은 한국어 파일명을 자동 번역/변형하는 경향이 있습니다. 프롬프트 지시만으로는 완전히 방지되지 않으므로, 파일명 보존은 반드시 `_replace_references_section()` 후처리 코드 수정(아래 "문제 해결" 섹션 참고)과 병행해야 합니다.

---

## LiteLLM vs 직접 vLLM 연결 비교

| 항목 | LiteLLM 경유 | 직접 vLLM |
|------|-------------|-----------|
| `enable_thinking` 제어 | `drop_params: true`가 표준 파라미터만 제거 → `chat_template_kwargs`는 비표준이므로 **그대로 전달됨** | 항상 적용 |
| `thinking_budget` 제어 | 위와 동일 (`chat_template_kwargs` 내부 값이므로 전달됨) | 항상 적용 |
| `max_tokens` 제어 | `drop_params: true`로 **제거됨** → `OPENAI_LLM_MAX_TOKENS` 설정이 적용 안 될 수 있음 | 항상 적용 |
| `repetition_penalty` 제어 | `drop_params: true`로 제거됨 | 항상 적용 |
| 모델 부하 분산 | 가능 | 불가 |
| AWQ 모델 안정성 | `chat_template_kwargs` 계열은 안정. `max_tokens` 등 표준 파라미터는 주의 | 높음 |
| 임베딩 모델 연결 | 가능 | 가능 |

> **`drop_params: true` 동작 상세:**  
> LiteLLM의 `drop_params: true`는 모델이 지원하지 않는 **표준 OpenAI 파라미터**(`temperature`, `top_p`, `max_tokens` 등)를 제거합니다.  
> `chat_template_kwargs`, `repetition_penalty` 등 **비표준 `extra_body` 파라미터**는 표준 파라미터가 아니므로 그대로 하위 엔드포인트(vLLM)로 전달됩니다.  
> 단, `max_tokens` 자체는 표준 파라미터이므로 `drop_params: true` 환경에서는 제거될 수 있습니다.

**권장:** AWQ 모델 LLM은 직접 vLLM 연결, 임베딩 모델은 LiteLLM 경유를 혼합 사용하세요.  
단, EXAONE처럼 thinking 비활성화만 필요한 경우에는 LiteLLM 경유도 안정적으로 동작합니다(`chat_template_kwargs`가 전달되므로).

```env
# AWQ LLM: 직접 vLLM
LLM_BINDING_HOST=http://192.168.0.210:8010/v1
LLM_BINDING_API_KEY=none
LLM_MODEL=Qwen3-30B-A3B-awq

# 임베딩: LiteLLM 경유
EMBEDDING_BINDING_HOST=http://192.168.0.210:4000/v1
EMBEDDING_BINDING_API_KEY=sk-dgx-proxy
EMBEDDING_MODEL=bge-m3
```

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

## 전체 권장 `.env`

### 패턴 A: Qwen3-AWQ + bge-m3 (AWQ 직접 vLLM, 임베딩 LiteLLM)

현재 검증된 안정 구성입니다.

```env
###########################
### Server Configuration
###########################
HOST=0.0.0.0
PORT=9621
WEBUI_TITLE='My Graph KB'
WEBUI_DESCRIPTION='Simple and Fast Graph Based RAG System'

###########################
### LLM Configuration (직접 vLLM - AWQ 모델 권장)
###########################
LLM_BINDING=openai
LLM_BINDING_HOST=http://192.168.0.210:8010/v1
LLM_BINDING_API_KEY=none
LLM_MODEL=Qwen3-30B-A3B-awq
LLM_TIMEOUT=600
MAX_ASYNC=1

###########################
### Embedding Configuration (LiteLLM 경유)
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
### Qwen3-AWQ 출력 제어
### - enable_thinking: false → thinking 완전 비활성화 (직접 vLLM에서만 적용됨)
### - /no_think prefix → prompt.py에서 defense-in-depth로 추가
### - MAX_TOTAL_TOKENS=4000 → AWQ 모델 garbage 방지 (5,000 토큰 초과 시 garbage 출력)
### - OPENAI_LLM_MAX_TOKENS=700 → 입력+출력 총 토큰을 ~3,650 이하로 유지 (반복 루프 방지)
### - repetition_penalty=1.15 → 토큰 레벨 반복 억제 (직접 vLLM extra_body로 전달)
###########################
OPENAI_LLM_MAX_TOKENS=700
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}, "repetition_penalty": 1.15}
MAX_TOTAL_TOKENS=4000
MAX_ENTITY_TOKENS=1000
MAX_RELATION_TOKENS=1000
```

### 패턴 B: Qwen3 (non-AWQ) + LiteLLM 전체 경유

```env
LLM_BINDING_HOST=http://192.168.0.210:4000/v1
LLM_BINDING_API_KEY=sk-dgx-proxy
LLM_MODEL=Qwen3-30B-A3B
LLM_TIMEOUT=300

# thinking 예산 제한 (LiteLLM drop_params=false 환경에서만 동작)
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"thinking_budget": 8000}}
```

### 패턴 C: EXAONE-4.5-33B-AWQ + bge-m3 (LLM·임베딩 모두 LiteLLM 경유)

EXAONE-4.5-33B-AWQ를 LiteLLM 프록시를 통해 사용하는 구성입니다.  
`enable_thinking: false`는 `chat_template_kwargs` 내부 파라미터이므로 `drop_params: true` 환경에서도 정상적으로 vLLM으로 전달됩니다.

```env
###########################
### LLM Configuration (LiteLLM 경유 - EXAONE AWQ)
###########################
LLM_BINDING=openai
LLM_BINDING_HOST=http://192.168.0.210:4000/v1
LLM_BINDING_API_KEY=sk-dgx-proxy
LLM_MODEL=EXAONE-4.5-33B
LLM_TIMEOUT=600
MAX_ASYNC=1

###########################
### Embedding Configuration (LiteLLM 경유)
###########################
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=http://192.168.0.210:4000/v1
EMBEDDING_BINDING_API_KEY=sk-dgx-proxy
EMBEDDING_MODEL=bge-m3
EMBEDDING_DIM=1024
EMBEDDING_MAX_TOKEN_SIZE=8192

###########################
### EXAONE-AWQ 출력 제어
### - enable_thinking: false → EXAONE 4.5의 thinking 비활성화
###   (chat_template_kwargs는 비표준 파라미터 → LiteLLM drop_params에 의해 제거되지 않음)
### - repetition_penalty → extra_body 비표준이므로 vLLM으로 전달됨
### - max_tokens는 LiteLLM drop_params: true로 제거될 수 있으므로
###   OPENAI_LLM_MAX_TOKENS 대신 vLLM --max-model-len 으로 제어
### - MAX_TOTAL_TOKENS=4000 → AWQ garbage 방지
###########################
OPENAI_LLM_MAX_TOKENS=800
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}, "repetition_penalty": 1.1}
MAX_TOTAL_TOKENS=4000
MAX_ENTITY_TOKENS=1000
MAX_RELATION_TOKENS=1000
```

**LiteLLM `litellm_config.yaml` 참고 (EXAONE 라우팅):**

```yaml
model_list:
  - model_name: EXAONE-4.5-33B
    litellm_params:
      model: openai/EXAONE-4.5-33B-AWQ
      api_base: http://192.168.0.210:8012/v1
      api_key: none

litellm_settings:
  drop_params: true  # 표준 파라미터(max_tokens 등) 제거, chat_template_kwargs는 제거 안 됨
```

> **`OPENAI_LLM_MAX_TOKENS`와 `drop_params: true` 주의사항:**  
> `max_tokens`는 표준 OpenAI 파라미터이므로 `drop_params: true` 환경에서 제거될 수 있습니다.  
> LightRAG `health` API에서 `provider_options.max_tokens`가 보이지 않는다면 보안 마스킹(`_SECRET_MARKERS`에 `"token"` 포함)에 의한 것이며, 실제 API 호출에는 정상 전달됩니다.  
> 확실하게 max_tokens를 제어하려면 vLLM `--max-model-len`(입력 포함 전체 컨텍스트 제한)을 사용하거나 직접 vLLM 연결로 전환하세요.

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

> **중요:** `.env` 파일을 변경한 후에는 반드시 `docker compose up -d --force-recreate`를 사용하세요.  
> `docker restart`는 `env_file`을 다시 읽지 않으므로 환경 변수 변경이 적용되지 않습니다.

```bash
# .env 변경 후 반드시 이 명령 사용
cd /svc/app/lightrag
docker compose up -d --force-recreate
```

---

## 문서 처리 운영

```bash
# 문서 업로드
curl -X POST http://localhost:9621/documents/upload \
  -F "file=@/path/to/document.pdf"

# 처리 상태 확인
curl -s http://localhost:9621/documents | python3 -c "
import json, sys
docs = json.load(sys.stdin)
for d in docs.get('statuses', {}).values():
    print(d.get('status'), d.get('file_path','')[:60])
"

# LLM 캐시 초기화 방법 1: API (부분 초기화)
curl -X POST http://localhost:9621/documents/clear_cache \
  -H "Content-Type: application/json" \
  -d '{"doc_ids": []}'

# LLM 캐시 초기화 방법 2: 파일 직접 초기화 (컨테이너 내부)
# garbage 캐시 오염 시 완전 초기화 권장
echo '{}' > /svc/app/lightrag/data/rag_storage/kv_store_llm_response_cache.json

# 실패 문서 재처리
curl -X POST http://localhost:9621/documents/reprocess_failed \
  -H "Content-Type: application/json"
```

---

## 문제 해결

### 증상: 쿼리 응답이 garbage (한/영 혼합 무의미 문자열)

**원인 1 (AWQ 모델 컨텍스트 초과):** Qwen3-AWQ는 system prompt가 ~5,000 토큰을 초과하면 garbage 출력.  
**해결:**
```env
MAX_TOTAL_TOKENS=4000
MAX_ENTITY_TOKENS=1000
MAX_RELATION_TOKENS=1000
```
설정 후 `docker compose up -d --force-recreate`, 캐시 초기화, 쿼리 재시도.

**원인 2 (Thinking 미비활성화):** `rag_response` 프롬프트에 `/no_think`가 없어 Qwen3가 thinking 모드 진입 → 2048 토큰 소모 → `</think>` 닫히지 않음 → garbage.  
**해결:** `prompt.py`의 `rag_response`, `naive_rag_response`, `keywords_extraction` 시작에 `/no_think\n` 추가.

**원인 3 (캐시 오염):** garbage 응답이 `kv_store_llm_response_cache.json`에 캐시되어 재사용됨.  
**해결:** 반드시 캐시 초기화 후 재시도.
```bash
echo '{}' > /svc/app/lightrag/data/rag_storage/kv_store_llm_response_cache.json
```

---

### 증상: 쿼리 응답이 "No relevant context found"

**원인:** keyword extraction LLM 호출 실패 → 키워드 없음 → 컨텍스트 없음.  
주로 global/hybrid 모드에서 발생. LiteLLM 프록시 다운, LLM 엔드포인트 오류 등.  
**해결:** LLM 엔드포인트 상태 확인:
```bash
curl http://192.168.0.210:8010/v1/models   # 직접 vLLM
curl http://192.168.0.210:4000/v1/models   # LiteLLM
```

---

### 증상: Worker execution timeout after 600s

**원인:** `max_execution_timeout = llm_timeout * 2`(기본값)가 너무 짧음.  
**해결:** `lightrag/utils.py`에서 배수를 8로 변경 후 재빌드.

---

### 증상: 0 entities extracted, LLM output format error

**원인 1:** Thinking이 타임아웃으로 잘려 `</think>` 없는 가비지가 캐시에 저장됨.  
**해결:** 캐시 초기화 → `remove_think_tags()` 개선 코드 적용 → 재빌드 → 재처리.

**원인 2:** `ENTITY_EXTRACTION_USE_JSON=true` 시 Qwen3가 빈 JSON `{}` 반환.  
**해결:** `.env`에서 `ENTITY_EXTRACTION_USE_JSON=false`로 변경.

---

### 증상: Rate limit error (HTTP 429) / RetryError (LiteLLM 경유 시)

**원인:** LiteLLM 60초 cooldown 동안 모든 재시도 실패.  
**해결:** `lightrag/llm/openai.py`의 `wait_exponential(min=30, max=70)`으로 변경 후 재빌드.

---

### 증상: 응답 시간이 300초 이상 (Thinking 모델)

**원인:** Thinking 모델이 제한 없이 thinking 토큰을 생성.  
**해결 A (직접 vLLM):** `.env`에 `OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}}` 추가.  
**해결 B (LiteLLM, drop_params=false 환경):** `.env`에 `OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"thinking_budget": 8000}}` 추가.

---

### 증상: .env 변경 후 설정이 적용 안 됨

**원인:** `docker restart`는 `env_file`을 다시 읽지 않음.  
**해결:**
```bash
cd /svc/app/lightrag
docker compose up -d --force-recreate
```

---

### 증상: Local/Hybrid 모드 References가 "Document Title One/Two/Three" 표시

**원인:** `MAX_TOTAL_TOKENS=4000` 환경에서 local/hybrid 모드는 KG 데이터(엔티티/관계, ~2,200 토큰) + 시스템 프롬프트(~900 토큰) 처리 후 청크(chunk)에 할당되는 예산이 ~685 토큰으로 부족 → 청크 0개 선택 → `reference_list` 빈값 → LLM이 프롬프트 예시 텍스트("Document Title One/Two/Three")를 그대로 출력.

**해결 (코드 수정, `lightrag/operate.py`):**

청크가 없을 때 KG 엔티티/관계의 `file_path`를 폴백 references로 사용하도록 수정합니다.

1. `_apply_token_truncation()` 함수 내 — 토큰 절삭 전에 `kg_file_paths` 수집:
```python
kg_file_paths = []
seen_kg_paths: set = set()
for _item in (*entities_context, *relations_context):
    fp = _item.get("file_path", "")
    if fp and fp != "unknown_source":
        for _path in fp.split(GRAPH_FIELD_SEP):
            _path = _path.strip()
            if _path and _path not in seen_kg_paths:
                kg_file_paths.append(_path)
                seen_kg_paths.add(_path)
# 반환 딕셔너리에 추가
return { ..., "kg_file_paths": kg_file_paths }
```

2. `_build_context_str()` 함수 내 — `reference_list`가 비어있을 때 폴백:
```python
if not reference_list and kg_file_paths:
    reference_list = [
        {"reference_id": str(i + 1), "file_path": fp}
        for i, fp in enumerate(kg_file_paths)
    ]
    reference_list_str = "\n".join(
        f"[{ref['reference_id']}] {ref['file_path']}"
        for ref in reference_list
    )
```

이 수정은 `commit cf0717f`에서 적용되었으며 `knowwheresoft/uiscloud-lightrag:1.5.0-cf0717f` 이미지부터 포함됩니다.

---

### 증상: Naive 모드 응답 끝에 반복 garbage ("and and and..." 또는 "Reference Document List" 반복)

**원인:** naive 모드의 입력 토큰(시스템 프롬프트 ~569 + 청크 컨텍스트 ~2,370 = ~2,939 토큰)에 생성 토큰 상한(기본 2,048)을 합산하면 총 ~4,987 토큰으로 AWQ 모델의 반복 루프 임계값을 초과합니다. 생성 중 특정 토큰 수를 넘으면 컨텍스트에 있는 구문("Reference Document List" 등)을 반복합니다.

| `OPENAI_LLM_MAX_TOKENS` | 증상 |
|------------------------|------|
| 기본(2048) | "and and and..." 가비지 |
| 1024 | "Standard Operating Procedures for the Standard Operating Procedures..." 문장 반복 |
| 700 | ✅ 정상 (완성된 답변 + References) |
| 600 | 안전하나 응답이 중간에 잘리는 경우 있음 |

**해결:**
```env
OPENAI_LLM_MAX_TOKENS=700
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}, "repetition_penalty": 1.15}
```
입력(~2,939) + 출력(700) = ~3,639 토큰으로 안전 범위 유지. 700 토큰은 Qwen3의 한국어 기준 약 1,400~2,100자 응답에 해당합니다. `repetition_penalty=1.15`를 함께 적용하면 토큰 레벨 반복을 추가로 억제합니다.

---

### 증상: References 파일명이 번역/변형됨 (`표준운 operating 절procedure서(배도).pdf`)

**원인:** AWQ 양자화 모델(Qwen3-AWQ, EXAONE-AWQ)이 References 섹션을 생성할 때 한국어 파일명을 글자 단위로 분해하여 영어로 번역하거나 오타를 만들어냅니다. 프롬프트 지시(`"Do NOT translate the filename"`)로는 완전히 방지할 수 없는 AWQ 모델의 한계입니다.

예: `응용프로그램 표준운영절차서(배포).pdf` → `응용프로그램 표준운 operating 절procedure서(배도).pdf`

**해결 (코드 수정, `lightrag/operate.py`):**

LLM이 생성한 References 섹션을 버리고 인덱스에서 읽은 실제 파일명으로 교체하는 후처리 함수를 추가합니다. AWQ 모델의 max_tokens 도달 시 발생하는 garbage tail 제거도 함께 처리합니다.

**1단계:** `kg_query` 함수 위(line ~3648)에 함수 추가:

```python
# AWQ 모델 garbage 감지용 패턴 (모듈 레벨에 정의, commit 3816676 기준 13개)
_GARBAGE_PATTERNS = [
    re.compile(r"[A-Za-z]{3,}[가-힣]"),   # 영어가 공백 없이 한국어에 직접 연결
    re.compile(r"[가-힣][A-Za-z]{3,}"),   # 한국어가 공백 없이 영어에 직접 연결
    re.compile(r"[!?~]{3,}"),             # 3회 이상 반복된 구두점
    re.compile(r"\|{2,}"),                # 2회 이상 연속된 파이프
    re.compile(r"[A-Z_]{5,}"),            # 5자 이상의 ALL_CAPS_IDENTIFIER
    re.compile(r"[_]{3,}"),               # 3회 이상 연속된 언더스코어
    re.compile(r"[,;]{3,}"),              # 3회 이상 연속된 쉼표/세미콜론
    re.compile(r"""["']{3,}"""),          # 3회 이상 연속된 따옴표
    re.compile(r"\\{2,}"),               # 2회 이상 연속된 역슬래시
    re.compile(r"[—–]{2,}"),             # 2회 이상 연속된 em/en 대시
    re.compile(r"[><=!]{2,}"),           # 2회 이상 비교/화살표 연산자 (=>, >>, <=)
    re.compile(r"[(){}\[\]]{2,}"),       # 2회 이상 연속 코드 괄호 (){ , {}, [[)
    re.compile(r"\.{2,}"),              # 2회 이상 연속 점 (.., ...)
]


def _trim_garbage_tail(body: str) -> str:
    """AWQ 모델이 max_tokens 도달 시 생성하는 garbage tail을 제거합니다.

    여섯 가지 garbage 패턴을 감지합니다:
    - Qwen3-style: 수학기호/그리스/키릴 문자 등 exotic 문자 비율이 높은 줄
    - EXAONE-style: 한국어 + 무작위 ASCII/코드 문자열 혼합 (워드 샐러드)
    - Japanese punct: 일본어 구두점(、。「」 등)이 포함된 한국어 없는 줄
    - ASCII soup: 한국어·exotic 없고 모음 비율 12% 미만인 자음 수프
    - English sentence: 영어 문장 3단어+ 뒤에 한국어가 섞인 줄 (LLM 언어 전환)
    - 영어 word-salad: 500자 이상인데 한국어 비율이 5% 미만인 줄 (naive mode)
    """
    lines = body.split("\n")
    result = []
    seen_korean_chars = 0  # 누적 한국어 글자 수 (영어 문장 체크 조기 실행 방지)

    for line in lines:
        stripped = line.strip()
        if not stripped:
            result.append(line)
            continue

        total = len(stripped)
        korean = sum(
            1 for c in stripped
            if "가" <= c <= "힣" or "㄰" <= c <= "㆏"
        )
        exotic = sum(
            1 for c in stripped
            if not c.isascii()
            and not ("가" <= c <= "힣")
            and not ("㄰" <= c <= "㆏")
            and not ("一" <= c <= "鿿")
            and not c.isdigit()
        )

        # Qwen3-style: exotic 문자 비율 높고 한국어 거의 없음
        if total > 15 and exotic / total > 0.25 and korean / total < 0.1:
            break

        # EXAONE-style: 8자 이상이고 한국어 포함 + 코드 정크 패턴 2개 이상 매칭
        if total > 8 and korean > 0:
            pattern_hits = sum(1 for p in _GARBAGE_PATTERNS if p.search(stripped))
            if pattern_hits >= 2:
                break

        # 일본어 구두점: 한국어 없는 줄에 일본어 구두점 포함 (、Discussion」 등)
        if total > 5 and korean == 0 and re.search(r"[、。「」『』【】〔〕]", stripped):
            break

        # ASCII soup: 한국어·exotic 없고 모음 비율 12% 미만 (s btlz tutp tm wif 등)
        if total > 15 and korean == 0 and exotic == 0:
            non_space = [c for c in stripped if not c.isspace()]
            if non_space and sum(1 for c in non_space if c.lower() in "aeiou") / len(non_space) < 0.12:
                break

        # 영어 문장 시작 + 한국어 혼합 (한국어 50자 누적 후에만 적용)
        # "My work is quite broad indeed - 여기가..." 유형 감지
        # WebSocket API를 등 혼합 대소문자 기술용어는 안전
        if korean > 0 and seen_korean_chars > 50 and re.search(r"^[A-Z][a-z]+(?:\s+[a-z]+){2,}\s", stripped):
            break

        # 영어 word-salad: 500자 이상인데 한국어가 5% 미만 (naive mode)
        if total > 500 and korean > 0 and korean < total // 20:
            break

        seen_korean_chars += korean
        result.append(line)

    return "\n".join(result).rstrip()


def _replace_references_section(response: str, reference_list: list) -> str:
    """LLM이 생성한 References 섹션을 실제 파일명으로 교체합니다.

    AWQ 모델의 한국어 파일명 번역/변형을 방지하고,
    max_tokens 도달 시 발생하는 garbage tail도 함께 제거합니다.
    """
    ref_heading_pattern = re.compile(
        r"(?:\n+|\A)#{1,4}\s*(?:References?|참고\s*문헌?|참고\s*문서?)\s*(?:\n|$)",
        re.IGNORECASE,
    )
    last_start = None
    for m in ref_heading_pattern.finditer(response):
        last_start = m.start()

    body = response[:last_start].rstrip() if last_start is not None else response.rstrip()

    # AWQ garbage tail 제거
    body = _trim_garbage_tail(body)

    if not reference_list:
        return body

    ref_lines = ["\n\n### References\n"]
    for ref in reference_list:
        ref_id = ref.get("reference_id", "")
        file_path = ref.get("file_path", "")
        if file_path:
            ref_lines.append(f"- [{ref_id}] {file_path}")

    return body + "\n".join(ref_lines)
```

**2단계:** `kg_query` 응답 반환 직전에 후처리 적용:

```python
# kg_query 함수 내 (line ~3879)
if isinstance(response, str):
    # ... 기존 sys_prompt 제거 로직 ...
    ref_list = context_result.raw_data.get("data", {}).get("references", [])
    response = _replace_references_section(response, ref_list)
    return QueryResult(content=response, raw_data=context_result.raw_data)
```

**3단계:** `naive_query` 응답 반환 직전에도 동일하게 적용:

```python
# naive_query 함수 내 (line ~5851)
if isinstance(response, str):
    # ... 기존 sys_prompt 제거 로직 ...
    response = _replace_references_section(response, reference_list)
    return QueryResult(content=response, raw_data=raw_data)
```

**동작 원리:**
1. LLM 응답에서 `### References`, `## 참고 문헌` 등의 마지막 References 헤딩 이후를 제거
2. `_trim_garbage_tail()`로 본문 끝의 garbage 제거 (세 가지 모델 패턴 처리)
3. 인덱스의 `reference_list`(파일 업로드 시 저장된 원본 파일명)로 References 섹션 재구성

**AWQ 모델별 garbage 특성 및 감지 패턴:**

| 모델 | garbage 특성 | 감지 방법 | 예시 |
|------|------------|---------|------|
| Qwen3-AWQ | 수학기호·그리스·키릴 문자 순수 나열 | exotic 비율 > 25% + Korean < 10% | `αβγδ∑∏∈∉∀∃` |
| EXAONE-AWQ | 한국어 + ASCII 혼합 워드 샐러드 | `_GARBAGE_PATTERNS` 13개 중 2개 이상 매칭 | `=>){ },>>같은`, `확인표준화처리...}}` |
| EXAONE-AWQ | 일본어 구두점 혼입 | 한국어 없는 줄 + 일본어 구두점 | `、Discussion」` |
| EXAONE-AWQ | ASCII 자음 수프 | 모음 비율 < 12% | `s btlz tutp tm wif fd tr sgdn gf` |
| EXAONE-AWQ | 영어 문장 + 한국어 혼합 | `^[A-Z][a-z]+(\s[a-z]+){2+}` 패턴 | `My work is quite broad indeed - 여기가 아직도...` |
| EXAONE-AWQ (naive) | 영어 word-salad (순수 ASCII) | 500자+ 줄, 한국어 < 5% | `epistemological debates axiological judgments...` |

**`_GARBAGE_PATTERNS` 13개 전체 목록 (commit 3816676, 4가지 모드 실제 테스트로 검증):**

| # | 패턴 | 설명 | 실제 감지 예시 |
|---|------|------|-------------|
| 1 | `[A-Za-z]{3,}[가-힣]` | 영어→한국어 공백 없이 붙음 | `EVALDENG니면` |
| 2 | `[가-힣][A-Za-z]{3,}` | 한국어→영어 공백 없이 붙음 | `목표설정DESCRIPT` |
| 3 | `[!?~]{3,}` | 3회+ 반복 구두점 | `!!!!` |
| 4 | `\|{2,}` | 2회+ 연속 파이프 | `\|\|` |
| 5 | `[A-Z_]{5,}` | 5자+ ALL_CAPS_IDENTIFIER | `EVALDENG` |
| 6 | `[_]{3,}` | 3회+ 연속 언더스코어 | `___` |
| 7 | `[,;]{3,}` | 3회+ 연속 쉼표/세미콜론 | `So,,,,,,` |
| 8 | `["']{3,}` | 3회+ 연속 따옴표 | `config"""=` |
| 9 | `\\{2,}` | 2회+ 연속 역슬래시 | `\\tower` |
| 10 | `[—–]{2,}` | 2회+ 연속 em/en 대시 | `————` |
| 11 | `[><=!]{2,}` | 2회+ 비교/화살표 연산자 | `=>`, `>>`, `<=` |
| 12 | `[(){}\[\]]{2,}` | 2회+ 연속 코드 괄호 | `){`, `}}`, `[[` |
| 13 | `\.{2,}` | 2회+ 연속 점 | `...}}` 조합 |

**EXAONE-style 감지 조건 변경 이력:**
- `total > 40` (commit 3f51427) → `total > 8` (commit e8d3388): 11자 단행 garbage(`=>){ },>>같은`) 감지를 위해 임계값 대폭 낮춤

이 수정은 `commit 35a5a66` + `a9970c1` + `3f51427` + `e8d3388` + `42e248b` + `b074e63` + `f245e61` + `fdb340f` + `3816676`에서 순차 적용되었으며 서버 override 파일(`/svc/app/lightrag/override/operate.py`)에 포함됩니다.

---

### 증상: EXAONE 응답에 영어 thinking 내용이 출력됨 ("Okay, let me try to figure out...")

**원인:** EXAONE-4.5는 기본적으로 thinking 모드가 활성화되어 있으며, Qwen3용 `/no_think` prefix가 효과 없습니다.

**해결:** `.env`의 `OPENAI_LLM_EXTRA_BODY`에 `chat_template_kwargs`로 비활성화:

```env
OPENAI_LLM_EXTRA_BODY={"chat_template_kwargs": {"enable_thinking": false}, "repetition_penalty": 1.1}
```

LiteLLM 경유 시에도 `chat_template_kwargs`는 비표준 파라미터(`extra_body` 내부)이므로 `drop_params: true`에 의해 제거되지 않고 vLLM으로 그대로 전달됩니다.

> **모델별 thinking 비활성화 방법:**
> | 방법 | Qwen3 | EXAONE-4.5 |
> |------|-------|-----------|
> | `/no_think` prefix | ✅ 효과 있음 | ❌ 효과 없음 |
> | `enable_thinking: false` (EXTRA_BODY) | ✅ | ✅ |
> | `thinking_budget: 0` (EXTRA_BODY) | 주의: -1 반복 버그 발생 가능 | 미검증 |

---

### 증상: EXAONE 응답 끝에 garbage (`EVALDENG#manage...Dnl니면기후체의나는...`)

**원인:** EXAONE-4.5-33B-AWQ 모델이 `max_tokens`에 도달하면 쿼리 모드에 따라 다양한 garbage 패턴이 발생합니다. Qwen3-AWQ의 순수 exotic 문자 garbage와 달리 한국어·ASCII 혼합형이 많아 단순 exotic 비율 감지가 실패합니다.

**EXAONE garbage 유형별 예시:**

| 쿼리 모드 | garbage 특성 | 예시 |
|----------|------------|------|
| naive | 한국어 본문 뒤에 순수 영어 word-salad | `epistemological debates axiological judgments teleos-emotional conflicts...` |
| local | 언더스코어·em 대시·역슬래시 혼합 | `\]—이론심층향상 모사:\Proposal목표설정————패러다임전환___언제` |
| global | 연속 쉼표 + 한영 직접 연결 | `So,,,,,,,, ..., 잘읽음////공공민원처리 >exp전송` |
| hybrid | 코드형 기호 혼합 | `pass되어서 {객체jTask_loop은 config"""=단지 intensify 수용역량` |

**해결 1 (코드 수정 — 영구):** `_trim_garbage_tail()` 함수에 EXAONE-style 패턴 감지 (총 13개 패턴 + 5가지 감지 조건, 현재 적용 중):

EXAONE-style 감지 조건: **8자 이상** + **한국어 포함** + 아래 13개 패턴 중 **2개 이상** 매칭:

| # | 패턴 | 설명 |
|---|------|------|
| 1-10 | (기본 패턴) | 한영직접부착, `!?~`, `\|`, ALL_CAPS, `___`, `;;;`, `"""`, `\\`, `——` |
| 11 | `[><=!]{2,}` | 비교/화살표 연산자 (`=>`, `>>`, `<=`) |
| 12 | `[(){}\[\]]{2,}` | 연속 코드 괄호 (`){`, `}}`, `[[`) |
| 13 | `\.{2,}` | 연속 점 (`..`, `...`) |

추가 감지 조건 (EXAONE-style 이외):

| 조건 | 대상 | 예시 |
|------|------|------|
| 일본어 구두점 | korean=0 + `[、。「」『』【】〔〕]` | `、Discussion」` |
| ASCII 자음 수프 | korean=0, exotic=0, 모음비율<12% | `s btlz tutp tm wif fd tr sgdn gf` |
| 영어 문장+한국어 혼합 | seen_korean>50 + `^[A-Z][a-z]+(\s[a-z]+){2+}` | `My work is quite broad indeed - 여기가...` |
| 영어 word-salad (naive) | 500자+, 한국어<5% | `epistemological debates...` |

> **seen_korean_chars 가드:** 영어 문장 체크는 본문에서 한국어가 50자 이상 누적된 후에만 적용됩니다. `Based on the context, 다음과 같습니다:` 같은 서두 문장을 잘못 제거하지 않도록 방지합니다.

**해결 2 (임시):** `OPENAI_LLM_MAX_TOKENS` 값을 낮춰서 garbage 발생 전에 생성을 종료:

```env
OPENAI_LLM_MAX_TOKENS=800   # 현재 설정 (800은 garbage 경계에 가까움)
# 또는
OPENAI_LLM_MAX_TOKENS=600   # 더 안전하지만 응답이 잘릴 수 있음
```

**캐시 오염 주의:** garbage 응답이 LLM 캐시(`kv_store_llm_response_cache.json`)에 저장된 경우 캐시 초기화 필요:

```bash
# Docker 컨테이너에서 캐시 초기화 후 재시작
CONTAINER=$(docker compose -f /svc/app/lightrag/docker-compose.yml ps -q lightrag | head -1)
docker exec $CONTAINER sh -c "echo '{}' > /app/data/rag_storage/kv_store_llm_response_cache.json"
docker restart $CONTAINER
```

> **중요:** 캐시 파일만 초기화하면 **in-memory 캐시는 유지**됩니다. 컨테이너 재시작까지 해야 in-memory 캐시도 초기화됩니다. 순서는 반드시 **파일 초기화 → 재시작** 순으로 하세요.

---

### 증상: References가 코드 블록(` ``` `)으로 감싸져 나오거나 형식이 일관되지 않음

**원인:** LLM이 References 섹션을 마크다운 코드 블록으로 렌더링하거나, `### References` 대신 `### 참고 문헌` 등 다양한 헤딩을 임의로 사용합니다.

**해결:** 위의 `_replace_references_section()` 후처리 함수가 함께 해결합니다. 함수가 LLM 생성 References 섹션 전체를 교체하므로 형식이 항상 `### References` + 마크다운 리스트로 통일됩니다.

추가로 `prompt.py`의 `rag_response`와 `naive_rag_response`에 아래 지시를 추가하면 스트리밍 응답에서도 일관된 형식을 유도할 수 있습니다:

```
4. References Section Format:
  - The References section MUST use the exact heading: `### References`
  - Use markdown list format only: `- [n] 파일명` — do NOT use a code block for the References section.
  - The filename MUST be copied EXACTLY as it appears in the Reference Document List.
  - Provide maximum of 5 most relevant citations.
  - Do not generate anything after the References section.
```

---

## Jenkins CI/CD 배포

### 배포 구성 개요

`Jenkinsfile`이 Build → Push → Deploy → Health Check 전 과정을 자동화합니다.

```
GitHub Push
  → Jenkins 빌드 트리거
  → Docker 이미지 빌드 (knowwheresoft/uiscloud-lightrag:버전-SHA)
  → Docker Hub Push
  → 서버 배포 (docker compose up -d --no-build)
  → override 파일 자동 적용 (docker cp + docker restart)
  → Health Check (최대 3분)
```

### Override 파일 자동 적용 메커니즘

Jenkins 배포 후 컨테이너는 이미지 기본 파일을 사용합니다. 커스텀 수정 파일(`operate.py`, `prompt.py`)은 `/svc/app/lightrag/override/` 디렉토리에 보관되며, 배포 시 자동으로 컨테이너에 복사됩니다.

```bash
# 서버에서 직접 override 파일 업데이트 방법
scp lightrag/operate.py hypermakina@192.168.0.151:/svc/app/lightrag/override/operate.py
scp lightrag/prompt.py  hypermakina@192.168.0.151:/svc/app/lightrag/override/prompt.py
# 이후 Jenkins 배포 시 자동 반영됨
```

**Override 대상 파일:**

| 파일 | 위치 (서버) | 용도 |
|------|-----------|------|
| `operate.py` | `/svc/app/lightrag/override/operate.py` | `_trim_garbage_tail()`, `_replace_references_section()` 등 출력 후처리 |
| `prompt.py`  | `/svc/app/lightrag/override/prompt.py`  | 한국어 일관성 지시, `/no_think` prefix, References 형식 지시 |

### `.env` 관리 정책

`.env`는 **서버 기존 파일 우선** 정책입니다:
- 서버에 `.env`가 존재하면 그대로 유지 (운영 중 수동 변경 보호)
- 서버에 `.env`가 없을 때만 Jenkins credentials(`env-file-lightrag`)에서 복사

운영 설정 변경이 필요한 경우 서버에서 직접 수정 후 적용:
```bash
# 서버에서 직접 .env 수정 후 적용
vi /svc/app/lightrag/.env
cd /svc/app/lightrag && docker compose up -d --force-recreate
```

### Jenkins credentials 구성

`scripts/jenkins-setup.groovy`를 Jenkins Script Console에서 실행해 credentials를 생성합니다:

| credential ID | 종류 | 용도 |
|--------------|------|------|
| `github-pat` | Username/Password | GitHub 소스 코드 접근 |
| `dockerhub-credentials` | Username/Password | Docker Hub push |
| `env-file-lightrag` | Secret File | LightRAG `.env` 초기 생성용 (서버에 없을 때만 사용) |

`env-file-lightrag` credential 재생성 방법:
```bash
# 서버 현재 .env를 base64로 인코딩하여 groovy 파일에 업데이트
ssh hypermakina@192.168.0.151 "cat /svc/app/lightrag/.env" | base64
# 출력 결과를 jenkins-setup.groovy의 envBase64 변수에 붙여넣기
```

### 배포 검증 (Jenkins #14, 2026-06-18)

```
✅ Build:        1.5.0-9c4aa66 이미지 빌드 완료
✅ Push:         knowwheresoft/uiscloud-lightrag:1.5.0-9c4aa66 push 완료
✅ Deploy:       docker compose up -d --no-build 정상 실행
✅ Override:     operate.py, prompt.py docker cp 적용 완료
✅ Restart:      컨테이너 재시작 완료
✅ Health Check: 첫 번째 시도(attempt 1/18)에서 통과
```

Health check 응답 확인:
```bash
curl -s http://192.168.0.151:9621/health | python3 -m json.tool | grep -E '"status"|"llm_model"|"embedding_model"'
# "status": "healthy",
# "llm_model": "EXAONE-4.5-33B",
# "embedding_model": "bge-m3",
```

### 롤백

배포 실패 시 Jenkins가 자동으로 이전 이미지로 롤백합니다. 수동 롤백이 필요한 경우:

```bash
# 서버에서 직접 이전 이미지로 롤백
PREV_TAG=$(docker images knowwheresoft/uiscloud-lightrag \
    --format '{{.CreatedAt}} {{.Tag}}' \
    | grep -v latest \
    | sort -r | sed -n '2p' | awk '{print $NF}')
echo "롤백 대상: $PREV_TAG"
cd /svc/app/lightrag
sed -i "s|image: .*lightrag.*|image: knowwheresoft/uiscloud-lightrag:$PREV_TAG|g" docker-compose.yml
docker compose down --timeout 30 && docker compose up -d --no-build
```

---

## 인덱싱 성능 최적화

### 병목 분석

문서 인덱싱 속도는 주로 **엔티티/관계 추출(extract)** 단계에서 결정됩니다. 기본 구성(`MAX_ASYNC=1`, EXAONE-4.5-33B)은 청크당 약 2.6분이 소요됩니다.

| 구성 | 모델 | 동시성 | 청크당 속도 | 24청크 예상 |
|------|------|--------|-----------|------------|
| 기본값 | EXAONE-4.5-33B (33B) | 1 | ~2.6분 | ~63분 |
| MAX_ASYNC=2 | EXAONE-4.5-33B (33B) | 2 | ~1.3분 | ~31분 |
| 역할별 분리 | Qwen3-1.7B (extract) | 4 | **~20초** | **~8분** |

### 역할별 LLM 분리 설정 (권장)

인덱싱(추출)에는 소형 모델, 쿼리 응답에는 대형 모델을 분리 적용하는 방식입니다. LightRAG의 `RoleSpecificLLMConfiguration` 기능을 활용합니다.

**역할 정의:**

| 역할 | 환경변수 prefix | 목적 |
|------|---------------|------|
| `extract` | `EXTRACT_` | 엔티티/관계 추출 (인덱싱 시) |
| `keyword` | `KEYWORD_` | 쿼리 키워드 추출 (쿼리 시) |
| `query` | `QUERY_` | 최종 답변 생성 (쿼리 시) |
| `vlm` | `VLM_` | 멀티모달 분석 |

**`.env` 설정 (2026-06-19 기준 운영 중):**

```env
# 기본 LLM: 쿼리 답변용 (역할 미지정 시 기본값)
LLM_BINDING=openai
LLM_BINDING_HOST=http://192.168.0.210:4000/v1
LLM_BINDING_API_KEY=sk-dgx-proxy
LLM_MODEL=EXAONE-4.5-33B
LLM_TIMEOUT=600
MAX_ASYNC=2

# 역할별 LLM: 추출/키워드는 Qwen3-1.7B (빠른 소형 모델)
EXTRACT_LLM_BINDING=openai
EXTRACT_LLM_MODEL=Qwen3-1.7B
EXTRACT_LLM_BINDING_HOST=http://192.168.0.210:4000/v1
EXTRACT_LLM_BINDING_API_KEY=sk-dgx-proxy
EXTRACT_MAX_ASYNC_LLM=4

KEYWORD_LLM_BINDING=openai
KEYWORD_LLM_MODEL=Qwen3-1.7B
KEYWORD_LLM_BINDING_HOST=http://192.168.0.210:4000/v1
KEYWORD_LLM_BINDING_API_KEY=sk-dgx-proxy
KEYWORD_MAX_ASYNC_LLM=4
```

적용 후 health 엔드포인트에서 역할별 모델 확인:

```bash
curl -s http://192.168.0.151:9621/health | python3 -c "
import sys, json
d = json.load(sys.stdin)
for role, cfg in d['configuration']['role_llm_config'].items():
    print(f'{role:10} → {cfg[\"model\"]}  (max_async:{cfg[\"max_async\"]})')
"
# extract    → Qwen3-1.7B  (max_async:4)
# keyword    → Qwen3-1.7B  (max_async:4)
# query      → EXAONE-4.5-33B  (max_async:2)
# vlm        → EXAONE-4.5-33B  (max_async:2)
```

### 실측 성능 비교 (2026-06-19)

**인덱싱 테스트:**

| 파일 | 크기 | 청크 | extract 모델 | MAX_ASYNC | 소요 시간 |
|------|------|------|------------|---------|---------|
| Menual.pdf (TIS 매뉴얼) | 48,665자 | 24 | EXAONE-4.5-33B | 1 | **63분** |
| E-GENE ITSM 솔루션소개서.pdf | 8MB | 10 | Qwen3-1.7B | 4 | **3분 20초** |

> 청크당 속도: 2.6분 → 20초 (**약 8배 향상**)

**쿼리 테스트 (`E-GENE ITSM 솔루션의 주요 기능은 무엇인가?`):**

| 모드 | 응답 시간 | 응답 길이 | References | 품질 |
|------|---------|--------|-----------|------|
| hybrid | 165.5초 | 2,682자 | ✅ 정확 | 드래그앤드롭 UI, 통합 인터페이스 등 정리 |
| local | 162.3초 | 2,128자 | ✅ 정확 | 기능 상세 설명 |

Qwen3-1.7B로 인덱싱한 문서도 EXAONE 쿼리 품질에 영향 없음이 확인됨.

### MAX_ASYNC 단독 조정 (간단한 대안)

역할 분리 없이 동시성만 높이는 방법. 효과는 제한적이나 설정이 단순함:

```bash
# 서버에서 직접 적용
ssh hypermakina@192.168.0.151 "
sed -i 's/^MAX_ASYNC=1$/MAX_ASYNC=2/' /svc/app/lightrag/.env
cd /svc/app/lightrag && docker compose up -d --force-recreate
"
```

> **주의:** AWQ 모델에서 `MAX_ASYNC=4` 이상은 garbage 출력 증가 위험. `MAX_ASYNC=2`까지만 권장.

### LiteLLM에서 사용 가능한 모델 확인

```bash
curl -s -H "Authorization: Bearer sk-dgx-proxy" http://192.168.0.210:4000/v1/models \
  | python3 -c "import sys,json;[print(m['id']) for m in json.load(sys.stdin)['data']]"
# bge-m3
# Qwen3-30B-A3B
# EXAONE-4.5-33B
# bge-reranker-v2-m3
# Qwen3-1.7B
# Qwen3-32B-Finetuned
```
