# LightRAG Core로 프로그래밍하기

> LightRAG를 프로젝트에 통합하려면 LightRAG Server가 제공하는 REST API를 사용하는 것을 권장합니다. LightRAG Core는 임베디드 애플리케이션 또는 연구 및 평가를 수행하는 연구자를 위한 것입니다.

## 간단한 프로그램

```python
import os
import asyncio
from lightrag import LightRAG, QueryParam
from lightrag.llm.openai import gpt_4o_mini_complete, gpt_4o_complete, openai_embed
from lightrag.utils import setup_logger

setup_logger("lightrag", level="INFO")

WORKING_DIR = "./rag_storage"
if not os.path.exists(WORKING_DIR):
    os.mkdir(WORKING_DIR)

async def initialize_rag():
    rag = LightRAG(
        working_dir=WORKING_DIR,
        embedding_func=openai_embed,
        llm_model_func=gpt_4o_mini_complete,
    )
    # 중요: 두 초기화 호출 모두 필수입니다!
    await rag.initialize_storages()  # 스토리지 백엔드 초기화
    return rag

async def main():
    try:
        # RAG 인스턴스 초기화
        rag = await initialize_rag()
        await rag.ainsert("Your text")

        # 하이브리드 검색 수행
        mode = "hybrid"
        print(
          await rag.aquery(
              "What are the top themes in this story?",
              param=QueryParam(mode=mode)
          )
        )

    except Exception as e:
        print(f"An error occurred: {e}")
    finally:
        if rag:
            await rag.finalize_storages()

if __name__ == "__main__":
    asyncio.run(main())
```

참고사항:
- 실행 전에 `OPENAI_API_KEY` 환경 변수를 내보내세요.
- 모든 데이터는 `WORKING_DIR`에 저장됩니다.

**중요:**

**LightRAG는 사용 전에 명시적인 초기화가 필요합니다.** LightRAG 인스턴스를 생성한 후 반드시 `await rag.initialize_storages()`를 호출해야 합니다. 그렇지 않으면 오류가 발생합니다.


## LightRAG 초기화 파라미터

**파라미터**

| **파라미터** | **유형** | **설명** | **기본값** |
| -------------- | ---------- | ----------------- | ------------- |
| **working_dir** | `str` | 캐시가 저장될 디렉터리 | `lightrag_cache+timestamp` |
| **workspace** | str | 서로 다른 LightRAG 인스턴스 간 데이터 격리를 위한 워크스페이스 이름 | |
| **kv_storage** | `str` | 문서 및 텍스트 청크를 위한 스토리지 유형. 지원 유형: `JsonKVStorage`, `PGKVStorage`, `RedisKVStorage`, `MongoKVStorage`, `OpenSearchKVStorage` | `JsonKVStorage` |
| **vector_storage** | `str` | 임베딩 벡터를 위한 스토리지 유형. 지원 유형: `NanoVectorDBStorage`, `PGVectorStorage`, `MilvusVectorDBStorage`, `ChromaVectorDBStorage`, `FaissVectorDBStorage`, `MongoVectorDBStorage`, `QdrantVectorDBStorage`, `OpenSearchVectorDBStorage` | `NanoVectorDBStorage` |
| **graph_storage** | `str` | 그래프 엣지와 노드를 위한 스토리지 유형. 지원 유형: `NetworkXStorage`, `Neo4JStorage`, `PGGraphStorage`, `AGEStorage`, `OpenSearchGraphStorage` | `NetworkXStorage` |
| **doc_status_storage** | `str` | 문서 처리 상태를 위한 스토리지 유형. 지원 유형: `JsonDocStatusStorage`, `PGDocStatusStorage`, `MongoDocStatusStorage`, `OpenSearchDocStatusStorage` | `JsonDocStatusStorage` |
| **chunk_token_size** | `int` | 문서 분할 시 청크당 최대 토큰 크기 | `1200` |
| **chunk_overlap_token_size** | `int` | 문서 분할 시 두 청크 간 오버랩 토큰 크기 | `100` |
| **tokenizer** | `Tokenizer` | 텍스트를 토큰(숫자)으로 변환하고 다시 변환하는 데 사용하는 함수. `TokenizerInterface` 프로토콜을 따르는 `.encode()` 및 `.decode()` 함수 사용. 지정하지 않으면 기본 Tiktoken 토크나이저를 사용합니다. | `TiktokenTokenizer` |
| **tiktoken_model_name** | `str` | 기본 Tiktoken 토크나이저를 사용하는 경우 특정 Tiktoken 모델 이름. 직접 토크나이저를 제공하면 이 설정은 무시됩니다. | `gpt-4o-mini` |
| **entity_extract_max_gleaning** | `int` | 개체 추출 프로세스에서의 루프 수, 기록 메시지를 추가하며 반복 | `1` |
| **node_embedding_algorithm** | `str` | 노드 임베딩 알고리즘 (현재 미사용) | `node2vec` |
| **node2vec_params** | `dict` | 노드 임베딩 파라미터 | `{"dimensions": 1536,"num_walks": 10,"walk_length": 40,"window_size": 2,"iterations": 3,"random_seed": 3,}` |
| **embedding_func** | `EmbeddingFunc` | 텍스트에서 임베딩 벡터를 생성하는 함수 | `openai_embed` |
| **embedding_batch_num** | `int` | 임베딩 프로세스의 최대 배치 크기 (배치당 여러 텍스트 전송) | `32` |
| **embedding_func_max_async** | `int` | 최대 동시 비동기 임베딩 프로세스 수 | `16` |
| **llm_model_func** | `callable` | LLM 생성을 위한 함수 | `gpt_4o_mini_complete` |
| **llm_model_name** | `str` | 생성을 위한 LLM 모델 이름 | `meta-llama/Llama-3.2-1B-Instruct` |
| **summary_context_size** | `int` | 개체 관계 병합을 위한 요약 생성 시 LLM에 전송하는 최대 토큰 수 | `10000` (환경 변수 SUMMARY_CONTEXT_SIZE로 설정) |
| **summary_max_tokens** | `int` | 개체/관계 설명의 최대 토큰 크기 | `500` (환경 변수 SUMMARY_MAX_TOKENS로 설정) |
| **llm_model_max_async** | `int` | 최대 동시 비동기 LLM 프로세스 수 | `4` (환경 변수 MAX_ASYNC로 기본값 변경) |
| **llm_model_kwargs** | `dict` | LLM 생성을 위한 추가 파라미터 | |
| **vector_db_storage_cls_kwargs** | `dict` | 노드 및 관계 검색 임계값 설정 등 벡터 데이터베이스 추가 파라미터 | cosine_better_than_threshold: 0.2 (환경 변수 COSINE_THRESHOLD로 기본값 변경) |
| **enable_llm_cache** | `bool` | `TRUE`이면 LLM 결과를 캐시에 저장; 반복된 프롬프트는 캐시된 응답 반환 | `TRUE` |
| **enable_llm_cache_for_entity_extract** | `bool` | `TRUE`이면 개체 추출에 LLM 결과를 캐시에 저장; 초보자가 애플리케이션을 디버깅하는 데 유용 | `TRUE` |
| **addon_params** | `dict` | 추출을 위한 런타임 매개변수. 지원 키: `language` (개체/관계 요약 출력 언어), `entity_type_prompt_file` (경로가 아닌 파일 이름 — `PROMPT_DIR/entity_type`에서 로드되는 YAML 프로필; `PROMPT_DIR` 기본값은 `./prompts`), `entity_types_guidance` (파일 프로필보다 우선하는 인라인 재정의). | `{"language": "English", "entity_type_prompt_file": ""}` |
| **embedding_cache_config** | `dict` | 질문-답변 캐싱 설정. 세 가지 파라미터 포함: `enabled`: 캐시 조회 기능 활성화/비활성화 불리언 값. `similarity_threshold`: 부동 소수점 값(0-1). 새 질문의 유사도가 이 임계값을 초과하면 캐시된 답변이 직접 반환됩니다. `use_llm_check`: LLM 유사도 검증 활성화/비활성화 불리언 값. | 기본값: `{"enabled": False, "similarity_threshold": 0.95, "use_llm_check": False}` |


