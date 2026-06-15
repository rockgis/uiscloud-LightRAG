# 고급 기능

## 멀티모달 문서 처리

LightRAG 서버는 텍스트, 이미지, 표, 수식을 위한 멀티모달 문서 파이프라인을 포함합니다. 문서 파싱은 엔드포인트로 설정된 외부 MinerU 또는 Docling 서비스를 통해 처리되므로, 서버에서 `raganything` 패키지를 로컬에 설치하거나 임포트할 필요가 없습니다.

**상태:** 멀티모달 후처리 훅은 현재 플레이스홀더입니다. 이미지, 표, 수식 프로세서는 계획 중이지만 아직 연결되지 않았습니다. 외부 MinerU/Docling 파서를 통한 수집 및 일반 텍스트 인덱싱은 현재 작동합니다.

**계획된 기능:**
- 엔드투엔드 멀티모달 파이프라인: 문서 수집부터 멀티모달 쿼리 응답까지 완전한 워크플로우
- 범용 문서 지원: PDF, Office 문서(DOC/DOCX/PPT/PPTX/XLS/XLSX), 이미지, 다양한 파일 형식
- 특화 콘텐츠 분석: 이미지, 표, 수학 수식 전용 프로세서
- 멀티모달 지식 그래프(Knowledge Graph): 자동 엔티티 추출 및 교차 모달 관계 발견
- 하이브리드 지능형 검색: 텍스트와 멀티모달 콘텐츠를 아우르는 고급 검색

### 빠른 시작

`.env`에서 파서 라우팅(Parser Routing) 및 외부 파서 서비스 엔드포인트를 설정하세요:

```bash
LIGHTRAG_PARSER=pdf:mineru,docx:docling,pptx:docling,xlsx:docling,*:legacy
MINERU_API_MODE=local
MINERU_LOCAL_ENDPOINT=http://localhost:8000
DOCLING_ENDPOINT=http://localhost:5001/v1/convert/file/async
```

그런 다음 LightRAG 서버를 통해 문서를 업로드하세요. `LIGHTRAG_PARSER` 규칙은 `pdf`와 같은 접미사를 매칭하며, 쉼표나 세미콜론으로 구분될 수 있고 왼쪽에서 오른쪽으로 평가됩니다. 규칙이 MinerU 또는 Docling을 활성화하는 경우, 해당 엔드포인트는 서버 시작 전에 설정되어야 합니다. `paper.[mineru].pdf`, `memo.[native].docx` 같은 파일별 힌트는 기본 규칙을 덮어씁니다. 파싱된 멀티모달 사이드카는 파이프라인이 작성하고 일반 인덱싱 흐름에서 사용됩니다. 자세한 라우팅 규칙 및 예시는 [파일 처리 설정](./FileProcessingConfiguration-zh.md)을 참고하세요.

---

## 토큰 사용량 추적

**개요 및 사용법**

LightRAG는 지원되는 LLM 프로바이더가 보고하는 토큰 소비량을 모니터링하는 `TokenTracker` 도구를 제공합니다. 이 기능은 API 비용 제어와 성능 최적화에 유용합니다.

`TokenTracker`는 자동으로 LLM 호출에 삽입되지 않습니다. 프로바이더 바인딩에 직접 전달하거나, `llm_model_kwargs`를 통해 바인딩하거나, 커스텀 LLM 함수에서 캡처해야 합니다.

**방법 1: 직접 LLM 호출 추적**

```python
from lightrag.llm.openai import openai_complete_if_cache
from lightrag.utils import TokenTracker

token_tracker = TokenTracker()

with token_tracker:
    result1 = await openai_complete_if_cache(
        "gpt-4o-mini",
        "your question 1",
        token_tracker=token_tracker,
    )
    result2 = await openai_complete_if_cache(
        "gpt-4o-mini",
        "your question 2",
        token_tracker=token_tracker,
    )
```

컨텍스트 매니저는 블록 진입 시 트래커를 초기화하고 블록 종료 시 사용량을 출력합니다. `token_tracker=token_tracker` 인수는 여전히 필요합니다.

**방법 2: LightRAG 호출 추적**

```python
from lightrag import LightRAG, QueryParam
from lightrag.llm.openai import gpt_4o_mini_complete
from lightrag.utils import TokenTracker

token_tracker = TokenTracker()

rag = LightRAG(
    working_dir="./rag_storage",
    llm_model_func=gpt_4o_mini_complete,
    llm_model_kwargs={"token_tracker": token_tracker},
    embedding_func=embedding_func,
)

await rag.initialize_storages()

token_tracker.reset()
await rag.ainsert(["document one", "document two"])
await rag.aquery("your question 1", param=QueryParam(mode="naive"))
await rag.aquery("your question 2", param=QueryParam(mode="mix"))

print("Token usage:", token_tracker.get_usage())
```

`llm_model_kwargs={"token_tracker": token_tracker}`는 추출, 키워드 생성, 쿼리, VLM 호출에 사용되는 기본 역할 LLM 래퍼에 전달됩니다. 역할별 LLM kwargs를 설정하는 경우, 해당 역할 kwargs에도 `token_tracker`를 포함시키거나 아래의 클로저 패턴을 사용하세요.

**강건한 커스텀 래퍼 패턴**

