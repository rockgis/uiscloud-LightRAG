# 저장소 가이드라인

## 프로젝트 개요

LightRAG는 향상된 정보 검색을 위해 그래프 기반 지식 표현을 사용하는 검색 증강 생성(RAG, Retrieval-Augmented Generation) 프레임워크입니다. 시스템은 문서에서 엔티티(entities)와 관계(relationships)를 추출하고, 지식 그래프를 구축하며, 쿼리에 대해 여러 검색 모드(`local`, `global`, `hybrid`, `mix`, `naive`)를 사용합니다.

## 프로젝트 구조

최상위 디렉토리:

- **lightrag/**: 핵심 Python 패키지 — 아래 *모듈 레이아웃*을 참조하세요.
- **lightrag_webui/**: React 19 + TypeScript 클라이언트 (Bun + Vite + Tailwind). UI 컴포넌트는 `src/`에 있습니다.
- **scripts/**: `test.sh` (권장 테스트 실행기), `setup/` 대화형 환경 마법사 (setup.sh를 직접 호출하는 대신 `make env-*`를 사용하세요 — *설정 > 설정 마법사 출력물* 참조), 릴리스 도구.
- **tests/**: Pytest 커버리지, `lightrag/`를 미러링하는 하위 디렉토리로 구성됩니다 (아래 *테스트* 참조). 작업 데이터셋은 `inputs/`, `rag_storage/`, `temp/`에 보관됩니다. 배포 관련 파일은 `docs/`, `k8s-deploy/`, compose 파일에 있습니다.

### 모듈 레이아웃 (`lightrag/`)

- **lightrag.py**: 메인 오케스트레이터 클래스(`LightRAG`) — 믹스인(mixins)으로 조합됩니다 (아래 *LightRAG 클래스 구성* 참조). `ainsert_custom_kg`, `_insert_done`, `_process_extract_entities`, `_refresh_addon_params_cache`, `addon_params` 접근자를 포함합니다. 중요: 인스턴스화 후 항상 `await rag.initialize_storages()`를 호출해야 합니다.
- **pipeline.py**: `_PipelineMixin` — 문서 수집 파이프라인(`apipeline_enqueue_documents`, `apipeline_process_enqueue_documents`, `apipeline_process_error_documents`), `parse_native` / `parse_mineru` / `parse_docling` 파서 디스패처, 멀티모달 분석, 검증, 워커 스캐폴딩을 소유합니다.
- **utils_pipeline.py**: 파이프라인 믹스인 및 다른 진입점이 공유하는 순수 헬퍼: doc-status 필드 접근, 문서 식별(소스 키, 콘텐츠 해시), 파싱된 아티팩트 경로 해석, 파서 페이로드 정규화, 멀티모달 엔티티 보강, `make_lightrag_doc_content`.
- **llm_roles.py**: `RoleSpec` / `RoleLLMConfig` / `_RoleLLMState` / `ROLES` 레지스트리 및 `_RoleLLMMixin` — 역할 정규화, 빌더 등록, 래퍼 재구축, 런타임 설정 업데이트, 큐 정리, 정제된 설정 내보내기, 큐 상태 보고. 역할별 동작은 프로바이더 모듈 대신 여기로 라우팅하세요.
- **storage_migrations.py**: `_StorageMigrationMixin` — `check_and_migrate_data`, `_migrate_entity_relation_data`, `_migrate_chunk_tracking_storage`.
- **addon_params.py**: `ObservableAddonParams` 및 `default_addon_params` / `normalize_addon_params` 헬퍼.
- **operate.py**: 엔티티/관계 추출, 청킹(chunking), 다중 모드 검색 로직을 포함한 핵심 추출 및 쿼리 작업.
- **base.py**: 스토리지 백엔드를 위한 추상 기본 클래스(`BaseKVStorage`, `BaseVectorStorage`, `BaseGraphStorage`, `BaseDocStatusStorage`).
- **kg/**: 스토리지 구현 (JSON, NetworkX, Neo4j, PostgreSQL, MongoDB, Redis, Milvus, Qdrant, Faiss, Memgraph, OpenSearch, NanoVectorDB). 백엔드 레지스트리(`STORAGE_IMPLEMENTATIONS` / `STORAGES`)는 `kg/__init__.py`에 있습니다. `kg/factory.py::get_storage_class()`가 설정에서 백엔드 클래스를 해석합니다.
- **llm/**: LLM 및 임베딩 프로바이더 바인딩 (OpenAI, Ollama, Azure, Gemini, Bedrock, Anthropic 등). 모두 캐싱 지원을 포함한 비동기입니다.
- **parser/**: 통합 파싱 계층. `parser/routing.py`가 `legacy`, `native`, `mineru`, `docling` 흐름에 대한 엔진과 파일명 힌트를 해석합니다. `parser/debug.py`는 `parser/cli.py` 디버그 진입점(`python -m lightrag.parser.cli`)을 위한 오프라인 LightRAG 스텁을 제공합니다. 네이티브 포맷 파서는 `parser/` 아래의 형제 하위 패키지에 있습니다 (현재 `parser/docx/`). 외부 HTTP 기반 어댑터는 `parser/external/` (`mineru`, `docling`) 아래에 `parser/external/_common.py`, `_manifest.py`, `_zip.py`의 공유 헬퍼와 함께 있습니다.
- **chunker/**: 청킹 전략 (토큰 크기, 재귀 문자, 시맨틱 벡터, 단락 시맨틱).
- **api/**: FastAPI 서비스(`lightrag_server.py`), REST 엔드포인트 및 Ollama 호환 API. 라우터는 `routers/`에 있으며, 정적 Swagger 에셋, 패키지된 WebUI 출력, Gunicorn 런처도 포함됩니다.

## 핵심 아키텍처

### LightRAG 클래스 구성

`LightRAG`는 집중화된 믹스인들로 조합됩니다 (이전의 모놀리식 `lightrag.py`에서 분리됨):

```
LightRAG → _RoleLLMMixin → _StorageMigrationMixin → _PipelineMixin → object
```

`LightRAG`의 `@final` 데코레이터는 유지됩니다 — 믹스인 계층화는 내부 구현 세부 사항이며, 외부 서브클래싱 인터페이스가 아닙니다. 공개 API(`ainsert`, `aquery`, `ainsert_custom_kg`, `initialize_storages` 등)는 변경되지 않습니다. `ainsert_custom_kg`와 그 내부 구성 로직, `_insert_done`, `_process_extract_entities`, `_refresh_addon_params_cache`, `addon_params` 프로퍼티 접근자는 여러 흐름에 걸쳐 있거나 프롬프트 프로파일 상태에 의존하기 때문에 `LightRAG` 자체에 남아 있습니다.

### 스토리지 계층

LightRAG는 플러그인 가능한 백엔드를 가진 4가지 스토리지 유형을 사용합니다:
- **KV_STORAGE**: LLM 응답 캐시, 텍스트 청크, 문서 정보
- **VECTOR_STORAGE**: 엔티티/관계/청크 임베딩
- **GRAPH_STORAGE**: 엔티티-관계 그래프 구조
- **DOC_STATUS_STORAGE**: 문서 처리 상태 추적

각 `LightRAG` 인스턴스는 데이터 격리를 위해 `workspace` 파라미터를 전달할 수 있습니다. 구현은 스토리지 유형별로 다릅니다:
- **파일 기반**: `working_dir` 아래의 하위 디렉토리.
- **컬렉션 기반**: 컬렉션 이름 프리픽스.
- **관계형 DB**: 워크스페이스 컬럼 필터링.
- **Qdrant**: 페이로드 기반 파티셔닝.

### 파이프라인 동시성 계약

문서 수집 파이프라인은 `lightrag.kg.shared_storage`의 워크스페이스별 공유 딕셔너리인 `pipeline_status`를 통해 동시 쓰기를 조정합니다. 이 필드들은 `get_namespace_lock("pipeline_status", workspace=...)` 하에서 변경됩니다:

- **`busy`**: 모든 파이프라인 바쁨 상태. 처리 루프와 파괴적 작업(clear / 문서별 삭제) 모두에 의해 설정됩니다. 단독으로 `busy=True`는 큐에 넣기를 차단하지 않습니다 — 독점 부분집합에 대해서는 `destructive_busy`를 참조하세요.
- **`destructive_busy`**: 바쁨 작업이 `/documents/clear` 또는 `/documents/{doc_id}` (삭제)입니다. 스토리지를 삭제하고 입력 파일을 제거합니다. 이 기간의 동시 큐 등록은 해체 중인 스토리지에 쓰게 되어 문서가 자동으로 손실됩니다. 예약과 큐 등록 마지막 줄 가드는 이것이 True일 때 거부합니다.
- **`scanning`**: `/documents/scan` 작업이 실행 중입니다(전체 수명 주기: 분류 + 처리). `/scan` 엔드포인트가 겹치는 스캔을 거부하는 데 사용합니다. 단독으로는 업로드/삽입을 차단하지 않습니다.
- **`scanning_exclusive`**: 스캔 작업의 분류 단계에서만 True입니다. `run_scanning_process`가 `doc_status`를 읽어 파일을 분류하고 있을 때입니다. 예약과 큐 등록 마지막 줄 가드는 이것이 설정될 때 거부합니다. 스캔이 처리 단계로 전환하기 전에 지워집니다.
- **`pending_enqueues`**: 슬롯을 예약했지만 (`_reserve_enqueue_slot`을 통해) 백그라운드 작업이 아직 완료되지 않은 `/upload`, `/text`, `/texts` 엔드포인트의 수입니다. 스캔 엔드포인트만 읽습니다.
- **`request_pending`**: 실행 중인 처리 루프에 대한 넛지(nudge). 루프가 각 배치 후 이를 확인하고 설정되어 있으면 `doc_status`를 다시 쿼리합니다.

상호 배제 규칙 (모두 락 내에서 원자적으로 확인):

| 작업 | 거부 조건 | 쓰기 |
|---|---|---|
| `_reserve_enqueue_slot` | `scanning_exclusive` 또는 `destructive_busy` | `pending_enqueues++` |
| `apipeline_enqueue_documents` (마지막 줄 가드) | (`scanning_exclusive`이고 `from_scan`이 아님) 또는 `destructive_busy` | — |
| 스캔 엔드포인트 예약 | `busy` 또는 `scanning` 또는 `pending_enqueues > 0` | `scanning = True` |
| `apipeline_process_enqueue_documents` 진입 | (이미 바쁨 → `request_pending` 설정, 반환) | `busy = True` (`destructive_busy`는 아님) |
| `clear_documents` / `delete_document` (동기 예약) | `busy` 또는 `scanning` 또는 `pending_enqueues > 0` | `busy = True`, `destructive_busy = True` |

이 계약은 **동시 큐 등록 + 처리**를 허용합니다: 새로 업로드된 문서가 루프가 배치 중간에 있는 동안 `doc_status`에 도달하고, 루프는 현재 배치 후에 `request_pending`을 확인하고, `doc_status`를 다시 쿼리하여 새 PENDING 행을 선택합니다.

나머지 — `full_docs` vs `doc_status`의 쓰기 순서, 중복 제거-및-upsert 주변의 워크스페이스 범위 `enqueue_serialize` 락, `from_scan=True` 우회 — 에 대해서는 `lightrag/pipeline.py`의 `apipeline_enqueue_documents` 및 `apipeline_process_enqueue_documents` 독스트링을 참조하세요.

### 쿼리 모드

- **local**: 특정 엔티티에 초점을 맞춘 문맥 의존적 검색
- **global**: 커뮤니티/요약 기반 광범위한 지식 검색
- **hybrid**: local과 global의 조합
- **naive**: 그래프 없는 직접 벡터 검색
- **mix**: KG와 벡터 검색 통합 (리랭커와 함께 권장)

## 개발 명령어

### 설정
```bash
# uv로 설치
uv sync
source .venv/bin/activate  # 또는 Windows에서: .venv\Scripts\activate

# API 지원으로 설치
uv sync --extra api

# 특정 extras 설치
uv sync --extra offline-storage  # 스토리지 백엔드
uv sync --extra offline-llm      # LLM 프로바이더
uv sync --extra test             # 테스트 의존성
```

### API 서버
```bash
# 환경 복사 및 설정
cp env.example .env  # LLM/임베딩 설정으로 편집

# WebUI 빌드
cd lightrag_webui
bun install --frozen-lockfile
bun run build
cd ..

# 서버 실행
lightrag-server                                           # 프로덕션
uvicorn lightrag.api.lightrag_server:app --reload        # 개발
lightrag-gunicorn                                         # 멀티 워커 (gunicorn)
```

### WebUI
```bash
cd lightrag_webui
bun install --frozen-lockfile      # 의존성 설치
bun run dev                        # 개발 서버 (Node + Vite)
bun run dev:bun                    # 개발 서버 (Bun 네이티브)
bun run build                      # 프로덕션 빌드
bun run preview                    # 프로덕션 빌드 미리보기
bun run lint                       # *.ts/tsx/js/jsx에 대한 ESLint

# 테스트 — Bun 내장 실행기 (Vitest/Jest가 아님)
bun test                           # 모든 테스트
bun test --watch                   # 감시 모드
bun test --coverage                # 커버리지 보고서 포함
bun test src/api/lightrag.test.ts  # 단일 테스트 파일
```

### 테스트

- 외부 서비스(Redis, httpx 등)에 대해서는 목(mock) 기반 테스트를 사용하세요 — 단위 테스트에서 실제 서비스에 의존하지 마세요.
- 모든 버그 수정에 대해 회귀 테스트를 추가하세요.
- 완료 선언 전에 전체 테스트 스위트(또는 관련 부분)를 실행하고 통과 수를 보고하세요.
- 백엔드 테스트는 pytest를 사용합니다. 프런트엔드 단위 테스트는 Bun의 내장 실행기를 사용합니다 — 위의 *WebUI*를 참조하세요.

```bash
# 새 셸과 자동화에 권장; PYTHON, venv, uv, .venv, venv, python, python3를 해석
./scripts/test.sh tests

# 특정 테스트 파일 실행
./scripts/test.sh tests/kg/test_graph_storage.py

# 커스텀 워커로 실행
./scripts/test.sh tests --test-workers 4
```

- `tests/`: 메인 테스트 스위트, 기능 폴더를 미러링합니다. 새 테스트는 테스트 대상 모듈과 일치하는 하위 디렉토리에 배치하세요:
  - `tests/api/{auth,config,routes}/` — FastAPI 서버 테스트; 최상위 `tests/api/`는 앱 전체 관심사
  - `tests/chunker/`, `tests/evaluation/`, `tests/extraction/`
  - `tests/kg/<backend>_impl/` — 백엔드별 스토리지 테스트; `_impl` 접미사 규칙 유지
  - `tests/llm/<provider>_impl/` — 프로바이더별 동작
  - `tests/parser/`, `tests/parser/docx/`, `tests/parser/external/{mineru,docling}/`
  - `tests/pipeline/` — 수집 파이프라인 및 doc-status 동작
  - `tests/sidecar/`, `tests/setup/`, `tests/workspace/`
- 마커 (`tests/pytest.ini` 참조): `offline`, `integration`, `requires_db`, `requires_api`. 통합 테스트는 기본적으로 `-m "not integration"`으로 건너뜁니다.

### 린팅
```bash
ruff check .
```

## 핵심 구현 패턴

### LightRAG 초기화 (중요)

가장 일반적인 오류는 스토리지 초기화를 잊는 것입니다 (`AttributeError: __aenter__` 또는 `KeyError: 'history_messages'`로 나타남):

```python
import asyncio
from lightrag import LightRAG
from lightrag.llm.openai import gpt_4o_mini_complete, openai_embed

async def main():
    rag = LightRAG(
        working_dir="./rag_storage",
        llm_model_func=gpt_4o_mini_complete,
        embedding_func=openai_embed
    )

    # 필수: 스토리지 백엔드 초기화
    await rag.initialize_storages()

    # 이제 안전하게 사용 가능
    await rag.ainsert("텍스트 내용")
    result = await rag.aquery("질문", param=QueryParam(mode="hybrid"))

    # 정리
    await rag.finalize_storages()

asyncio.run(main())
```

### 커스텀 임베딩 함수

`@wrap_embedding_func_with_attrs` 데코레이터를 사용하고 래핑 시 `.func`를 호출하세요 (이미 데코레이트된 함수는 다시 래핑할 수 없습니다 — `.func`를 통해 기본 함수에 접근하세요):

```python
from lightrag.utils import wrap_embedding_func_with_attrs

@wrap_embedding_func_with_attrs(embedding_dim=1536, max_token_size=8192)
async def custom_embed(texts: list[str]) -> np.ndarray:
    # 래핑된 버전이 아닌 기본 함수 호출
    return await openai_embed.func(texts, model="text-embedding-3-large")

# 잘못된 예: EmbeddingFunc(func=openai_embed)
# 올바른 예: EmbeddingFunc(func=openai_embed.func)
```

> **주의 — 임베딩 모델 변경**: 임베딩 모델을 변경할 때는 데이터 디렉토리를 지워야 합니다 (LLM 캐시를 위해 `kv_store_llm_response_cache.json`을 선택적으로 유지할 수 있습니다). 기존 벡터는 새 모델의 공간과 일치하지 않습니다.

### 스토리지 설정

환경 변수 또는 생성자 파라미터를 통해 설정하세요:

```python
# 환경 변수 기반 (프로덕션에 권장)
# 전체 목록은 env.example 참조

# 생성자 기반
rag = LightRAG(
    working_dir="./storage",
    workspace="project_name",  # 데이터 격리를 위해
    kv_storage="PGKVStorage",
    vector_storage="PGVectorStorage",
    graph_storage="Neo4JStorage",
    doc_status_storage="PGDocStatusStorage",
    vector_db_storage_cls_kwargs={
        "cosine_better_than_threshold": 0.2
    }
)
```

### 문서 삽입

```python
# 단일 문서
await rag.ainsert("텍스트 콘텐츠")

# 배치 삽입
await rag.ainsert(["텍스트 1", "텍스트 2", ...])

# 커스텀 ID 포함
await rag.ainsert("텍스트", ids=["doc-123"])

# 파일 경로 포함 (인용을 위해)
await rag.ainsert(["텍스트 1", "텍스트 2"], file_paths=["doc1.pdf", "doc2.pdf"])

# 배치 크기 설정
rag = LightRAG(..., max_parallel_insert=4)  # 기본값: 2, 최대 권장: 10
```

### 쿼리 설정

```python
from lightrag import QueryParam

result = await rag.aquery(
    "질문",
    param=QueryParam(
        mode="mix",                    # 리랭커와 함께 권장
        top_k=60,                      # 검색할 KG 엔티티/관계
        chunk_top_k=20,                # 검색할 텍스트 청크
        max_entity_tokens=6000,
        max_relation_tokens=8000,
        max_total_tokens=30000,
        enable_rerank=True,
        user_prompt="LLM에 대한 추가 지침",
        stream=False
    )
)
```

## Playwright를 통한 프런트엔드 디버깅

렌더링된 DOM에서만 나타나는 WebUI 버그 — 레이아웃/오버플로/스크롤바 문제, 일시적인 깜빡임, React 트리 외부의 `<body>`에 헬퍼를 붙이는 서드파티 라이브러리, 수정에 대한 엔드투엔드 검증 — 에 대해서는 소스만으로 추론하는 대신 `document-skills:webapp-testing` 스킬로 실행 중인 개발 서버(`http://localhost:5173`)를 조종하세요. 라이브 LLM 호출을 건너뛰기 위해 `localStorage`(영속 키 `settings-storage`, 스키마는 `lightrag_webui/src/stores/settings.ts`)를 통해 직접 상태를 시드하세요. Vite 개발의 장수 폴링으로 `networkidle`이 타임아웃되므로 `wait_until="domcontentloaded"` 더하기 선택자 대기를 사용하세요.

## 설정

### .env 설정
API 서버를 위한 기본 설정 파일입니다. `make env-base`로 생성하거나 `env.example`을 수동으로 복사하세요. 주요 섹션:
- 서버 설정 (HOST, PORT, CORS)
- 스토리지 백엔드 (환경 변수를 통한 연결 문자열)
- 쿼리 파라미터 (TOP_K, MAX_TOTAL_TOKENS 등)
- 리랭킹 설정 (RERANK_BINDING, RERANK_MODEL)
- 인증 (AUTH_ACCOUNTS, LIGHTRAG_API_KEY)

포괄적인 템플릿은 `env.example`을 참조하세요.

### 설정 마법사 출력물
- `.env`를 호스트에서 사용 가능하게 유지하세요. 컨테이너 전용 호스트명과 스테이징된 SSL 경로는 `.env`에 다시 저장되는 대신 마법사 관리 compose 계층에 속합니다.
- `docker-compose.final.yml`을 `scripts/setup/templates/*.yml`에서 조합된 생성 출력으로 취급하세요.
- 설정 워크플로우 변경 시 `scripts/setup/setup.sh`를 직접 호출하는 대신 `make env-*` 타겟을 선호하세요.

## 코드 스타일

### 언어
주석, 백엔드 코드, 로그 메시지는 영어로 작성합니다. 프런트엔드는 다국어 지원을 위해 i18next를 사용합니다.

### Python
- 4칸 들여쓰기를 사용한 PEP 8 준수
- 타입 어노테이션 사용
- 상태 관리를 위해 데이터클래스 선호
- print 대신 `lightrag.utils.logger` 사용
- 전체에 걸쳐 async/await 패턴

### TypeScript / React (WebUI ESLint 포함)
- 훅을 사용하는 함수형 컴포넌트; 컴포넌트에는 PascalCase
- 2칸 들여쓰기, 작은따옴표 (`@stylistic` 규칙으로 적용)
- Tailwind 유틸리티 우선 스타일링
- ESLint 스택: TypeScript-ESLint + React Hooks 플러그인 + Prettier; `@typescript-eslint/no-explicit-any`는 비활성화됨 (허용)

## 커밋 및 풀 리퀘스트 가이드

- 이 저장소가 `HKUDS/LightRAG`의 포크인 경우. PR 생성 시 포크 자체 저장소가 아닌 `HKUDS/LightRAG`를 대상으로 합니다.
- PR 설명에는 요약, 동기, 해당하는 경우 연결된 이슈, 변경 사항, 무엇이 중단되고 어떻게 작동하는지를 포함해야 합니다.