## QueryParam

`QueryParam`을 사용하여 쿼리 동작을 제어합니다:

```python
class QueryParam:
    """LightRAG에서 쿼리 실행을 위한 설정 파라미터."""

    mode: Literal["local", "global", "hybrid", "naive", "mix", "bypass"] = "global"
    """검색 모드 지정:
    - "local": 문맥 의존적 정보에 집중.
    - "global": 전역 지식 활용.
    - "hybrid": 로컬과 전역 검색 방법 결합.
    - "naive": 고급 기술 없이 기본 검색 수행.
    - "mix": 지식 그래프와 벡터 검색 통합.
    """

    only_need_context: bool = False
    """True이면 응답 생성 없이 검색된 컨텍스트만 반환."""

    only_need_prompt: bool = False
    """True이면 응답 생성 없이 생성된 프롬프트만 반환."""

    response_type: str = "Multiple Paragraphs"
    """응답 형식 정의. 예: 'Multiple Paragraphs', 'Single Paragraph', 'Bullet Points'."""

    stream: bool = False
    """True이면 실시간 응답을 위한 스트리밍 출력 활성화."""

    top_k: int = int(os.getenv("TOP_K", "60"))
    """검색할 상위 항목 수. 'local' 모드에서는 개체, 'global' 모드에서는 관계를 나타냅니다."""

    chunk_top_k: int = int(os.getenv("CHUNK_TOP_K", "20"))
    """벡터 검색에서 초기에 검색하고 리랭킹 후 유지할 텍스트 청크 수.
    None이면 top_k 값을 기본값으로 사용.
    """

    max_entity_tokens: int = int(os.getenv("MAX_ENTITY_TOKENS", "6000"))
    """통합 토큰 제어 시스템에서 개체 컨텍스트에 할당된 최대 토큰 수."""

    max_relation_tokens: int = int(os.getenv("MAX_RELATION_TOKENS", "8000"))
    """통합 토큰 제어 시스템에서 관계 컨텍스트에 할당된 최대 토큰 수."""

    max_total_tokens: int = int(os.getenv("MAX_TOTAL_TOKENS", "30000"))
    """전체 쿼리 컨텍스트(개체 + 관계 + 청크 + 시스템 프롬프트)의 최대 총 토큰 예산."""

    # 기록 메시지는 컨텍스트를 위해 LLM에만 전송되며 검색에는 사용되지 않음
    conversation_history: list[dict[str, str]] = field(default_factory=list)
    """컨텍스트를 유지하기 위한 이전 대화 기록 저장.
    형식: [{"role": "user/assistant", "content": "message"}].
    """

    user_prompt: str | None = None
    """쿼리를 위한 사용자 제공 프롬프트.
    LLM을 위한 추가 지시사항. 제공되면 프롬프트 템플릿에 주입됩니다.
    LLM이 응답을 생성하는 방식을 사용자가 커스터마이징할 수 있도록 합니다.
    """

    enable_rerank: bool = True
    """검색된 텍스트 청크에 대한 리랭킹 활성화. True이지만 리랭크 모델이 설정되지 않으면 경고가 발행됩니다.
    리랭크 모델이 사용 가능할 때 리랭킹을 활성화하기 위해 기본값이 True입니다.
    """
```