```python
from lightrag import LightRAG
from lightrag.llm.gemini import gemini_complete_if_cache
from lightrag.utils import TokenTracker


def make_llm_func(token_tracker: TokenTracker):
    async def _llm_model_func(
        prompt,
        system_prompt=None,
        history_messages=None,
        **kwargs,
    ):
        return await gemini_complete_if_cache(
            "gemini-2.5-flash-lite",
            prompt,
            system_prompt=system_prompt,
            history_messages=history_messages,
            token_tracker=token_tracker,
            **kwargs,
        )

    return _llm_model_func


token_tracker = TokenTracker()

rag = LightRAG(
    working_dir="./rag_storage",
    llm_model_func=make_llm_func(token_tracker),
    embedding_func=embedding_func,
)

await rag.initialize_storages()

token_tracker.reset()
await rag.ainsert(["document one", "document two"])

print("Token usage:", token_tracker.get_usage())
```

**사용 팁:**
- 자동 초기화 및 최종 출력을 원할 때는 직접 LLM 세션에 컨텍스트 매니저를 사용하세요
- 구간별 통계를 위해 각 인덱싱 또는 쿼리 단계 전에 `reset()`을 호출하세요
- LLM 캐시 히트는 새로운 프로바이더 호출을 생성하지 않으므로, 캐시된 응답의 토큰 사용량은 증가하지 않습니다
- 토큰 사용량을 정기적으로 확인하면 비정상적인 소비를 조기에 감지할 수 있습니다

---

## 데이터 내보내기 기능

LightRAG는 분석, 공유, 백업을 위해 다양한 형식으로 지식 그래프 데이터를 내보낼 수 있습니다.

**기본 사용법**

```python
# 기본 CSV 내보내기 (기본 형식)
rag.export_data("knowledge_graph.csv")

# 특정 형식 지정
rag.export_data("output.xlsx", file_format="excel")
```

**지원 파일 형식**

```python
rag.export_data("graph_data.csv", file_format="csv")
rag.export_data("graph_data.xlsx", file_format="excel")
rag.export_data("graph_data.md", file_format="md")
rag.export_data("graph_data.txt", file_format="txt")
```

**추가 옵션**

내보내기에 벡터 임베딩 포함 (선택 사항):

```python
rag.export_data("complete_data.csv", include_vector_data=True)
```

모든 내보내기에는 엔티티 정보(이름, ID, 메타데이터), 관계 데이터(엔티티 간 연결), 벡터 데이터베이스의 관계 정보가 포함됩니다.

---

## 캐시 관리

**캐시 삭제**

`aclear_cache()`는 `llm_response_cache`의 모든 캐시 항목을 삭제합니다. 모드 또는 캐시 타입별 선택적 삭제는 지원하지 않습니다.

```python
# 비동기
await rag.aclear_cache()

# 동기
rag.clear_cache()
```

쿼리 관련 캐시의 선택적 삭제는 `lightrag.tools.clean_llm_query_cache` 도구를 사용하고 [lightrag/tools/README_CLEAN_LLM_QUERY_CACHE.md](../lightrag/tools/README_CLEAN_LLM_QUERY_CACHE.md)의 가이드를 참고하세요. 이 도구는 `mix`, `hybrid`, `local`, `global` 모드의 쿼리 캐시와 키워드 캐시를 관리합니다. `default:extract:*` 및 `default:summary:*`와 같은 추출 캐시는 삭제하지 **않습니다**.

---

## Langfuse 관찰성(Observability) 통합

Langfuse는 모든 LLM 상호작용을 자동으로 추적하는 OpenAI 클라이언트의 드롭인 대체제로, 개발자가 RAG 시스템을 모니터링, 디버깅, 최적화할 수 있게 합니다.

### 설치

```bash
pip install lightrag-hku[observability]
# 또는 소스에서:
pip install -e ".[observability]"
```

### 설정

`.env` 파일에 추가:

```
## Langfuse 관찰성 (선택 사항)
LANGFUSE_SECRET_KEY=""
LANGFUSE_PUBLIC_KEY=""
LANGFUSE_HOST="https://cloud.langfuse.com"  # 또는 자체 호스팅 인스턴스
LANGFUSE_ENABLE_TRACE=true
```

### 기능

설치 및 설정 후 Langfuse는 모든 OpenAI LLM 호출을 자동으로 추적합니다. 대시보드 기능:
- **추적(Tracing)**: 완전한 LLM 호출 체인 조회
- **분석(Analytics)**: 토큰 사용량, 지연 시간, 비용 메트릭
- **디버깅**: 프롬프트 및 응답 검사
- **평가**: 모델 출력 비교
- **모니터링**: 실시간 알림

> **참고**: LightRAG는 현재 OpenAI 호환 API 호출만 Langfuse와 통합됩니다. Ollama, Azure, AWS Bedrock 등의 API는 아직 Langfuse 관찰성을 지원하지 않습니다.

---

## RAGAS 기반 평가

**RAGAS** (Retrieval Augmented Generation Assessment)는 LLM을 사용하여 RAG 시스템을 참조 없이 평가하는 프레임워크입니다. LightRAG는 RAGAS 기반 평가 스크립트를 제공합니다. 자세한 내용은 [RAGAS 기반 평가 프레임워크](../lightrag/evaluation/README_EVALUASTION_RAGAS.md)를 참고하세요.
