# LightRAG 아키텍처 문서

> **분석 기준:** 2026-05-26 / 브랜치: `main`  
> 이 문서는 실제 소스 코드를 직접 분석하여 작성된 공식 아키텍처 레퍼런스입니다.

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [전체 디렉터리 구조](#2-전체-디렉터리-구조)
3. [핵심 클래스 계층 구조](#3-핵심-클래스-계층-구조)
4. [문서 처리 파이프라인](#4-문서-처리-파이프라인)
5. [스토리지 백엔드 시스템](#5-스토리지-백엔드-시스템)
6. [LLM 제공자 & 역할 라우팅](#6-llm-제공자--역할-라우팅)
7. [파서 엔진 & 라우팅](#7-파서-엔진--라우팅)
8. [청킹 전략 시스템](#8-청킹-전략-시스템)
9. [쿼리 시스템](#9-쿼리-시스템)
10. [API 서버 구조](#10-api-서버-구조)
11. [WebUI 아키텍처](#11-webui-아키텍처)
12. [파이프라인 동시성 제어](#12-파이프라인-동시성-제어)
13. [멀티모달 분석 (Sidecar 시스템)](#13-멀티모달-분석-sidecar-시스템)
14. [핵심 환경 변수 레퍼런스](#14-핵심-환경-변수-레퍼런스)
15. [코드 규모 요약](#15-코드-규모-요약)

---

## 1. 프로젝트 개요

LightRAG는 **지식 그래프(Knowledge Graph) 기반 RAG(Retrieval-Augmented Generation) 프레임워크**입니다.  
단순 벡터 유사도 검색을 넘어 엔티티-관계 그래프를 구축하여, 문서 간 관계를 활용한 고품질 정보 검색을 제공합니다.

### 핵심 특징

| 특징 | 내용 |
|---|---|
| **5가지 쿼리 모드** | local, global, hybrid, naive, mix |
| **14개 스토리지 백엔드** | NetworkX, Neo4j, Memgraph, MongoDB, PostgreSQL, Redis, Milvus, Qdrant, FAISS, OpenSearch 등 |
| **20개 LLM 제공자** | OpenAI, Anthropic, Gemini, Bedrock, Ollama, Azure, Zhipu, Jina, VoyageAI 등 |
| **4가지 파서 엔진** | native(DOCX), mineru(PDF/이미지), docling(PDF/Office/웹), legacy |
| **4가지 청킹 전략** | F(고정), R(재귀 문자), V(시맨틱 벡터), P(문단 시맨틱) |
| **역할 기반 LLM 라우팅** | extract, keyword, query, vlm 역할별 독립 LLM 설정 |
| **멀티모달 지원** | VLM(Vision Language Model) 기반 이미지/표/수식 분석 |
| **멀티테넌시** | workspace 파라미터 기반 데이터 격리 |

### 기술 스택

| 계층 | 기술 |
|---|---|
| **백엔드 엔진** | Python 3.10+, asyncio, dataclass |
| **웹 서버** | FastAPI, Uvicorn, Gunicorn |
| **프론트엔드** | React 19, TypeScript, Vite, Tailwind CSS, Zustand |
| **패키지 관리** | uv (권장), pip |
| **컨테이너화** | Docker, Docker Compose, Kubernetes |

---

## 2. 전체 디렉터리 구조

```
uiscloud-LightRAG/
├── lightrag/                    # 핵심 Python 패키지
│   ├── lightrag.py              # LightRAG 메인 클래스 (4,041줄)
│   ├── pipeline.py              # 문서 파이프라인 (4,787줄)
│   ├── operate.py               # 엔티티/관계 추출 및 쿼리 (5,792줄)
│   ├── base.py                  # 스토리지 추상 인터페이스 (1,050줄)
│   ├── utils.py                 # 유틸리티: 토큰화, 임베딩, 캐시 (3,907줄)
│   ├── utils_graph.py           # 그래프 쿼리 유틸 (1,774줄)
│   ├── utils_pipeline.py        # 파이프라인 헬퍼 (704줄)
│   ├── constants.py             # 설정 기본값 (334줄)
│   ├── addon_params.py          # 런타임 확장 파라미터 (157줄)
│   ├── chunk_schema.py          # 청킹 스키마 정의 (256줄)
│   ├── llm_roles.py             # LLM 역할 관리 (572줄)
│   ├── storage_migrations.py    # 스토리지 마이그레이션 Mixin
│   ├── prompt.py                # LLM 프롬프트 템플릿
│   ├── prompt_multimodal.py     # 멀티모달 분석 프롬프트
│   │
│   ├── kg/                      # 스토리지 백엔드 구현 (14종)
│   │   ├── factory.py           # 스토리지 팩토리
│   │   ├── shared_storage.py    # 공유 스토리지/잠금 (1,742줄)
│   │   ├── postgres_impl.py     # PostgreSQL 전체 스택 (7,225줄)
│   │   ├── opensearch_impl.py   # OpenSearch 전체 스택 (3,784줄)
│   │   ├── mongo_impl.py        # MongoDB 전체 스택 (2,713줄)
│   │   ├── neo4j_impl.py        # Neo4j 그래프 DB (2,019줄)
│   │   ├── memgraph_impl.py     # Memgraph 그래프 DB (1,346줄)
│   │   ├── milvus_impl.py       # Milvus 벡터 DB (1,803줄)
│   │   ├── redis_impl.py        # Redis KV/DocStatus (1,238줄)
│   │   ├── qdrant_impl.py       # Qdrant 벡터 DB (1,051줄)
│   │   ├── faiss_impl.py        # FAISS 벡터 DB (823줄)
│   │   ├── networkx_impl.py     # NetworkX 인메모리 그래프 (789줄)
│   │   ├── nano_vector_db_impl.py # NanoVectorDB (615줄)
│   │   ├── json_kv_impl.py      # JSON 파일 KV (489줄)
│   │   └── json_doc_status_impl.py # JSON 파일 DocStatus (551줄)
│   │
│   ├── llm/                     # LLM 제공자 (20개 파일)
│   │   ├── openai.py            # OpenAI / OpenAI 호환 (1,095줄)
│   │   ├── anthropic.py         # Anthropic Claude
│   │   ├── gemini.py            # Google Gemini (멀티모달)
│   │   ├── bedrock.py           # AWS Bedrock
│   │   ├── ollama.py            # Ollama (로컬 LLM)
│   │   ├── azure_openai.py      # Azure OpenAI
│   │   ├── zhipu.py             # 智谱 AI
│   │   ├── jina.py              # Jina (임베딩/리랭킹)
│   │   ├── voyageai.py          # VoyageAI 임베딩
│   │   ├── hf.py                # HuggingFace
│   │   ├── llama_index_impl.py  # LlamaIndex 통합
│   │   ├── lmdeploy.py          # LM Deploy
│   │   ├── lollms.py            # LoLLMs
│   │   ├── nvidia_openai.py     # NVIDIA NIM
│   │   └── binding_options.py   # LLM 바인딩 옵션 (34K)
│   │
│   ├── parser/                  # 파서 엔진 (29개 파일)
│   │   ├── routing.py           # 파서 라우팅 + process_options (34K)
│   │   ├── cli.py               # 파서 디버그 CLI
│   │   ├── docx/                # Native DOCX 파서 (10개 파일)
│   │   └── external/
│   │       ├── mineru/          # MinerU 외부 파서 (5개 파일)
│   │       └── docling/         # Docling 외부 파서 (5개 파일)
│   │
│   ├── chunker/                 # 청킹 전략 (4개 파일)
│   │   ├── token_size.py        # F: 고정 토큰 청킹
│   │   ├── recursive_character.py # R: 재귀 문자 청킹
│   │   ├── semantic_vector.py   # V: 시맨틱 벡터 청킹
│   │   └── paragraph_semantic.py # P: 문단 시맨틱 청킹 (60K)
│   │
│   └── api/                     # FastAPI 서버
│       ├── lightrag_server.py   # 메인 서버 진입점 (111K)
│       └── routers/
│           ├── document_routes.py # 문서 관리 API (4,246줄)
│           ├── query_routes.py    # 쿼리 API (1,164줄)
│           ├── graph_routes.py    # 그래프 API (708줄)
│           └── ollama_api.py      # Ollama 호환 API (738줄)
│
├── lightrag_webui/              # React 프론트엔드
│   └── src/
│       ├── features/            # 주요 기능 페이지
│       ├── components/          # 공유 UI 컴포넌트 (그래프 시각화 포함)
│       ├── stores/              # Zustand 상태 관리
│       ├── api/                 # API 클라이언트
│       └── locales/             # 다국어 (13개 언어)
│
├── tests/                       # 테스트 스위트 (15개 서브디렉터리)
├── examples/                    # 사용 예제 (22개 파일)
├── docs/                        # 문서 (36개 파일)
├── k8s-deploy/                  # Kubernetes 배포 설정
├── pyproject.toml               # 패키지 메타데이터 & 의존성 (190줄)
├── env.example                  # 환경 변수 전체 레퍼런스 (46,981줄)
├── docker-compose.yml           # Docker Compose 기본 설정
└── Makefile                     # 개발/배포 명령
```

---

## 3. 핵심 클래스 계층 구조

### 3.1 LightRAG 메인 클래스 (`lightrag/lightrag.py:159`)

LightRAG는 **다중 상속 Mixin 패턴**으로 구현됩니다:

```python
@final
@dataclass
class LightRAG(_RoleLLMMixin, _StorageMigrationMixin, _PipelineMixin):
    """LightRAG: Simple and Fast Retrieval-Augmented Generation."""
```

**MRO (Method Resolution Order):**
```
LightRAG
    → _RoleLLMMixin          (llm_roles.py:93)   역할별 LLM 관리
        → _StorageMigrationMixin (storage_migrations.py:21)  마이그레이션
            → _PipelineMixin     (pipeline.py:194)    파이프라인
                → object
```

### 3.2 Mixin 상세

#### `_RoleLLMMixin` (`lightrag/llm_roles.py:93`)
역할별 LLM 인스턴스 관리 및 핫 리로드를 담당합니다.

```python
class _RoleLLMMixin:
    role_llm_configs: dict[str, RoleLLMConfig | dict] | None = None
    # 4가지 역할: extract, keyword, query, vlm

    def register_role_llm_builder(role: str, builder: Callable) -> None
    def set_role_llm_metadata(role: str, metadata: dict) -> None
    def _wrap_llm_role_func(role: str, func: Callable) -> Callable
    def _rebuild_role_llm_funcs() -> None
    # 속성: role_llm_funcs, role_llm_kwargs
```

#### `_StorageMigrationMixin` (`lightrag/storage_migrations.py:21`)
구버전 데이터를 신버전 스키마로 마이그레이션합니다.

```python
class _StorageMigrationMixin:
    async def check_and_migrate_data() -> None
    async def _migrate_entity_relation_data() -> None
    async def _migrate_chunk_tracking_storage() -> None
```

#### `_PipelineMixin` (`lightrag/pipeline.py:194`)
문서 파이프라인의 모든 단계(파싱 → 분석 → 청킹 → 추출)를 관리합니다.

```python
class _PipelineMixin:
    # 문서 enqueue/처리
    async def apipeline_enqueue_documents(...)  -> str   # track_id 반환
    async def apipeline_process_enqueue_documents(...)

    # 개별 문서 처리
    async def process_single_document(doc_id: str) -> None

    # 파싱 (엔진별 분리)
    async def parse_native(file_path: str, ...) -> dict
    async def parse_mineru(file_path: str, ...) -> dict
    async def parse_docling(file_path: str, ...) -> dict

    # VLM 멀티모달 분석
    async def analyze_multimodal(doc_id: str) -> None

    # 파이프라인 상태
    pipeline_status: dict          # 동시성 제어 상태 딕셔너리
    pipeline_status_lock: asyncio.Lock
```

### 3.3 LightRAG 주요 dataclass 필드

```python
@dataclass
class LightRAG(...):
    # ── 저장소 설정 ──────────────────────────────────────────────
    working_dir: str = "./rag_storage"
    workspace: str = ""
    kv_storage: str = "JsonKVStorage"
    vector_storage: str = "NanoVectorDBStorage"
    graph_storage: str = "NetworkXStorage"
    doc_status_storage: str = "JsonDocStatusStorage"

    # ── 청킹 설정 ────────────────────────────────────────────────
    chunk_token_size: int | None = None          # None → addon_params에서 읽음
    chunk_overlap_token_size: int | None = None
    tokenizer: Optional[Tokenizer] = None
    tiktoken_model_name: str = "gpt-4o-mini"

    # ── LLM 설정 ─────────────────────────────────────────────────
    llm_model_func: Callable | None = None
    llm_model_name: str = "gpt-4o-mini"
    llm_model_max_async: int = 4                 # MAX_ASYNC
    default_llm_timeout: int = 180
    role_llm_configs: dict | None = None         # 역할별 LLM 오버라이드

    # ── 임베딩 설정 ──────────────────────────────────────────────
    embedding_func: EmbeddingFunc | None = None
    embedding_batch_num: int = 10
    embedding_func_max_async: int = 8
    default_embedding_timeout: int = 30

    # ── 재랭킹 설정 ──────────────────────────────────────────────
    rerank_model_func: Callable | None = None
    rerank_model_max_async: int = 4
    min_rerank_score: float = 0.0

    # ── 쿼리 설정 ────────────────────────────────────────────────
    top_k: int = 40
    chunk_top_k: int = 20
    max_entity_tokens: int = 6000
    max_relation_tokens: int = 8000
    max_total_tokens: int = 30000
    cosine_threshold: float = 0.2
    kg_chunk_pick_method: str = "VECTOR"

    # ── 엔티티 추출 설정 ─────────────────────────────────────────
    entity_extract_max_gleaning: int = 1
    entity_extract_max_records: int = 100
    entity_extract_max_entities: int = 40
    entity_extraction_use_json: bool = False
    force_llm_summary_on_merge: int = 8

    # ── 파이프라인 병렬화 설정 ────────────────────────────────────
    max_parallel_parse_native: int = 5
    max_parallel_parse_mineru: int = 1
    max_parallel_parse_docling: int = 1
    max_parallel_analyze: int = 5
    max_parallel_insert: int = 2
    queue_size_default: int = 100
    queue_size_insert: int = 4

    # ── VLM 멀티모달 설정 ────────────────────────────────────────
    vlm_process_enable: bool = False

    # ── 캐시 설정 ────────────────────────────────────────────────
    enable_llm_cache: bool = True
    enable_llm_cache_for_entity_extract: bool = True
```

### 3.4 스토리지 추상 인터페이스 (`lightrag/base.py`)

```
StorageNameSpace (ABC)
    │  - async initialize()
    │  - async finalize()
    │  - async index_done_callback()
    │  - async drop()
    │
    ├── BaseVectorStorage (ABC)  :206
    │       - async query(query, top_k)
    │       - async upsert(data)
    │       - async delete(ids)
    │       - async filter_batch(ids)
    │
    ├── BaseKVStorage (ABC)  :365
    │       - async get_by_id(id)
    │       - async upsert(data)
    │       - async delete_by_id(id)
    │       - async get_by_ids(ids)
    │
    ├── BaseGraphStorage (ABC)  :428
    │       - async upsert_nodes(nodes)
    │       - async upsert_edges(edges)
    │       - async get_neighbors(node_id, depth)
    │       - async delete_nodes(node_ids)
    │       - async get_all_nodes()
    │
    └── DocStatusStorage (BaseKVStorage, ABC)  :850
            - async filter_by_status(status)
            - async get_docs_by_status(statuses)
            - async get_doc_by_file_basename(basename)
            - async get_doc_by_content_hash(hash)
```

---

## 4. 문서 처리 파이프라인

### 4.1 전체 흐름

```
파일 업로드 / 텍스트 삽입
        │
        ▼
┌─────────────────────────────┐
│  apipeline_enqueue_documents │  ← 중복 제거, 상태 초기화
│  (pipeline.py:208)          │
└──────────────┬──────────────┘
               │ DocStatus = PENDING
               ▼
┌──────────────────────────────────────────────────┐
│  apipeline_process_enqueue_documents              │
│  (pipeline.py:990)                               │
│                                                  │
│  ┌─────────┐    ┌──────────┐    ┌─────────────┐ │
│  │ q_native│    │ q_mineru │    │ q_docling   │ │
│  │  ×N1   │    │  ×N2    │    │  ×N3       │ │
│  └────┬────┘    └────┬─────┘    └──────┬──────┘ │
│       └──────────────┴────────────────┘         │
│                       │                          │
│               ┌───────▼────────┐                 │
│               │ Stage 1: Parse │ PARSING         │
│               │ (엔진별 분리)   │                 │
│               └───────┬────────┘                 │
│                       │                          │
│               ┌───────▼────────┐                 │
│               │ Stage 2:       │ ANALYZING       │
│               │ analyze_multi  │                 │
│               │ modal (VLM)    │ ×N4             │
│               └───────┬────────┘                 │
│                       │                          │
│               ┌───────▼────────┐                 │
│               │ Stage 3: Chunk │                 │
│               │ (F/R/V/P 전략)  │                │
│               └───────┬────────┘                 │
│                       │                          │
│               ┌───────▼────────┐                 │
│               │ Stage 4:       │ PROCESSING      │
│               │ Entity Extract │                 │
│               │ + Graph Build  │ ×N5             │
│               └───────┬────────┘                 │
│                       │ DocStatus = PROCESSED    │
└───────────────────────┼──────────────────────────┘
                        ▼
              저장소 (KV + Vector + Graph)
```

### 4.2 Stage 1: Parse (파싱)

**엔진 선택:** `LIGHTRAG_PARSER` 환경변수 또는 파일명 힌트 `[engine-options]`

| 엔진 | 지원 형식 | 출력 |
|---|---|---|
| `native` | DOCX | `.blocks.jsonl` + 사이드카 |
| `mineru` | PDF, DOCX, PPTX, 이미지 | `.blocks.jsonl` + 사이드카 |
| `docling` | PDF, DOCX, PPTX, MD, HTML, 이미지 | `.blocks.jsonl` + 사이드카 |
| `legacy` | 거의 모든 텍스트 형식 | `parse_format=raw` 텍스트 |

**파싱 결과 (LightRAG Document 형식):**
```
inputs/__parsed__/<파일명>.parsed/
├── <파일명>.blocks.jsonl     # 블록 시퀀스 (type=meta + type=content 행들)
├── <파일명>.drawings.json    # 이미지/드로잉 사이드카
├── <파일명>.tables.json      # 표 사이드카
├── <파일명>.equations.json   # 수식 사이드카
└── <파일명>.blocks.assets/   # 원본 이미지 파일
```

**`full_docs` 상태 필드:**

| 필드 | 값 | 의미 |
|---|---|---|
| `parse_format` | `pending_parse` | 아직 파싱 안 됨 |
| `parse_format` | `raw` | legacy 파서 결과 (일반 텍스트) |
| `parse_format` | `lightrag` | 구조화된 LightRAG Document |
| `content_hash` | MD5 | 내용 중복 방지 |
| `process_options` | `"iteP"` 등 | 처리 옵션 문자열 |
| `chunk_options` | dict | 청킹 파라미터 스냅샷 |
| `lightrag_document_path` | 경로 | `.blocks.jsonl` 위치 |

### 4.3 Stage 2: Analyze (멀티모달 분석)

`process_options`의 `i/t/e` 플래그와 `VLM_PROCESS_ENABLE=true` 조건이 모두 충족될 때만 실행됩니다.

```
analyze_multimodal(doc_id)  ← pipeline.py:3645
    │
    ├── i 플래그 → drawings.json 항목들 → VLM 이미지 분석
    │                → llm_analyze_result { name, type, description }
    │
    ├── t 플래그 → tables.json 항목들 → LLM 표 분석
    │                → llm_analyze_result { name, description }
    │
    └── e 플래그 → equations.json 항목들 → LLM 수식 분석
                     → llm_analyze_result { name, description, equation(LaTeX) }
```

**surrounding 컨텍스트:** VLM/LLM이 멀티모달 항목을 분석할 때, 항목 앞뒤 텍스트(`leading`/`trailing`)를 컨텍스트로 자동 주입합니다. (`SURROUNDING_LEADING_MAX_TOKENS`, `SURROUNDING_TRAILING_MAX_TOKENS`)

### 4.4 Stage 3: Chunk (청킹)

`process_options`의 청킹 선택자(`F/R/V/P`)에 따라 전략이 결정됩니다.

```
문서 텍스트
    │
    ├── F → chunking_by_fixed_token()     # 고정 토큰 분할
    ├── R → chunking_by_recursive_char()  # 재귀 문자 분할
    ├── V → chunking_by_semantic_vector() # 임베딩 기반 분할
    └── P → chunking_by_paragraph_semantic() # 문단/제목 기반 분할
                │
                └── (사이드카 없으면 R로 폴백)
    │
    ▼
청크 목록: [{"tokens": int, "content": str, "chunk_order_index": int, ...}]
```

**chunk_options 슬림 스냅샷** (enqueue 시 전략별로 필요한 파라미터만 저장):
```python
# R 전략 선택 예시
{
    "chunk_token_size": 1200,
    "recursive_character": {
        "chunk_overlap_token_size": 100,
        "separators": ["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""]
    }
}
```

### 4.5 Stage 4: Process (엔티티 추출 & 그래프 구축)

```
청크 목록
    │
    ▼
extract_entities()  ← operate.py:3221
    │
    ├── LLM 프롬프트 생성 (entity + relation 공동 추출)
    │       ↓ [Extract Role LLM 사용]
    ├── JSON 구조화 출력 (entity_extraction_use_json=True)
    │   또는 구분자 기반 파싱
    │
    ├── 엔티티: { name, type, description, source_id }
    └── 관계: { src_id, tgt_id, keywords, description, weight }
    │
    ▼
merge_nodes_and_edges()
    │
    ├── 기존 노드/엣지와 병합 (중복 시 LLM 요약으로 통합)
    ├── force_llm_summary_on_merge 임계값 초과 시 LLM 재요약
    └── 그래프 저장소에 upsert
    │
    ▼
벡터 저장소에 청크/엔티티/관계 임베딩 upsert
```

### 4.6 파이프라인 재개(Resume) 규칙

서버 재시작이나 오류 후 파이프라인이 재개될 때:

| 조건 | 동작 |
|---|---|
| `parse_format=pending_parse` | 전체 파이프라인 처음부터 재실행 |
| `parse_format=raw/lightrag` (추출 완료) | 파싱 건너뜀, Stage 2부터 재실행 |
| `DocStatus=FAILED` + `full_docs` 없음 | 스텁 삭제 후 신규 파일로 재enqueue |

---

## 5. 스토리지 백엔드 시스템

### 5.1 4가지 스토리지 유형

| 유형 | 역할 | 기본 구현체 |
|---|---|---|
| **KV_STORAGE** | LLM 캐시, 텍스트 청크, 문서 정보 | `JsonKVStorage` |
| **VECTOR_STORAGE** | 엔티티/관계/청크 임베딩 벡터 | `NanoVectorDBStorage` |
| **GRAPH_STORAGE** | 엔티티-관계 그래프 구조 | `NetworkXStorage` |
| **DOC_STATUS_STORAGE** | 문서 처리 상태 추적 | `JsonDocStatusStorage` |

### 5.2 스토리지 구현체 전체 목록

**KV Storage (5종)**

| 구현체 | 백엔드 | 특징 |
|---|---|---|
| `JsonKVStorage` | JSON 파일 | 기본값, 로컬 개발 |
| `RedisKVStorage` | Redis | 인메모리, 고속 |
| `MongoKVStorage` | MongoDB | NoSQL, 분산 |
| `PGKVStorage` | PostgreSQL | ACID 보장 |
| `OpenSearchKVStorage` | OpenSearch | 검색 최적화 |

**Vector Storage (7종)**

| 구현체 | 백엔드 | 특징 |
|---|---|---|
| `NanoVectorDBStorage` | NanoVectorDB | 기본값, 경량 |
| `FaissVectorDBStorage` | FAISS | Facebook, CPU/GPU |
| `QdrantVectorDBStorage` | Qdrant | 오픈소스 벡터 DB |
| `MilvusVectorDBStorage` | Milvus | 엔터프라이즈 스케일 |
| `MongoVectorDBStorage` | MongoDB Atlas | Atlas Search 필요 |
| `PGVectorStorage` | PostgreSQL + pgvector | PostgreSQL 통합 |
| `OpenSearchVectorDBStorage` | OpenSearch | k-NN 내장 |

**Graph Storage (6종)**

| 구현체 | 백엔드 | 특징 |
|---|---|---|
| `NetworkXStorage` | NetworkX | 기본값, 인메모리 |
| `Neo4JStorage` | Neo4j | 프로덕션 그래프 DB |
| `MemgraphStorage` | Memgraph | 고성능, Neo4j 호환 |
| `MongoGraphStorage` | MongoDB | 문서 기반 그래프 |
| `PGGraphStorage` | PostgreSQL + AGE | 관계형 그래프 |
| `OpenSearchGraphStorage` | OpenSearch | PPL BFS 지원 |

**DocStatus Storage (5종)**

| 구현체 | 백엔드 |
|---|---|
| `JsonDocStatusStorage` | JSON 파일 (기본) |
| `RedisDocStatusStorage` | Redis |
| `MongoDocStatusStorage` | MongoDB |
| `PGDocStatusStorage` | PostgreSQL |
| `OpenSearchDocStatusStorage` | OpenSearch |

### 5.3 워크스페이스(Workspace) 데이터 격리

```python
# 스토리지 유형별 격리 방식
"JsonKVStorage"         → workspace 서브디렉터리
"NetworkXStorage"       → workspace 서브디렉터리
"NanoVectorDBStorage"   → workspace 서브디렉터리
"FaissVectorDBStorage"  → workspace 서브디렉터리

"RedisKVStorage"        → 컬렉션/키 이름에 workspace 접두사
"MilvusVectorDBStorage" → 컬렉션 이름에 workspace 접두사
"MongoKVStorage"        → 컬렉션 이름에 workspace 접두사

"QdrantVectorDBStorage" → payload 기반 파티셔닝 (Qdrant 멀티테넌시)

"PGKVStorage"           → 테이블의 workspace 컬럼으로 논리 격리
"PGVectorStorage"       → 테이블의 workspace 컬럼으로 논리 격리

"Neo4JStorage"          → 레이블로 격리 (기본값: "base")
"MemgraphStorage"       → 레이블로 격리

"OpenSearch*"           → 인덱스 이름 접두사
```

**스토리지별 workspace 환경변수 오버라이드:**
```
REDIS_WORKSPACE, MILVUS_WORKSPACE, QDRANT_WORKSPACE, MONGODB_WORKSPACE,
POSTGRES_WORKSPACE, NEO4J_WORKSPACE, MEMGRAPH_WORKSPACE, OPENSEARCH_WORKSPACE
```

### 5.4 권장 스토리지 조합

```python
# 개발 환경 (기본값)
kv_storage="JsonKVStorage"
vector_storage="NanoVectorDBStorage"
graph_storage="NetworkXStorage"
doc_status_storage="JsonDocStatusStorage"

# 중소규모 프로덕션 (PostgreSQL 통합)
kv_storage="PGKVStorage"
vector_storage="PGVectorStorage"
graph_storage="PGGraphStorage"
doc_status_storage="PGDocStatusStorage"

# 대규모 프로덕션 (분산 스택)
kv_storage="RedisKVStorage"
vector_storage="MilvusVectorDBStorage"
graph_storage="Neo4JStorage"
doc_status_storage="MongoDocStatusStorage"

# 검색 중심 (OpenSearch 통합)
kv_storage="OpenSearchKVStorage"
vector_storage="OpenSearchVectorDBStorage"
graph_storage="OpenSearchGraphStorage"
doc_status_storage="OpenSearchDocStatusStorage"
```

---

## 6. LLM 제공자 & 역할 라우팅

### 6.1 지원 LLM 제공자

| 제공자 | 바인딩 이름 | LLM | 임베딩 | 비전(VLM) |
|---|---|:-:|:-:|:-:|
| OpenAI / 호환 API | `openai` | ✓ | ✓ | ✓ |
| Azure OpenAI | `azure_openai` | ✓ | ✓ | ✓ |
| Anthropic Claude | `anthropic` | ✓ | - | ✓ |
| Google Gemini | `gemini` | ✓ | ✓ | ✓ |
| AWS Bedrock | `bedrock` | ✓ | ✓ | ✓ |
| Ollama | `ollama` | ✓ | ✓ | ✓ |
| NVIDIA NIM | `nvidia_openai` | ✓ | - | - |
| Zhipu (智谱) | `zhipu` | ✓ | - | - |
| Jina AI | `jina` | - | ✓ | - |
| VoyageAI | `voyageai` | - | ✓ | - |
| HuggingFace | `hf` | ✓ | ✓ | - |
| LlamaIndex | `llama_index` | ✓ | ✓ | - |
| LM Deploy | `lmdeploy` | ✓ | - | - |
| LoLLMs | `lollms` | ✓ | ✓ | ✗ (VLM 불가) |

### 6.2 역할 기반 LLM 라우팅 (`lightrag/llm_roles.py`)

LightRAG는 처리 단계별로 **서로 다른 LLM**을 사용할 수 있습니다:

```
┌──────────────────────────────────────────────────┐
│              역할별 LLM 라우팅                    │
│                                                  │
│  EXTRACT 역할  → 엔티티/관계 추출 + 요약 병합    │
│  KEYWORD 역할  → 쿼리 키워드 추출               │
│  QUERY 역할    → 최종 답변 생성 + bypass 쿼리   │
│  VLM 역할      → 이미지/표/수식 멀티모달 분석   │
│                                                  │
│  (설정 없는 역할은 기본 LLM_* 설정 상속)         │
└──────────────────────────────────────────────────┘
```

**역할별 환경변수 패턴:**
```bash
# EXTRACT 역할 (엔티티 추출용 소형 모델 추천)
EXTRACT_LLM_BINDING=openai
EXTRACT_LLM_MODEL=gpt-5-mini
EXTRACT_LLM_TIMEOUT=60
EXTRACT_MAX_ASYNC_LLM=10

# KEYWORD 역할 (가장 경량 모델 사용 가능)
KEYWORD_LLM_BINDING=openai
KEYWORD_LLM_MODEL=gpt-5-nano

# QUERY 역할 (최고 품질 모델 권장)
QUERY_LLM_BINDING=openai
QUERY_LLM_MODEL=gpt-5

# VLM 역할 (비전 지원 모델 필수)
VLM_LLM_BINDING=openai
VLM_LLM_MODEL=gpt-4o
VLM_MAX_IMAGE_BYTES=5242880
```

**Python SDK에서 역할 설정:**
```python
from lightrag.llm_roles import RoleLLMConfig

rag = LightRAG(
    role_llm_configs={
        "extract": RoleLLMConfig(
            func=gpt_4o_mini_complete,
            max_async=10,
            timeout=60,
        ),
        "query": RoleLLMConfig(
            func=gpt_4o_complete,
        ),
    }
)
```

### 6.3 비대칭 임베딩 설정

```bash
EMBEDDING_ASYMMETRIC=true

# 접두사 기반 (openai, azure_openai, ollama)
EMBEDDING_QUERY_PREFIX="search_query: "
EMBEDDING_DOCUMENT_PREFIX="search_document: "
# NO_PREFIX = 접두사 없음

# 제공자 태스크 기반 (jina, gemini, voyageai)
# → 제공자 파라미터(task/task_type/input_type) 사용, 접두사 불필요
```

> **중요:** 임베딩 설정(모델, 차원, 비대칭 여부) 변경 시 기존 벡터 데이터를 모두 삭제하고 재인덱싱해야 합니다.

---

## 7. 파서 엔진 & 라우팅

### 7.1 `LIGHTRAG_PARSER` 설정 문법

```bash
LIGHTRAG_PARSER=ext:engine-options,ext:engine,*:legacy-R

# 예시
LIGHTRAG_PARSER=docx:native-teP,pdf:mineru-iteP,*:legacy-R
```

**문법 규칙:**
- 왼쪽에서 오른쪽으로 첫 번째 매칭 규칙 적용
- `*` 와일드카드 = 모든 확장자
- `ENGINE-OPTIONS`: 하이픈으로 엔진과 옵션 구분
- `,` 또는 `;`로 규칙 구분

**파일명 힌트 (단일 파일 오버라이드):**
```bash
# 형식: filename.[ENGINE-OPTIONS].ext 또는 filename.[-OPTIONS].ext
paper.[mineru-iteP].pdf      # mineru 엔진, i/t/e/P 옵션
memo.[native-R!].docx        # native 엔진, R 청킹, KG 비활성
notes.[-R].md                # 기본 엔진, R 청킹만 변경
```

### 7.2 처리 옵션(Process Options)

```
i  →  이미지/드로잉 VLM 분석 활성화
t  →  표 VLM 분석 활성화
e  →  수식 VLM 분석 활성화
!  →  엔티티 추출 건너뜀 (벡터 인덱스만 생성, naive/mix 검색은 작동)
F  →  고정 토큰 청킹 (Fixed Token)
R  →  재귀 문자 청킹 (Recursive Character)
V  →  시맨틱 벡터 청킹 (Semantic Vector)
P  →  문단 시맨틱 청킹 (Paragraph Semantic, DOCX native 필요)
```

### 7.3 파서 캐시 시스템

MinerU와 Docling 파서는 결과를 로컬에 캐시합니다:

```
inputs/__parsed__/<파일명>.mineru_raw/   # MinerU 원본 번들 + _manifest.json
inputs/__parsed__/<파일명>.docling_raw/  # Docling 원본 번들 + _manifest.json
```

**캐시 무효화 조건:**
- 소스 파일 크기/해시 변경
- 엔진 버전 변경 (`MINERU_ENGINE_VERSION`, `DOCLING_ENGINE_VERSION`)
- 엔드포인트 변경
- Docling: OCR/수식 설정 변경

**강제 재파싱:**
```bash
LIGHTRAG_FORCE_REPARSE_MINERU=true
LIGHTRAG_FORCE_REPARSE_DOCLING=true
```

---

## 8. 청킹 전략 시스템

### 8.1 4가지 청킹 전략

#### F 전략 - 고정 토큰 청킹 (`chunker/token_size.py`)

```python
def chunking_by_fixed_token(
    tokenizer, content, chunk_token_size,
    *,
    chunk_overlap_token_size: int = 0,
    split_by_character: str | None = None,
    split_by_character_only: bool = False,
) -> list[dict]
```

- 토큰 크기 기반 기계적 분할
- `split_by_character` 설정 시 해당 문자로 먼저 분할
- 레거시 호환성 유지

#### R 전략 - 재귀 문자 청킹 (`chunker/recursive_character.py`)

```python
async def chunking_by_recursive_character(
    tokenizer, content, chunk_token_size,
    *,
    chunk_overlap_token_size: int = 0,
    separators: list[str] = DEFAULT_R_SEPARATORS,
) -> list[dict]
```

**기본 구분자 계층 (`DEFAULT_R_SEPARATORS`):**
```python
("\n\n",   # 문단 (가장 강한 경계)
 "\n",     # 줄
 "。", "！", "？",   # 중국어 문장 끝
 "；", "，",          # 중국어 절 경계
 " ",      # 공백
 "")       # 문자 단위 (마지막 폴백)
```

> **설계 의도:** 영어 `.?!`는 의도적으로 제외 (`0.95`, `e.g.` 같은 숫자/약어 오분할 방지)

#### V 전략 - 시맨틱 벡터 청킹 (`chunker/semantic_vector.py`)

```python
async def chunking_by_semantic_vector(
    tokenizer, content, chunk_token_size,
    *,
    embedding_func: EmbeddingFunc,
    breakpoint_threshold_type: str = "percentile",
    breakpoint_threshold_amount: float | None = None,
    buffer_size: int = 1,
    sentence_split_regex: str = DEFAULT_SENTENCE_SPLIT_REGEX,
) -> list[dict]
```

- LangChain `SemanticChunker` 래퍼
- 문장 임베딩 → 인접 문장 간 거리 → 임계값 초과 지점에서 분할
- `chunk_token_size` 초과 시 R로 2차 분할 (V의 비중복 시맨틱 보존)
- 임베딩 비용이 높으므로 대규모 처리 시 주의

**임계값 유형:**
```
percentile     → 거리 분포의 N번째 백분위수 (기본: 95)
standard_deviation → 평균 + N × 표준편차
interquartile  → IQR 기반
gradient       → 기울기 변화 기반
```

#### P 전략 - 문단 시맨틱 청킹 (`chunker/paragraph_semantic.py`, 60K)

```python
def chunking_by_paragraph_semantic(
    tokenizer, content, chunk_token_size,
    *,
    blocks_path: str,
    chunk_overlap_token_size: int = 100,
) -> list[dict]
```

**처리 단계 (A → E):**
```
.blocks.jsonl (native 파서 fixlevel=0)
    ↓ Stage A: 제목별 기본 청크 구성
    ↓ Stage B: 초과 크기 표 행 경계 슬라이싱 (first/middle/last 역할 부여)
    ↓ Stage B.1: 연속 대형 표 사이 연결 텍스트 양방향 중복
    ↓ Stage C: 앵커 기반 긴 텍스트 재분할
    ↓ Stage D: 계층 인식 2단계 병합 (동등 병합 → 교차 수준 흡수)
    ↓ Stage E: [part n] 접미사 부여
최종 청크 목록
```

**핵심 임계값 (N = `chunk_token_size` = 2000 기본):**

| 임계값 | 공식 | N=2000 시 값 | 역할 |
|---|---|---:|---|
| `target_max` | N | 2000 | 청크 하드 상한 |
| `target_ideal` | 0.75N | 1500 | 동등 병합 목표 |
| `table_max` | 0.625N | 1250 | 표 슬라이싱 촉발 |
| `table_ideal` | 0.375N | 750 | 표 슬라이스 목표 |
| `table_min_last` | 0.32×table_max | 400 | 마지막 슬라이스 역흡수 |
| `small_tail_threshold` | 0.125N | 250 | 꼬리 조각 흡수 |

### 8.2 chunk_options 우선순위 체인

```
① addon_params["chunker"] 명시적 값      (최고 우선순위)
    ↓ (없으면)
② 전략별 환경변수
   CHUNK_R_SIZE, CHUNK_V_SIZE, CHUNK_P_SIZE
   CHUNK_R_OVERLAP_SIZE, CHUNK_P_OVERLAP_SIZE ...
    ↓ (없으면)
③ LightRAG() 생성자 파라미터
   chunk_token_size=..., chunk_overlap_token_size=...
    ↓ (없으면)
④ 레거시 환경변수 (최저 우선순위)
   CHUNK_SIZE=1200, CHUNK_OVERLAP_SIZE=100
```

> **P 전략 특이사항:** `CHUNK_P_SIZE`가 없으면 `DEFAULT_CHUNK_P_SIZE=2000`을 사용합니다. `CHUNK_SIZE`나 생성자 파라미터로 폴백하지 않습니다. 문단 병합을 위해 전역 기본값보다 큰 여유 공간이 필요하기 때문입니다.

### 8.3 청킹 재현성 보장

`full_docs[doc_id]["chunk_options"]`는 **enqueue 시점에 동결(frozen)**됩니다:
- 환경변수 변경 후 재시작해도 이미 enqueue된 문서는 기존 파라미터로 청킹
- 재개(resume) 시에도 동결된 스냅샷을 사용하여 일관성 유지
- 파라미터를 바꾸려면 문서를 삭제 후 재업로드 필요

---

## 9. 쿼리 시스템

### 9.1 5가지 쿼리 모드

```
QueryParam(mode="...")
    │
    ├── "local"   → 특정 엔티티 중심 검색 (좁은 범위, 높은 정밀도)
    │               → 벡터 검색 → 엔티티 → 관계 → 관련 청크
    │
    ├── "global"  → 커뮤니티/요약 기반 광범위 검색
    │               → 전체 그래프 탐색 → 고수준 키워드
    │
    ├── "hybrid"  → local + global 결합 (기본 권장)
    │               → 두 결과를 컨텍스트로 통합
    │
    ├── "naive"   → 순수 벡터 검색 (그래프 미사용)
    │               → chunk 벡터만 검색
    │
    ├── "mix"     → 지식 그래프 + 벡터 통합 (리랭커 사용 시 최적)
    │               → KG 검색 + 벡터 검색 + 재랭킹
    │
    └── "bypass"  → LLM 직접 호출 (RAG 미사용, 대화 히스토리 포함)
```

### 9.2 QueryParam 전체 파라미터

```python
class QueryParam:
    mode: Literal["local", "global", "hybrid", "naive", "mix", "bypass"] = "global"
    
    # 컨텍스트 크기 제어
    top_k: int = 40                   # TOP_K 환경변수
    chunk_top_k: int = 20             # CHUNK_TOP_K 환경변수
    max_entity_tokens: int = 6000     # MAX_ENTITY_TOKENS 환경변수
    max_relation_tokens: int = 8000   # MAX_RELATION_TOKENS 환경변수
    max_total_tokens: int = 30000     # MAX_TOTAL_TOKENS 환경변수
    
    # 출력 제어
    only_need_context: bool = False   # 컨텍스트만 반환
    only_need_prompt: bool = False    # 프롬프트만 반환
    response_type: str = "Multiple Paragraphs"
    stream: bool = False              # SSE 스트리밍
    
    # 대화 및 사용자 지시
    conversation_history: list[dict] = []  # [{"role": "user", "content": "..."}]
    user_prompt: str | None = None    # RAG 검색 후 LLM 지시 (검색에 영향 없음)
    
    # 키워드 직접 지정
    hl_keywords: list[str] = []       # 고수준 키워드 (직접 제공)
    ll_keywords: list[str] = []       # 저수준 키워드 (직접 제공)
    
    # 재랭킹
    enable_rerank: bool = True
    include_references: bool = True
    include_chunk_content: bool = False  # 청크 원문 포함 여부
```

### 9.3 쿼리 처리 흐름 (`operate.py`)

```
aquery(query, param)
    │
    ▼
get_keywords_from_query()  ← KEYWORD 역할 LLM
    │ → hl_keywords (고수준, global 검색용)
    │ → ll_keywords (저수준, local 검색용)
    │
    ▼
kg_query() 또는 naive_query()
    │
    ├── 벡터 검색 (엔티티 벡터, 청크 벡터)
    ├── 그래프 탐색 (엔티티 → 관계 → 이웃)
    ├── 토큰 예산 적용 (max_entity_tokens, max_relation_tokens)
    └── 재랭킹 (enable_rerank=True 시)
    │
    ▼
컨텍스트 문자열 구성 (엔티티 + 관계 + 청크)
    │
    ▼
LLM 응답 생성 (QUERY 역할 LLM)
    │
    ▼
응답 + 참조 정보 반환
```

### 9.4 Ollama 에뮬레이션 모드 (`api/routers/ollama_api.py`)

LightRAG를 Ollama 모델처럼 사용할 수 있습니다:

```bash
# Open WebUI에서 Ollama 연결로 LightRAG 사용
# 기본 모델: lightrag:latest

# 쿼리 접두사로 모드 선택
/local 질문    → local 모드
/mix 질문      → mix 모드
/bypass 질문   → RAG 없이 LLM 직접 호출
/context 질문  → 컨텍스트만 반환

# 사용자 프롬프트 주입
/mix[mermaid 형식으로] 인물 관계도 그려줘
```

---

## 10. API 서버 구조

### 10.1 FastAPI 서버 (`lightrag/api/lightrag_server.py`, 111K)

```
FastAPI 앱
    │
    ├── /docs          (Swagger UI)
    ├── /redoc         (ReDoc)
    ├── /health        (상태 확인, 인증 화이트리스트 기본)
    ├── /webui         (React 프론트엔드 서빙)
    │
    ├── /documents/    (문서 관리 라우터)
    ├── /query         (쿼리 라우터)
    ├── /graph/        (그래프 라우터)
    │
    ├── /api/          (Ollama 호환 API)
    └── /api/*         (Ollama 화이트리스트)
```

**서버 시작 방법:**
```bash
lightrag-server                   # Uvicorn 단일 프로세스
lightrag-gunicorn --workers 4     # Gunicorn + Uvicorn (프로덕션, Windows 미지원)
```

### 10.2 문서 API (`/documents/`) - 16개 엔드포인트

| 메서드 | 경로 | 기능 |
|---|---|---|
| `POST` | `/documents/scan` | 입력 디렉터리 스캔 → 자동 색인 |
| `POST` | `/documents/upload` | 파일 업로드 → 색인 |
| `POST` | `/documents/text` | 텍스트 직접 삽입 |
| `POST` | `/documents/texts` | 텍스트 배치 삽입 |
| `GET` | `/documents/paginated` | 문서 목록 (페이지네이션) |
| `GET` | `/documents/pipeline_status` | 파이프라인 상태 |
| `GET` | `/documents/track_status/{id}` | 처리 추적 |
| `GET` | `/documents/{doc_id}` | 문서 상세 조회 |
| `DELETE` | `/documents/{doc_id}` | 문서 삭제 |
| `DELETE` | `/documents/clear` | 전체 문서 삭제 |

### 10.3 쿼리 API (`/query`, `/query/stream`) - 3개 엔드포인트

| 메서드 | 경로 | 기능 |
|---|---|---|
| `POST` | `/query` | 일반 쿼리 응답 |
| `POST` | `/query/stream` | SSE 스트리밍 응답 |
| `POST` | `/query/data` | 컨텍스트 데이터만 반환 |

### 10.4 그래프 API (`/graph/`) - 5개 엔드포인트

| 메서드 | 경로 | 기능 |
|---|---|---|
| `GET` | `/graph/labels` | 엔티티 타입 목록 |
| `GET` | `/graph/entities` | 엔티티 조회/필터 |
| `GET` | `/graph/relationships` | 관계 조회/필터 |
| `POST` | `/graph/edit` | 엔티티/관계 편집 |
| `GET` | `/graph/stats` | 그래프 통계 |

### 10.5 인증 & 보안

**API Key 인증:**
```bash
LIGHTRAG_API_KEY=your-secure-api-key-here
WHITELIST_PATHS=/health,/api/*
# 헤더: X-API-Key: your-key
```

**JWT 계정 인증 (웹 로그인):**
```bash
AUTH_ACCOUNTS='admin:{bcrypt}$2b$12$...,user1:pass456'
TOKEN_SECRET=your-jwt-secret
TOKEN_EXPIRE_HOURS=4

# 해시 생성 도구
lightrag-hash-password --username admin
```

> **보안 주의:** API Key만 설정하면 게스트 계정으로 모든 API 접근 가능합니다. 완전한 보안을 위해 API Key와 JWT 계정 인증을 동시에 설정하세요.

### 10.6 Nginx 역방향 프록시 설정

```nginx
server {
    listen 80;
    client_max_body_size 8M;  # 기본 (LLM 쿼리 긴 컨텍스트용)

    # 파일 업로드 엔드포인트
    location /documents/upload {
        client_max_body_size 100M;  # MAX_UPLOAD_SIZE와 일치
        proxy_pass http://localhost:9621;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }

    # 스트리밍 엔드포인트 (gzip 비활성화 필수)
    location ~ ^/(query/stream|api/chat|api/generate) {
        gzip off;
        proxy_pass http://localhost:9621;
        proxy_read_timeout 300s;
    }

    location / {
        proxy_pass http://localhost:9621;
    }
}
```

### 10.7 다중 사이트 배포 (`LIGHTRAG_API_PREFIX`)

단일 서버에서 여러 LightRAG 인스턴스를 다른 URL 접두사로 서빙할 때:

```bash
# 인스턴스 1
LIGHTRAG_API_PREFIX=/site01
lightrag-server --port 9621 --workspace site01

# 인스턴스 2
LIGHTRAG_API_PREFIX=/site02
lightrag-server --port 9622 --workspace site02
```

`SmartStaticFiles`가 런타임에 WebUI 설정을 주입하여, 하나의 프론트엔드 빌드로 모든 접두사를 지원합니다.

---

## 11. WebUI 아키텍처

### 11.1 기술 스택

| 항목 | 기술 |
|---|---|
| 프레임워크 | React 19 |
| 언어 | TypeScript |
| 빌드 도구 | Vite |
| 스타일링 | Tailwind CSS 4 |
| 상태 관리 | Zustand |
| UI 컴포넌트 | Radix UI |
| 그래프 시각화 | D3.js / Sigma.js |
| 수식 렌더링 | KaTeX |
| 다이어그램 | Mermaid |
| 패키지 관리 | Bun |
| 다국어 | react-i18next (13개 언어) |

### 11.2 주요 컴포넌트 구조

```
lightrag_webui/src/
│
├── features/                     # 주요 페이지
│   ├── DocumentManager.tsx       # 문서 관리 (업로드, 상태 추적, 삭제)
│   ├── RetrievalTesting.tsx      # 쿼리 테스트 & 결과 뷰어
│   ├── GraphViewer.tsx           # 그래프 시각화
│   ├── ApiSite.tsx               # Swagger UI 연동
│   ├── LoginPage.tsx             # JWT 로그인
│   └── SiteHeader.tsx            # 헤더 (언어/테마/워크스페이스 전환)
│
├── components/
│   ├── graph/                    # 그래프 시각화 컴포넌트 (18개)
│   │   ├── GraphControl.tsx      # 레이아웃/필터 제어
│   │   ├── LayoutsControl.tsx    # 그래프 레이아웃 선택
│   │   ├── GraphLabels.tsx       # 엔티티 타입 필터
│   │   ├── GraphSearch.tsx       # 노드 검색
│   │   ├── PropertiesView.tsx    # 속성 조회/편집
│   │   ├── PropertyEditDialog.tsx # 속성 편집 다이얼로그
│   │   └── Settings.tsx          # 그래프 설정
│   │
│   ├── documents/                # 문서 관련 컴포넌트
│   ├── retrieval/                # 검색 관련 컴포넌트
│   └── ui/                       # Radix UI 래퍼 (30개)
│
├── stores/                       # Zustand 상태 관리
│   ├── state.ts                  # 백엔드 연결 상태 (apiKey, baseUrl, workspace)
│   ├── settings.ts               # 사용자 설정 (쿼리 모드, 테마, 언어)
│   └── graph.ts                  # 그래프 상태 (노드, 엣지, 선택, 필터)
│
├── api/
│   └── lightrag.ts               # LightRAG API 클라이언트 (모든 엔드포인트)
│
└── locales/                      # 다국어 지원
    ├── en.json                   # 영어
    ├── zh.json                   # 중국어 간체
    ├── ko.json                   # 한국어
    └── (10개 추가 언어)
```

### 11.3 프론트엔드 빌드

```bash
cd lightrag_webui
bun install --frozen-lockfile
bun run build    # dist/ 에 빌드 결과물 생성
bun run dev      # 개발 서버 (Vite HMR, LIGHTRAG_API_PREFIX 지원)
```

---

## 12. 파이프라인 동시성 제어

### 12.1 `pipeline_status` 딕셔너리 (동시성 제어 핵심)

```python
pipeline_status: dict = {
    "busy": bool,               # 처리 루프 실행 중
    "destructive_busy": bool,   # 삭제/초기화 작업 중 (enqueue 완전 차단)
    "scanning": bool,           # /documents/scan 실행 중
    "scanning_exclusive": bool, # scan 분류 단계 (완전 배타적)
    "pending_enqueues": int,    # 예약된 enqueue 수 (scan이 확인)
    "request_pending": bool,    # 처리 루프에 새 작업 알림
}
```

### 12.2 진입점별 동시성 동작

| 진입점 | 차단 조건 | 허용 조건 |
|---|---|---|
| `/documents/upload`, `/documents/text` | `scanning_exclusive` OR `destructive_busy` | `busy=True`여도 허용 (request_pending으로 알림) |
| `/documents/scan` | `busy` OR `scanning` OR `pending_enqueues>0` | 완전히 유휴 상태 |
| `/documents/clear`, `/documents/delete` | `busy` OR `scanning` OR `pending_enqueues>0` | 완전히 유휴 상태 |

### 12.3 병렬화 파라미터 (파이프라인 처리량)

```
PENDING 문서
    ↓ enqueue_serialize 잠금 (중복 제거 일관성)
    │
    ├── q_native  → [N1 Native 파서 워커] → MAX_PARALLEL_PARSE_NATIVE=5
    ├── q_mineru  → [N2 MinerU 파서 워커] → MAX_PARALLEL_PARSE_MINERU=1
    ├── q_docling → [N3 Docling 파서 워커] → MAX_PARALLEL_PARSE_DOCLING=1
    │              ↓ QUEUE_SIZE_DEFAULT=100
    ├── q_analyze → [N4 VLM 분석 워커] → MAX_PARALLEL_ANALYZE=5
    │              ↓ QUEUE_SIZE_INSERT=4 (백프레셔)
    └── q_process → [N5 엔티티추출 워커] → MAX_PARALLEL_INSERT=2
```

**튜닝 지침:**
- Native 전용: `MAX_PARALLEL_PARSE_NATIVE=CPU코어수`
- MinerU 로컬 GPU: `MAX_PARALLEL_PARSE_MINERU=2~3` (VRAM 충분 시)
- MinerU 클라우드: `MAX_PARALLEL_PARSE_MINERU=3~5` (클라우드 할당량 기준)
- LLM 속도 제한 시: `MAX_PARALLEL_INSERT` 먼저 낮추고, 그 다음 `MAX_PARALLEL_ANALYZE`

### 12.4 중복 제거 2중 게이트

```
파일 업로드/텍스트 삽입
    │
    ├── Gate 1: basename 중복 검사
    │   - canonical_basename 기준 (힌트 제거 후)
    │   - abc.docx = abc.[native].docx = abc.[native-iteP].docx
    │   - doc_status에 동일 이름 있으면 409
    │
    └── Gate 2: content_hash 중복 검사
        - 다른 파일명이어도 내용이 같으면 FAILED(duplicate) 처리
        - legacy: enqueue 시 즉시 검사
        - native/mineru/docling: 파싱 완료 후 검사
```

---

## 13. 멀티모달 분석 (Sidecar 시스템)

### 13.1 Sidecar 파일 구조

Native/MinerU/Docling 파서가 생성하는 LightRAG Document 형식:

```
<파일명>.parsed/
├── <파일명>.blocks.jsonl    # 블록 시퀀스
│   ├── {"type": "meta", "format": "lightrag", "version": "1.0", ...}
│   ├── {"type": "content", "blockid": "...", "heading": "...", "content": "...", ...}
│   └── ...
│
├── <파일명>.drawings.json   # {"version": "1.0", "drawings": {id: item, ...}}
├── <파일명>.tables.json     # {"version": "1.0", "tables": {id: item, ...}}
├── <파일명>.equations.json  # {"version": "1.0", "equations": {id: item, ...}}
└── <파일명>.blocks.assets/  # 원본 이미지 파일들
```

### 13.2 Content 인라인 플레이스홀더

`.blocks.jsonl`의 `content` 필드 내부 멀티모달 태그:

```xml
<!-- 표 플레이스홀더 -->
<table id="tb-<hash>-0001" format="json">[[...]]</table>
<table id="tb-<hash>-0002" format="html"><thead>...</thead><tbody>...</tbody></table>

<!-- 이미지/드로잉 플레이스홀더 (자기 폐쇄) -->
<drawing id="im-<hash>-0003" format="png" path="file.blocks.assets/image3.png" caption="그림 1" />

<!-- 수식 플레이스홀더 -->
<equation id="eq-<hash>-0004" format="latex">C = \frac{P \cdot T}{V^2}</equation>
<!-- 인라인 수식 (id 없음, sidecar에 저장 안 됨) -->
<equation format="latex">E = mc^2</equation>
```

### 13.3 LLM 분석 결과 (`llm_analyze_result`)

| 항목 | status | 필드 |
|---|---|---|
| **드로잉** | `success` | `name`, `type`(12-값 enum), `description` |
| **표** | `success` | `name`, `description` |
| **수식** | `success` | `name`, `description`, `equation`(LaTeX 정규화) |
| 모든 유형 | `skipped` | `message`(건너뜀 이유) |
| 모든 유형 | `failure` | `message`(진단 정보) |

`analyze_multimodal`은 **멱등적**입니다: 재실행 시 기존 결과를 덮어씁니다. LLM 캐시가 있으면 provider를 재호출하지 않습니다.

### 13.4 surrounding 컨텍스트

VLM/LLM이 멀티모달 항목 분석 시 자동 주입되는 앞뒤 텍스트:

```python
surrounding = {
    "leading": "...",   # 항목 앞의 텍스트 (최대 SURROUNDING_LEADING_MAX_TOKENS)
    "trailing": "..."   # 항목 뒤의 텍스트 (최대 SURROUNDING_TRAILING_MAX_TOKENS)
}
```

- 기본값: 각 2000 토큰
- 내부 파서 속성(`id/path/src`)은 제거, 캡션 등 모델이 볼 수 있는 속성만 유지
- `enrich_sidecars_with_surrounding`은 멱등적: 설정 변경 후 재분석 시 자동 갱신

---

## 14. 핵심 환경 변수 레퍼런스

### 14.1 서버 설정

```bash
HOST=0.0.0.0
PORT=9621
WORKERS=2                          # Gunicorn 워커 수 (≤ 2×코어+1)
TIMEOUT=150                        # Gunicorn 워커 타임아웃
LIGHTRAG_API_PREFIX=/site01        # 역방향 프록시 경로 접두사
WEBUI_TITLE="My LightRAG KB"
WEBUI_DESCRIPTION="Graph Based RAG"
MAX_UPLOAD_SIZE=104857600          # 100MB
```

### 14.2 LLM 설정

```bash
LLM_BINDING=openai                 # 바인딩: openai|ollama|gemini|bedrock|anthropic|...
LLM_MODEL=gpt-4o-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=sk-...
MAX_ASYNC=4                        # 동시 LLM 요청 수
TIMEOUT=150                        # LLM 요청 타임아웃(초)
ENTITY_EXTRACTION_USE_JSON=true    # JSON 구조화 추출 (권장)
SUMMARY_LANGUAGE=Korean            # 엔티티/관계 요약 언어
```

### 14.3 임베딩 설정

```bash
EMBEDDING_BINDING=openai
EMBEDDING_MODEL=text-embedding-3-large
EMBEDDING_BINDING_HOST=https://api.openai.com/v1
EMBEDDING_BINDING_API_KEY=sk-...
EMBEDDING_DIM=3072
EMBEDDING_TOKEN_LIMIT=8192
EMBEDDING_FUNC_MAX_ASYNC=8
# 비대칭 임베딩 (선택)
EMBEDDING_ASYMMETRIC=true
EMBEDDING_QUERY_PREFIX="search_query: "
EMBEDDING_DOCUMENT_PREFIX="search_document: "
```

### 14.4 VLM 멀티모달 설정

```bash
VLM_PROCESS_ENABLE=true
VLM_LLM_BINDING=openai
VLM_LLM_MODEL=gpt-4o
VLM_LLM_BINDING_HOST=https://api.openai.com/v1
VLM_LLM_BINDING_API_KEY=sk-...
VLM_MAX_IMAGE_BYTES=5242880        # 5MB
SURROUNDING_LEADING_MAX_TOKENS=2000
SURROUNDING_TRAILING_MAX_TOKENS=2000
```

### 14.5 파서 & 청킹 설정

```bash
LIGHTRAG_PARSER=*:native-teP,*:legacy-R  # 파서 라우팅 규칙

# 청킹 (전략별 전용 설정 우선, 없으면 레거시 전역값)
CHUNK_SIZE=1200                    # 전역 기본 청크 크기
CHUNK_OVERLAP_SIZE=100             # 전역 기본 겹침

CHUNK_R_SIZE=1200                  # R 전략 전용
CHUNK_R_OVERLAP_SIZE=100
CHUNK_R_SEPARATORS=["\n\n","\n"]   # JSON 배열 문자열

CHUNK_V_BREAKPOINT_THRESHOLD_TYPE=percentile  # V 전략
CHUNK_V_BREAKPOINT_THRESHOLD_AMOUNT=95.0

CHUNK_P_SIZE=2000                  # P 전략 전용 (레거시 CHUNK_SIZE 무시)
CHUNK_P_OVERLAP_SIZE=100
```

### 14.6 파이프라인 병렬화

```bash
MAX_PARALLEL_PARSE_NATIVE=5
MAX_PARALLEL_PARSE_MINERU=1
MAX_PARALLEL_PARSE_DOCLING=1
MAX_PARALLEL_ANALYZE=5             # VLM 분석
MAX_PARALLEL_INSERT=2              # 엔티티 추출 + 그래프 구축
QUEUE_SIZE_DEFAULT=100
QUEUE_SIZE_INSERT=4                # 백프레셔용 작은 큐
```

### 14.7 쿼리 설정

```bash
TOP_K=40                           # 엔티티/관계 검색 수
CHUNK_TOP_K=20                     # 청크 검색 수
MAX_ENTITY_TOKENS=6000
MAX_RELATION_TOKENS=8000
MAX_TOTAL_TOKENS=30000
COSINE_THRESHOLD=0.2
RERANK_BINDING=cohere              # null|cohere|jina|aliyun
RERANK_BY_DEFAULT=true
```

### 14.8 스토리지 선택

```bash
LIGHTRAG_KV_STORAGE=JsonKVStorage
LIGHTRAG_VECTOR_STORAGE=NanoVectorDBStorage
LIGHTRAG_GRAPH_STORAGE=NetworkXStorage
LIGHTRAG_DOC_STATUS_STORAGE=JsonDocStatusStorage

WORKING_DIR=./rag_storage
WORKSPACE=default                  # 데이터 격리 네임스페이스
```

---

## 15. 코드 규모 요약

### 핵심 모듈별 코드 라인 수

| 파일 | 라인 수 | 역할 |
|---|---|---|
| `lightrag/operate.py` | 5,792 | 엔티티/관계 추출, 그래프 쿼리 핵심 로직 |
| `lightrag/pipeline.py` | 4,787 | 4단계 문서 파이프라인 |
| `lightrag/lightrag.py` | 4,041 | LightRAG 메인 클래스 (dataclass + Mixin) |
| `lightrag/utils.py` | 3,907 | 토큰화, 임베딩, 캐시, 해싱 유틸 |
| `lightrag/utils_graph.py` | 1,774 | 그래프 탐색 유틸리티 |
| `lightrag/base.py` | 1,050 | 스토리지 추상 인터페이스 |
| `lightrag/api/routers/document_routes.py` | 4,246 | 문서 관리 REST API |
| `lightrag/api/routers/query_routes.py` | 1,164 | 쿼리 REST API |
| **lightrag/kg/ (전체)** | **26,391** | **14개 스토리지 백엔드** |
| `lightrag/kg/postgres_impl.py` | 7,225 | PostgreSQL 통합 스택 |
| `lightrag/kg/opensearch_impl.py` | 3,784 | OpenSearch 통합 스택 |
| `lightrag/kg/mongo_impl.py` | 2,713 | MongoDB 통합 스택 |
| `lightrag/chunker/paragraph_semantic.py` | ~60K (byte) | P 전략 청킹 구현 |

### 전체 프로젝트 규모

| 구성 요소 | 파일 수 | 비고 |
|---|---|---|
| `lightrag/` Python 패키지 | ~150개 파일 | 핵심 엔진 |
| `lightrag_webui/src/` TypeScript | 98개 파일 | React 프론트엔드 |
| `tests/` | 15개 서브디렉터리 | pytest + asyncio |
| `docs/` | 36개 파일 | 영어 + 한국어 |
| `examples/` | 22개 파일 | 사용 예제 |
| `env.example` | 46,981줄 | 전체 환경변수 레퍼런스 |

---

## 부록: 자주 발생하는 문제와 해결책

### `AttributeError: __aenter__`
```python
# 원인: initialize_storages() 미호출
rag = LightRAG(...)
await rag.initialize_storages()  # 반드시 호출 후 사용
```

### 임베딩 모델 변경 후 오류
```bash
# 벡터 데이터 전체 삭제 후 재인덱싱 필요
rm -rf ./rag_storage/vdb_*
```

### P 전략이 R처럼 동작
1. `LIGHTRAG_PARSER=docx:native-P` 설정 확인
2. `.blocks.jsonl` 파일 존재 확인
3. 로그에서 `fallback to recursive_character` 메시지 확인

### Ollama 컨텍스트 부족
```bash
OLLAMA_LLM_NUM_CTX=32768  # 기본 8K는 LightRAG에 부족
```

### Gunicorn macOS 멀티워커 오류
```bash
export OBJC_DISABLE_INITIALIZE_FORK_SAFETY=YES
lightrag-gunicorn --workers 2
```