> `top_k`의 기본값은 환경 변수 `TOP_K`로 변경할 수 있습니다.


## LLM 및 임베딩 주입

LightRAG는 문서 인덱싱과 쿼리를 위해 LLM과 임베딩 모델이 필요합니다. 초기화 시 관련 모델 함수를 LightRAG에 주입하세요.

### 모델 선택 요구사항

- **LLM**: 최소 32B 파라미터, 32KB 컨텍스트(64KB 권장). 인덱싱 중에는 추론 모델 사용을 피하고, 쿼리 시에는 더 강력한 모델 사용.
- **임베딩**: 인덱싱과 쿼리 전반에서 일관성 유지 필수. 권장: `BAAI/bge-m3`, `text-embedding-3-large`. 모델 변경 시 벡터 스토리지 초기화 필요.
- **리랭커**: 검색 품질을 크게 향상. 활성화 시 쿼리 모드를 `mix`로 설정. 권장: `BAAI/bge-reranker-v2-m3`, Jina 리랭커.

#### OpenAI 유사 API 사용

LightRAG는 OpenAI 유사 채팅/임베딩 API를 지원합니다:

```python
import os
import numpy as np
from lightrag.utils import wrap_embedding_func_with_attrs
from lightrag.llm.openai import openai_complete_if_cache, openai_embed

async def llm_model_func(
    prompt, system_prompt=None, history_messages=[], keyword_extraction=False, **kwargs
) -> str:
    return await openai_complete_if_cache(
        "solar-mini",
        prompt,
        system_prompt=system_prompt,
        history_messages=history_messages,
        api_key=os.getenv("UPSTAGE_API_KEY"),
        base_url="https://api.upstage.ai/v1/solar",
        **kwargs
    )

@wrap_embedding_func_with_attrs(embedding_dim=4096, max_token_size=8192, model_name="solar-embedding-1-large-query")
async def embedding_func(texts: list[str]) -> np.ndarray:
    return await openai_embed.func(
        texts,
        model="solar-embedding-1-large-query",
        api_key=os.getenv("UPSTAGE_API_KEY"),
        base_url="https://api.upstage.ai/v1/solar"
    )

async def initialize_rag():
    rag = LightRAG(
        working_dir=WORKING_DIR,
        llm_model_func=llm_model_func,
        embedding_func=embedding_func  # 데코레이트된 함수를 직접 전달
    )
    await rag.initialize_storages()
    return rag
```

> **임베딩 함수 래핑에 대한 중요 참고사항:**
>
> `EmbeddingFunc`는 중첩될 수 없습니다. `@wrap_embedding_func_with_attrs`로 데코레이트된 함수(예: `openai_embed`, `ollama_embed` 등)는 `EmbeddingFunc()`를 사용하여 다시 래핑할 수 없습니다. 이것이 커스텀 임베딩 함수를 생성할 때 `xxx_embed`를 직접 사용하는 대신 `xxx_embed.func`(기본 래핑되지 않은 함수)를 호출하는 이유입니다.

#### Hugging Face 모델 사용

`lightrag_hf_demo.py` 참고:

```python
from functools import partial
from transformers import AutoTokenizer, AutoModel

# 토크나이저와 모델 사전 로드
tokenizer = AutoTokenizer.from_pretrained("sentence-transformers/all-MiniLM-L6-v2")
embed_model = AutoModel.from_pretrained("sentence-transformers/all-MiniLM-L6-v2")

# Hugging Face 모델로 LightRAG 초기화
rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=hf_model_complete,  # 텍스트 생성에 Hugging Face 모델 사용
    llm_model_name='meta-llama/Llama-3.1-8B-Instruct',  # Hugging Face 모델 이름
    # Hugging Face 임베딩 함수 사용
    embedding_func=EmbeddingFunc(
        embedding_dim=384,
        max_token_size=2048,
        model_name="sentence-transformers/all-MiniLM-L6-v2",
        func=partial(
            hf_embed.func,  # 래핑되지 않은 함수에 접근하기 위해 .func 사용
            tokenizer=tokenizer,
            embed_model=embed_model
        )
    ),
)
```

#### Ollama 모델 사용

사용할 모델과 임베딩 모델(예: `nomic-embed-text`)을 pull하세요:

```python
import numpy as np
from lightrag.utils import wrap_embedding_func_with_attrs
from lightrag.llm.ollama import ollama_model_complete, ollama_embed

@wrap_embedding_func_with_attrs(embedding_dim=768, max_token_size=8192, model_name="nomic-embed-text")
async def embedding_func(texts: list[str]) -> np.ndarray:
    return await ollama_embed.func(texts, embed_model="nomic-embed-text")

# Ollama 모델로 LightRAG 초기화
rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=ollama_model_complete,
    llm_model_name='your_model_name',
    embedding_func=embedding_func,
)
```

#### 컨텍스트 크기 늘리기

LightRAG는 최소 32k 컨텍스트 토큰이 필요합니다. Ollama의 기본값은 8k입니다. 두 가지 방법:

*방법 1: Modelfile 편집*

```bash
ollama pull qwen2
ollama show --modelfile qwen2 > Modelfile
# Modelfile에 이 줄을 추가하세요:
# PARAMETER num_ctx 32768
ollama create -f Modelfile qwen2m
```

*방법 2: `llm_model_kwargs`를 통해 `num_ctx` 설정*

```python
rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=ollama_model_complete,
    llm_model_name='your_model_name',
    llm_model_kwargs={"options": {"num_ctx": 32768}},
    embedding_func=embedding_func,
)
```

> **임베딩 함수 래핑에 대한 중요 참고사항:**
>
> `EmbeddingFunc`는 중첩될 수 없습니다. 기본 래핑되지 않은 함수에 접근하려면 `xxx_embed.func`를 사용하세요.

**저용량 RAM GPU**

저용량 RAM GPU(예: 6GB)의 경우 소형 모델을 선택하고 컨텍스트 윈도우를 조정하세요. 예를 들어 `gemma2:2b`와 `num_ctx=26000`으로 `book.txt`에서 약 197개의 개체와 19개의 관계를 찾을 수 있습니다.

#### LlamaIndex

LightRAG는 LlamaIndex와의 통합을 지원합니다 (`llm/llama_index_impl.py`):

```python
import asyncio
from lightrag import LightRAG
from lightrag.llm.llama_index_impl import llama_index_complete_if_cache, llama_index_embed
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.llms.openai import OpenAI
from lightrag.utils import setup_logger

setup_logger("lightrag", level="INFO")

async def initialize_rag():
    rag = LightRAG(
        working_dir="your/path",
        llm_model_func=llama_index_complete_if_cache,
        embedding_func=EmbeddingFunc(
            embedding_dim=1536,
            max_token_size=2048,
            model_name=embed_model,
            func=partial(llama_index_embed.func, embed_model=embed_model)
        ),
    )
    await rag.initialize_storages()
    return rag
```

**추가 자료:**
- [LlamaIndex 문서](https://developers.llamaindex.ai/python/framework/)
- [직접 OpenAI 예시](examples/unofficial-sample/lightrag_llamaindex_direct_demo.py)
- [LiteLLM 프록시 예시](examples/unofficial-sample/lightrag_llamaindex_litellm_demo.py)

#### Azure OpenAI 모델 사용

```python
import os
import numpy as np
from lightrag.utils import wrap_embedding_func_with_attrs
from lightrag.llm.azure_openai import azure_openai_complete_if_cache, azure_openai_embed

async def llm_model_func(
    prompt, system_prompt=None, history_messages=[], keyword_extraction=False, **kwargs
) -> str:
    return await azure_openai_complete_if_cache(
        prompt,
        system_prompt=system_prompt,
        history_messages=history_messages,
        api_key=os.getenv("AZURE_OPENAI_API_KEY"),
        azure_endpoint=os.getenv("AZURE_OPENAI_ENDPOINT"),
        api_version=os.getenv("AZURE_OPENAI_API_VERSION"),
        deployment_name=os.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"),
        **kwargs
    )

@wrap_embedding_func_with_attrs(
    embedding_dim=1536,
    max_token_size=8192,
    model_name=os.getenv("AZURE_OPENAI_EMBEDDING_MODEL")
)
async def embedding_func(texts: list[str]) -> np.ndarray:
    return await azure_openai_embed.func(
        texts,
        api_key=os.getenv("AZURE_OPENAI_API_KEY"),
        azure_endpoint=os.getenv("AZURE_OPENAI_ENDPOINT"),
        api_version=os.getenv("AZURE_OPENAI_API_VERSION"),
        deployment_name=os.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME")
    )

rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=llm_model_func,
    embedding_func=embedding_func
)
```

#### Google Gemini 모델 사용

```python
import os
import numpy as np
from lightrag.utils import wrap_embedding_func_with_attrs
from lightrag.llm.gemini import gemini_model_complete, gemini_embed

async def llm_model_func(
    prompt, system_prompt=None, history_messages=[], keyword_extraction=False, **kwargs
) -> str:
    return await gemini_model_complete(
        prompt,
        system_prompt=system_prompt,
        history_messages=history_messages,
        api_key=os.getenv("GEMINI_API_KEY"),
        model_name="gemini-2.0-flash",
        **kwargs
    )

@wrap_embedding_func_with_attrs(
    embedding_dim=768,
    max_token_size=2048,
    model_name="models/text-embedding-004"
)
async def embedding_func(texts: list[str]) -> np.ndarray:
    return await gemini_embed.func(
        texts,
        api_key=os.getenv("GEMINI_API_KEY"),
        model="models/text-embedding-004"
    )

rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=llm_model_func,
    llm_model_name="gemini-2.0-flash",
    embedding_func=embedding_func
)
```

### 리랭크 함수 주입

검색 품질을 향상시키기 위해 더 효과적인 관련성 점수 모델을 기반으로 문서를 재정렬할 수 있습니다. `rerank.py` 파일은 세 가지 리랭커 공급자 드라이버 함수를 제공합니다:

- **Cohere / vLLM**: `cohere_rerank`
- **Jina AI**: `jina_rerank`
- **Aliyun**: `ali_rerank`

이 함수 중 하나를 LightRAG 객체의 `rerank_model_func` 속성에 주입하세요. 자세한 사용법은 `examples/rerank_example.py`를 참고하세요.

### 사용자 프롬프트 대 쿼리

LightRAG를 콘텐츠 쿼리에 사용할 때는 검색 프로세스와 관련 없는 출력 처리를 결합하지 마세요. `QueryParam`의 `user_prompt` 파라미터는 RAG 검색 단계에 참여하지 않으며 쿼리 완료 후 LLM이 검색된 결과를 처리하는 방법을 안내합니다.

```python
query_param = QueryParam(
    mode="hybrid",
    user_prompt="For diagrams, use mermaid format with English/Pinyin node names and Chinese display labels",
)

response_default = rag.query(
    "Please draw a character relationship diagram for Scrooge",
    param=query_param
)
print(response_default)
```


## 스토리지 백엔드

### 스토리지 유형

LightRAG는 서로 다른 목적을 위해 4가지 유형의 스토리지를 사용합니다:

| 스토리지 유형 | 목적 |
|---|---|
| **KV_STORAGE** | LLM 응답 캐시, 텍스트 청크, 문서 정보 |
| **VECTOR_STORAGE** | 개체/관계/청크 임베딩 벡터 |
| **GRAPH_STORAGE** | 개체-관계 그래프 구조 |
| **DOC_STATUS_STORAGE** | 문서 인덱싱 상태 |

### 지원 구현체

**KV_STORAGE**
```
JsonKVStorage        JsonFile (기본값)
PGKVStorage          Postgres
RedisKVStorage       Redis
MongoKVStorage       MongoDB
OpenSearchKVStorage  OpenSearch
```

**GRAPH_STORAGE**
```
NetworkXStorage          NetworkX (기본값)
Neo4JStorage             Neo4J
PGGraphStorage           PostgreSQL with AGE plugin
MemgraphStorage          Memgraph
OpenSearchGraphStorage   OpenSearch
```

> 테스트 결과 Neo4J는 프로덕션 환경에서 AGE 플러그인이 있는 PostgreSQL보다 우수한 성능을 제공합니다.

**VECTOR_STORAGE**
```
NanoVectorDBStorage         NanoVector (기본값)
PGVectorStorage             Postgres
MilvusVectorDBStorage       Milvus
FaissVectorDBStorage        Faiss
QdrantVectorDBStorage       Qdrant
MongoVectorDBStorage        MongoDB
OpenSearchVectorDBStorage   OpenSearch
```

**DOC_STATUS_STORAGE**
```
JsonDocStatusStorage        JsonFile (기본값)
PGDocStatusStorage          Postgres
MongoDocStatusStorage       MongoDB
OpenSearchDocStatusStorage  OpenSearch
```

각 스토리지 유형에 대한 연결 설정 예시는 저장소의 `env.example` 파일에 있습니다. 연결 문자열의 데이터베이스 인스턴스는 미리 생성되어야 합니다. LightRAG는 인스턴스 내의 테이블만 생성하며 인스턴스 자체는 생성하지 않습니다.

### 백엔드별 설정

#### Neo4J 스토리지 사용

프로덕션 환경에서는 KG 스토리지를 위한 엔터프라이즈 솔루션을 활용하고 싶을 것입니다. 원활한 로컬 테스트를 위해 Docker에서 Neo4J를 실행하는 것이 권장됩니다.

```bash
export NEO4J_URI="neo4j://localhost:7687"
export NEO4J_USERNAME="neo4j"
export NEO4J_PASSWORD="password"
export NEO4J_DATABASE="neo4j"  # 커뮤니티 에디션에 필요
```

```python
from lightrag.utils import setup_logger

setup_logger("lightrag", level="INFO")

async def initialize_rag():
    rag = LightRAG(
        working_dir=WORKING_DIR,
        llm_model_func=gpt_4o_mini_complete,
        graph_storage="Neo4JStorage",
    )
    await rag.initialize_storages()
    return rag
```

작동하는 예시는 `test_neo4j.py`를 참고하세요.

#### PostgreSQL 스토리지 사용

PostgreSQL은 KV 스토어, VectorDB(pgvector), GraphDB(apache AGE)를 위한 원스톱 솔루션을 제공할 수 있습니다. PostgreSQL 버전 16.6 이상이 지원됩니다.

- Docker를 선호한다면 이 이미지로 시작하세요 (기본 사용자 비밀번호: rag/rag): https://hub.docker.com/r/gzdaniel/postgres-for-rag
- 사용 방법: [examples/lightrag_gemini_postgres_demo.py](https://github.com/HKUDS/LightRAG/blob/main/examples/lightrag_gemini_postgres_demo.py) 참고
- 고성능 그래프 데이터베이스 요구사항에는 Neo4j가 권장됩니다. Apache AGE의 성능이 그만큼 경쟁력이 없습니다.

#### Faiss 스토리지 사용

Faiss를 사용하기 전에 `faiss-cpu` 또는 `faiss-gpu`를 수동으로 설치하세요:

```bash
pip install faiss-cpu
```

```python
async def embedding_func(texts: list[str]) -> np.ndarray:
    model = SentenceTransformer('all-MiniLM-L6-v2')
    embeddings = model.encode(texts, convert_to_numpy=True)
    return embeddings

rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=llm_model_func,
    embedding_func=EmbeddingFunc(
        embedding_dim=384,
        max_token_size=2048,
        model_name="all-MiniLM-L6-v2",
        func=embedding_func,
    ),
    vector_storage="FaissVectorDBStorage",
    vector_db_storage_cls_kwargs={
        "cosine_better_than_threshold": 0.3
    }
)
```

#### Memgraph 스토리지 사용

Memgraph는 Neo4j Bolt 프로토콜과 호환되는 고성능 인메모리 그래프 데이터베이스입니다.

```bash
export MEMGRAPH_URI="bolt://localhost:7687"
```

```python
async def initialize_rag():
    rag = LightRAG(
        working_dir=WORKING_DIR,
        llm_model_func=gpt_4o_mini_complete,
        graph_storage="MemgraphStorage",
    )
    await rag.initialize_storages()
    return rag
```

#### Milvus 벡터 스토리지 사용

Milvus는 프로덕션 수준 벡터 스토리지를 위한 고성능 확장 가능한 벡터 데이터베이스입니다. 인덱스 유형(HNSW, HNSW_SQ, IVF, DISKANN 등) 및 메트릭 유형을 포함한 전체 설정 옵션은 [docs/MilvusConfigurationGuide.md](./MilvusConfigurationGuide.md)를 참고하세요.

**환경 변수를 통한 빠른 설정:**

```bash
MILVUS_URI=http://localhost:19530
MILVUS_DB_NAME=lightrag
LIGHTRAG_VECTOR_STORAGE=MilvusVectorDBStorage
```

**Python SDK를 통한 빠른 설정:**

```python
rag = LightRAG(
    working_dir="./rag_storage",
    llm_model_func=...,
    embedding_func=...,
    vector_storage="MilvusVectorDBStorage",
    vector_db_storage_cls_kwargs={
        "milvus_uri": "http://localhost:19530",
        "milvus_db_name": "lightrag",
        "cosine_better_than_threshold": 0.2,
    },
)
```

#### MongoDB 스토리지 사용

MongoDB는 기본 KV 스토리지와 벡터 스토리지로 LightRAG를 위한 원스톱 스토리지 솔루션을 제공합니다. LightRAG는 MongoDB 컬렉션을 사용하여 간단한 그래프 스토리지를 구현합니다.

`MongoVectorDBStorage`는 Atlas Search/Vector Search를 지원하는 MongoDB 배포가 필요합니다 (예: MongoDB Atlas 또는 Atlas 로컬). 설정 마법사의 번들 로컬 Docker MongoDB 서비스는 MongoDB Community Edition으로 KV/그래프/문서 상태 스토리지에 사용할 수 있지만 `MongoVectorDBStorage`에는 사용할 수 **없습니다**.

#### Redis 스토리지 사용

LightRAG는 KV 스토리지로 Redis를 지원합니다. 영속성과 메모리 사용량을 신중하게 설정하세요. 권장 Redis 설정:

```
save 900 1
save 300 10
save 60 1000
stop-writes-on-bgsave-error yes
maxmemory 4gb
maxmemory-policy noeviction
maxclients 500
```

#### OpenSearch 스토리지 사용

OpenSearch는 LightRAG의 모든 4가지 스토리지 유형(KV, 벡터, 그래프, DocStatus)을 위한 통합 스토리지 솔루션을 제공합니다. 클라우드 전용 제한 없이 기본 k-NN 벡터 검색, 전문 검색, 수평 확장성을 제공합니다.

**요구사항**: k-NN 플러그인이 활성화된 OpenSearch 3.x 이상.

Docker로 설치 (플러그인 없음):
```bash
docker run -d -p 9200:9200 -e "discovery.type=single-node" \
  -e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=<custom-admin-password>" \
  opensearchproject/opensearch:latest
```

Docker Compose로 설치 (권장, 플러그인 포함):
```bash
curl -O https://raw.githubusercontent.com/opensearch-project/opensearch-build/main/docker/release/dockercomposefiles/docker-compose-3.x.yml
OPENSEARCH_INITIAL_ADMIN_PASSWORD=<custom-admin-password> docker-compose -f docker-compose-3.x.yml up -d
```

**설정** (전체 목록은 `env.example` 참고):
```bash
export OPENSEARCH_HOSTS=localhost:9200
export OPENSEARCH_USER=admin
export OPENSEARCH_PASSWORD=<custom-admin-password>
export OPENSEARCH_USE_SSL=true
export OPENSEARCH_VERIFY_CERTS=false
```

**사용법**:
```python
rag = LightRAG(
    working_dir=WORKING_DIR,
    llm_model_func=your_llm_func,
    embedding_func=your_embed_func,
    kv_storage="OpenSearchKVStorage",
    doc_status_storage="OpenSearchDocStatusStorage",
    graph_storage="OpenSearchGraphStorage",
    vector_storage="OpenSearchVectorDBStorage",
)
```

**그래프 탐색**: OpenSearch SQL 플러그인과 PPL 지원이 사용 가능하면 그래프 쿼리는 `graphlookup` 명령을 사용하여 최적 성능을 위한 서버 측 BFS를 사용합니다. 그렇지 않으면 클라이언트 측 배치 BFS로 폴백합니다. 시작 시 자동 감지되거나 `OPENSEARCH_USE_PPL_GRAPHLOOKUP=true|false`로 강제 설정합니다.


## LightRAG 인스턴스 간 데이터 격리

`workspace` 파라미터는 서로 다른 LightRAG 인스턴스 간의 데이터 격리를 보장합니다. 초기화되면 `workspace`는 변경 불가능합니다.

| 스토리지 유형 | 격리 방법 |
|---|---|
| `JsonKVStorage`, `JsonDocStatusStorage`, `NetworkXStorage`, `NanoVectorDBStorage`, `FaissVectorDBStorage` | 워크스페이스 하위 디렉터리 |
| `RedisKVStorage`, `MilvusVectorDBStorage`, `MongoKVStorage`, `MongoVectorDBStorage`, `MongoGraphStorage`, `PGGraphStorage` | 컬렉션 이름의 워크스페이스 접두사 |
| `QdrantVectorDBStorage` | 페이로드 기반 파티셔닝 (Qdrant 멀티테넌시) |
| `PGKVStorage`, `PGVectorStorage`, `PGDocStatusStorage` | 테이블의 `workspace` 필드 |
| `Neo4JStorage` | 레이블 |
| `OpenSearch*` | 인덱스 이름 접두사 |

**레거시 호환성**: PostgreSQL 비그래프 스토리지의 기본 워크스페이스는 `default`, PostgreSQL AGE 그래프 스토리지는 null, Neo4j 그래프 스토리지는 `base`.

스토리지별 워크스페이스 환경 변수가 공통 `WORKSPACE` 변수보다 우선합니다: `REDIS_WORKSPACE`, `MILVUS_WORKSPACE`, `QDRANT_WORKSPACE`, `MONGODB_WORKSPACE`, `POSTGRES_WORKSPACE`, `NEO4J_WORKSPACE`, `OPENSEARCH_WORKSPACE`.

여러 격리된 지식 베이스 관리의 실용적인 데모는 [워크스페이스 데모](examples/lightrag_gemini_workspace_demo.py)를 참고하세요.


## 삽입

* 기본 삽입

```python
rag.insert("Text")
```

* 일괄 삽입

```python
# 기본 일괄 삽입
rag.insert(["TEXT1", "TEXT2", ...])

# 커스텀 배치 크기로 일괄 삽입
rag = LightRAG(
    ...
    working_dir=WORKING_DIR,
    max_parallel_insert=4
)
rag.insert(["TEXT1", "TEXT2", "TEXT3", ...])  # 배치 크기 4로 처리
```

`max_parallel_insert` 파라미터는 동시에 처리되는 문서 수를 결정합니다. 기본값은 **2**입니다. 병목 현상이 일반적으로 LLM에 있으므로 **10 이하**로 유지하는 것이 권장됩니다.

* ID를 지정하여 삽입

문서와 ID의 수가 동일해야 합니다.

```python
# ID를 지정한 단일 텍스트
rag.insert("TEXT1", ids=["ID_FOR_TEXT1"])

# ID를 지정한 여러 텍스트
rag.insert(["TEXT1", "TEXT2", ...], ids=["ID_FOR_TEXT1", "ID_FOR_TEXT2"])
```

* 파이프라인을 사용한 삽입

`apipeline_enqueue_documents`와 `apipeline_process_enqueue_documents`는 메인 스레드가 계속 실행되는 동안 백그라운드에서 문서를 점진적으로 삽입할 수 있게 합니다.

```python
rag = LightRAG(..)
await rag.apipeline_enqueue_documents(input)
# 루프의 루틴
await rag.apipeline_process_enqueue_documents(input)
```

* 다중 파일 유형 지원 삽입

`textract` 라이브러리는 TXT, DOCX, PPTX, CSV, PDF 읽기를 지원합니다:

```python
import textract

file_path = 'TEXT.pdf'
text_content = textract.process(file_path)
rag.insert(text_content.decode('utf-8'))
```

* 인용 기능

파일 경로를 제공하면 시스템이 소스를 원본 문서로 추적할 수 있습니다:

```python
documents = ["Document content 1", "Document content 2"]
file_paths = ["path/to/doc1.txt", "path/to/doc2.txt"]

rag.insert(documents, file_paths=file_paths)
```


## 개체와 관계 편집

LightRAG는 포괄적인 지식 그래프 관리를 지원합니다: 개체와 관계를 생성, 편집, 삭제합니다.

* 개체와 관계 생성

```python
# 개체 생성
entity = rag.create_entity("Google", {
    "description": "Google is a multinational technology company specializing in internet-related services and products.",
    "entity_type": "company"
})

product = rag.create_entity("Gmail", {
    "description": "Gmail is an email service developed by Google.",
    "entity_type": "product"
})

# 관계 생성
relation = rag.create_relation("Google", "Gmail", {
    "description": "Google develops and operates Gmail.",
    "keywords": "develops operates service",
    "weight": 2.0
})
```

* 개체와 관계 편집

```python
# 개체 속성 편집
updated_entity = rag.edit_entity("Google", {
    "description": "Google is a subsidiary of Alphabet Inc., founded in 1998.",
    "entity_type": "tech_company"
})

# 개체 이름 변경 (모든 관계를 적절히 마이그레이션)
renamed_entity = rag.edit_entity("Gmail", {
    "entity_name": "Google Mail",
    "description": "Google Mail (formerly Gmail) is an email service."
})

# 관계 편집
updated_relation = rag.edit_relation("Google", "Google Mail", {
    "description": "Google created and maintains Google Mail service.",
    "keywords": "creates maintains email service",
    "weight": 3.0
})
```

모든 작업은 동기 및 비동기 버전으로 사용 가능합니다. 비동기 버전은 "a" 접두사를 가집니다 (예: `acreate_entity`, `aedit_relation`).

* 커스텀 KG 삽입

```python
custom_kg = {
    "chunks": [
        {
            "content": "Alice and Bob are collaborating on quantum computing research.",
            "source_id": "doc-1",
            "file_path": "test_file",
        }
    ],
    "entities": [
        {
            "entity_name": "Alice",
            "entity_type": "person",
            "description": "Alice is a researcher specializing in quantum physics.",
            "source_id": "doc-1",
            "file_path": "test_file"
        },
        {
            "entity_name": "Bob",
            "entity_type": "person",
            "description": "Bob is a mathematician.",
            "source_id": "doc-1",
            "file_path": "test_file"
        },
        {
            "entity_name": "Quantum Computing",
            "entity_type": "technology",
            "description": "Quantum computing utilizes quantum mechanical phenomena for computation.",
            "source_id": "doc-1",
            "file_path": "test_file"
        }
    ],
    "relationships": [
        {
            "src_id": "Alice",
            "tgt_id": "Bob",
            "description": "Alice and Bob are research partners.",
            "keywords": "collaboration research",
            "weight": 1.0,
            "source_id": "doc-1",
            "file_path": "test_file"
        },
        {
            "src_id": "Alice",
            "tgt_id": "Quantum Computing",
            "description": "Alice conducts research on quantum computing.",
            "keywords": "research expertise",
            "weight": 1.0,
            "source_id": "doc-1",
            "file_path": "test_file"
        },
        {
            "src_id": "Bob",
            "tgt_id": "Quantum Computing",
            "description": "Bob researches quantum computing.",
            "keywords": "research application",
            "weight": 1.0,
            "source_id": "doc-1",
            "file_path": "test_file"
        }
    ]
}

rag.insert_custom_kg(custom_kg)
```

* 기타 개체 및 관계 작업
  - **create_entity**: 지정된 속성으로 새 개체 생성
  - **edit_entity**: 기존 개체의 속성 업데이트 또는 이름 변경
  - **create_relation**: 기존 개체 간 새 관계 생성
  - **edit_relation**: 기존 관계의 속성 업데이트

이러한 작업은 그래프 데이터베이스와 벡터 데이터베이스 구성 요소 모두에서 데이터 일관성을 유지합니다.


## 삭제 함수

LightRAG는 포괄적인 삭제 기능을 제공합니다.

### 개체 삭제

```python
# 동기
rag.delete_by_entity("Google")

# 비동기
await rag.adelete_by_entity("Google")
```

개체를 삭제할 때:
- 지식 그래프에서 개체 노드 제거
- 모든 관련 관계 삭제
- 벡터 데이터베이스에서 관련 임베딩 벡터 제거
- 지식 그래프 무결성 유지

### 관계 삭제

```python
# 동기
rag.delete_by_relation("Google", "Gmail")

# 비동기
await rag.adelete_by_relation("Google", "Gmail")
```

관계를 삭제할 때:
- 지정된 관계 엣지 제거
- 관계의 임베딩 벡터 삭제
- 두 개체 노드와 다른 관계는 보존

### 문서 ID로 삭제

```python
# 비동기 전용 (복잡한 재구성 프로세스)
await rag.adelete_by_doc_id("doc-12345")
```

삭제 프로세스:
1. 문서와 관련된 모든 텍스트 청크 삭제
2. 이 문서에만 속하는 개체/관계 식별 및 삭제
3. 다른 문서에도 존재하는 개체/관계 재구성
4. 모든 관련 벡터 인덱스 업데이트
5. 문서 상태 레코드 정리

**중요 참고사항:**
1. 모든 삭제 작업은 **되돌릴 수 없습니다** — 주의해서 사용하세요
2. 대량의 데이터 삭제는 시간이 걸릴 수 있습니다. 특히 문서 ID로 삭제 시
3. 삭제 작업은 자동으로 그래프와 벡터 데이터베이스 간의 일관성을 유지합니다
4. 중요한 삭제 작업 전에 데이터 백업을 고려하세요


## 개체 병합

**개체와 관계 병합**

```python
# 기본 병합
rag.merge_entities(
    source_entities=["Artificial Intelligence", "AI", "Machine Intelligence"],
    target_entity="AI Technology"
)

# 커스텀 병합 전략 사용
rag.merge_entities(
    source_entities=["John Smith", "Dr. Smith", "J. Smith"],
    target_entity="John Smith",
    merge_strategy={
        "description": "concatenate",  # 모든 설명 결합
        "entity_type": "keep_first",   # 첫 번째 개체의 유형 유지
        "source_id": "join_unique"     # 모든 고유 소스 ID 결합
    }
)

# 커스텀 대상 개체 데이터 사용
rag.merge_entities(
    source_entities=["New York", "NYC", "Big Apple"],
    target_entity="New York City",
    target_entity_data={
        "entity_type": "LOCATION",
        "description": "New York City is the most populous city in the United States.",
    }
)

# 고급: 전략과 커스텀 데이터 결합
rag.merge_entities(
    source_entities=["Microsoft Corp", "Microsoft Corporation", "MSFT"],
    target_entity="Microsoft",
    merge_strategy={
        "description": "concatenate",
        "source_id": "join_unique"
    },
    target_entity_data={
        "entity_type": "ORGANIZATION",
    }
)
```

개체를 병합할 때:
- 소스 개체의 모든 관계가 대상 개체로 리디렉션됩니다
- 중복 관계가 지능적으로 병합됩니다
- 자기 관계(루프)가 방지됩니다
- 소스 개체가 병합 후 제거됩니다
- 관계 가중치와 속성이 보존됩니다


## 문제 해결

### 일반적인 초기화 오류

1. **`AttributeError: __aenter__`**
   - **원인**: 스토리지 백엔드가 초기화되지 않음
   - **해결**: LightRAG 인스턴스를 생성한 후 `await rag.initialize_storages()` 호출

2. **`KeyError: 'history_messages'`**
   - **원인**: 파이프라인 상태가 초기화되지 않음
   - **해결**: LightRAG 인스턴스를 생성한 후 `await rag.initialize_storages()` 호출

3. **두 오류가 연속 발생**
   - **해결**: 항상 이 패턴을 따르세요:
   ```python
   rag = LightRAG(...)
   await rag.initialize_storages()
   ```

### 모델 전환 문제

서로 다른 임베딩 모델 간에 전환할 때는 오류를 방지하기 위해 데이터 디렉터리를 초기화해야 합니다. LLM 캐시를 유지하려면 `kv_store_llm_response_cache.json` 파일만 보존할 수 있습니다.
