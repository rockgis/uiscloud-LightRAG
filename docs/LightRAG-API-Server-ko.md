# LightRAG 서버 및 WebUI

LightRAG Server는 Web UI와 API 지원을 제공하도록 설계되었습니다. Web UI는 문서 인덱싱, 지식 그래프 탐색, 간단한 RAG 쿼리 인터페이스를 지원합니다. LightRAG Server는 또한 Ollama 호환 인터페이스를 제공하여 LightRAG를 Ollama 채팅 모델로 에뮬레이션합니다. 이를 통해 Open WebUI 같은 AI 채팅봇이 LightRAG에 쉽게 접근할 수 있습니다.

![image-20250323122538997](./LightRAG-API-Server.assets/image-20250323122538997.png)

![image-20250323122754387](./LightRAG-API-Server.assets/image-20250323122754387.png)

![image-20250323123011220](./LightRAG-API-Server.assets/image-20250323123011220.png)

## v1.4.16에서 v1.5.0rc2로 업그레이드

v1.5.0rc2 릴리스에는 새로운 파일 처리 파이프라인, 파서 라우팅, 멀티모달 분석, 역할별 LLM/VLM 설정, JSON 개체 추출, 여러 공급자/스토리지 변경이 추가되었습니다. 프로덕션 인스턴스를 업그레이드하기 전에 [v1.5.0rc2 릴리스 노트](https://github.com/HKUDS/LightRAG/releases/tag/v1.5.0rc2)를 검토하세요.

- 서버를 업그레이드하면서 이전 파일 처리 동작을 유지하려면 다음을 설정하세요:

```bash
LIGHTRAG_PARSER=*:legacy-F
```

- `ENTITY_TYPES`는 더 이상 지원되지 않습니다. 대신 `ENTITY_TYPE_PROMPT_FILE`을 사용하고, `PROMPT_DIR/entity_type` 아래에 저장된 YAML 프로필을 사용합니다 (`PROMPT_DIR` 기본값은 `./prompts`). 샘플 템플릿은 `prompts/samples/entity_type_prompt.sample.yml`에 있습니다.
- OpenSearch 스토리지를 사용하고 클러스터가 OpenSearch 3.3.0보다 오래된 경우, v1.5 스토리지 경로를 활성화하기 전에 OpenSearch를 업그레이드하고 기존 인덱스를 검증하세요. 새 배포에는 OpenSearch 3.3.0 이상을 사용하세요.
- 임베딩 모델, 임베딩 차원, 비대칭 임베딩 동작, 또는 쿼리/문서 접두사를 변경하면 벡터 의미론이 바뀝니다. 영향받는 LightRAG 워크스페이스/벡터 데이터를 지우고 소스 파일을 다시 인덱싱하세요.
- 파서 라우팅(`LIGHTRAG_PARSER`) 또는 파일명 힌트를 변경하면 새로 업로드되는 파일에만 영향을 줍니다. 기존 문서를 다른 파서 엔진으로 전환하려면 해당 문서를 삭제하고 다시 업로드하세요.
- 청커 설정(`CHUNK_*`)을 변경하면 서버 재시작 후 enqueue되는 문서에 영향을 줍니다. 이전 문서의 저장된 `chunk_options` 스냅샷을 새 설정에 맞추려면 해당 문서를 재처리하세요.
- 멀티모달 옵션(`i/t/e`)을 활성화하려면 파싱된 사이드카와 `VLM_PROCESS_ENABLE=true`가 필요합니다. 기존 문서를 재처리하여 사용 가능한 사이드카에서 VLM 분석을 실행할 수 있습니다. 추출 엔진을 전환하려면 여전히 삭제 후 재업로드가 필요합니다.

## 시작하기

### 설치

* PyPI에서 설치

```bash
### uv를 사용하여 LightRAG Server를 도구로 설치 (권장)
uv tool install "lightrag-hku[api]"

### 또는 pip 사용
# python -m venv .venv
# source .venv/bin/activate  # Windows: .venv\Scripts\activate
# pip install "lightrag-hku[api]"
```

* 소스에서 설치

```bash
# 저장소 클론
git clone https://github.com/HKUDS/lightrag.git

# 저장소 디렉터리로 이동
cd lightrag

# 개발 환경 부트스트랩 (권장)
make dev
source .venv/bin/activate  # 가상 환경 활성화 (Linux/macOS)
# 또는 Windows: .venv\Scripts\activate

# make dev는 테스트 툴체인과 전체 오프라인 스택
# (API, 스토리지 백엔드, 공급자 통합)을 설치하고 프론트엔드를 빌드합니다.
# 서버 시작 전에 make env-base를 실행하거나 env.example을 .env로 복사하세요.

# uv를 사용한 동등한 수동 단계
# Note: uv sync는 자동으로 .venv/에 가상 환경을 생성합니다
uv sync --extra test --extra offline
source .venv/bin/activate  # 가상 환경 활성화 (Linux/macOS)
# 또는 Windows: .venv\Scripts\activate

# 또는 pip와 가상 환경 사용
# python -m venv .venv
# source .venv/bin/activate  # Windows: .venv\Scripts\activate
# pip install -e ".[test,offline]"

# 프론트엔드 아티팩트 빌드
cd lightrag_webui
bun install --frozen-lockfile
bun run build
cd ..
```

### LightRAG Server 시작 전

LightRAG는 문서 인덱싱 및 쿼리 작업을 효과적으로 실행하기 위해 LLM (대규모 언어 모델)과 임베딩 모델 모두의 통합이 필요합니다. LightRAG 서버의 초기 배포 전에 LLM과 임베딩 모델 설정을 구성해야 합니다.

LightRAG가 지원하는 LLM 백엔드:

* ollama
* lollms
* openai 또는 openai 호환
* azure_openai
* bedrock
* gemini

LightRAG가 지원하는 임베딩 백엔드:

* lollms
* ollama
* openai 또는 openai 호환
* azure_openai
* bedrock
* jina
* gemini
* voyageai

LightRAG Server 설정에는 환경 변수를 사용하는 것이 권장됩니다. 프로젝트 루트 디렉터리에 `env.example`이라는 환경 변수 예시 파일이 있습니다. 이 파일을 시작 디렉터리에 복사하고 `.env`로 이름을 변경하세요. 그런 다음 `.env` 파일에서 LLM 및 임베딩 모델 관련 파라미터를 수정할 수 있습니다. LightRAG Server는 시작 시마다 `.env`의 환경 변수를 시스템 환경 변수로 로드합니다. **LightRAG Server는 `.env` 파일보다 시스템 환경 변수 설정을 우선합니다**.

> VS Code에서 Python 확장이 통합 터미널에서 `.env` 파일을 자동으로 로드할 수 있으므로, `.env` 파일을 수정할 때마다 새 터미널 세션을 여세요.

개체 추출, 키워드 추출, 최종 답변, 또는 멀티모달 분석에 서로 다른 LLM/VLM을 설정해야 하는 경우 [역할별 LLM/VLM 설정 가이드](./RoleSpecificLLMConfiguration.md)를 참고하세요.

LLM 및 임베딩 모델의 일반적인 설정 예시:

* OpenAI LLM + Ollama 임베딩:

```
LLM_BINDING=openai
LLM_MODEL=gpt-4o
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key

EMBEDDING_BINDING=ollama
EMBEDDING_BINDING_HOST=http://localhost:11434
EMBEDDING_MODEL=bge-m3:latest
EMBEDDING_DIM=1024
# EMBEDDING_BINDING_API_KEY=your_api_key
```

> Google Gemini를 사용할 경우 `LLM_BINDING=gemini`로 설정하고, `LLM_MODEL=gemini-flash-latest` 같은 모델을 선택하며, `LLM_BINDING_API_KEY`(또는 `GEMINI_API_KEY`)를 통해 Gemini 키를 제공하세요.

* Ollama LLM + Ollama 임베딩:

```
LLM_BINDING=ollama
LLM_MODEL=mistral-nemo:latest
LLM_BINDING_HOST=http://localhost:11434
# LLM_BINDING_API_KEY=your_api_key
###  Ollama Server 컨텍스트 길이 (MAX_TOTAL_TOKENS+2000보다 커야 함)
OLLAMA_LLM_NUM_CTX=16384

EMBEDDING_BINDING=ollama
EMBEDDING_BINDING_HOST=http://localhost:11434
EMBEDDING_MODEL=bge-m3:latest
EMBEDDING_DIM=1024
# EMBEDDING_BINDING_API_KEY=your_api_key
```

> **중요 참고사항**: 임베딩 모델과 비대칭 임베딩 설정은 문서 인덱싱 전에 결정되어야 하며, 쿼리 단계에서도 동일한 설정을 사용해야 합니다. 특정 스토리지 솔루션(예: PostgreSQL)의 경우 최초 테이블 생성 시 벡터 차원을 정의해야 합니다. 임베딩 모델, 임베딩 차원, `EMBEDDING_ASYMMETRIC`, 쿼리/문서 접두사, 또는 공급자 태스크 동작을 변경하면 기존 LightRAG 워크스페이스/벡터 데이터를 지우고 소스 파일을 다시 인덱싱해야 합니다.

#### 비대칭 임베딩 설정

LightRAG는 기본적으로 대칭 임베딩을 사용합니다. 쿼리/문서 비대칭 임베딩은 `EMBEDDING_ASYMMETRIC=true`가 명시적으로 설정된 경우에만 활성화됩니다.

- `jina`, `gemini`, `voyageai` 같은 공급자 태스크 바인딩은 공급자 파라미터(`task`/`task_type`/`input_type`)를 사용하며 쿼리/문서 접두사를 사용해서는 안 됩니다.
- `openai`, `azure_openai`, `ollama` 같은 접두사 기반 바인딩은 `EMBEDDING_QUERY_PREFIX`와 `EMBEDDING_DOCUMENT_PREFIX` 모두 필요합니다. 의도적으로 접두사가 없어야 하는 측에는 `NO_PREFIX`를 사용하세요.
- 비대칭 임베딩 설정의 유효한 변경은 기존 데이터를 지우고 파일을 다시 인덱싱해야 합니다.

전체 유효성 검사 규칙과 예시는 [비대칭 임베딩 설정](./AsymmetricEmbedding.md)을 참고하세요.

### 설정 도구로 .env 파일 생성

`env.example`을 수동으로 편집하는 대신 대화형 설정 마법사를 사용하여 설정된 `.env`와 필요한 경우 `docker-compose.final.yml`을 생성할 수 있습니다:

```bash
make env-base           # 필수 첫 번째 단계: LLM, 임베딩, 리랭커
make env-storage        # 선택 사항: 스토리지 백엔드 및 데이터베이스 서비스
make env-server         # 선택 사항: 서버 포트, 인증, SSL
make env-security-check # 선택 사항: 현재 .env의 보안 위험 감사
```

각 타겟에 대한 자세한 설명은 [docs/InteractiveSetup.md](./InteractiveSetup.md)를 참고하세요.

### LightRAG Server 시작

LightRAG Server는 두 가지 운영 모드를 지원합니다:
* 간단하고 효율적인 Uvicorn 모드:

```
lightrag-server
```
* 멀티프로세스 Gunicorn + Uvicorn 모드 (프로덕션 모드, Windows 환경에서는 지원 안 함):

```
lightrag-gunicorn --workers 4
```

LightRAG를 시작할 때 현재 작업 디렉터리에 `.env` 설정 파일이 있어야 합니다. **`.env` 파일은 시작 디렉터리에 위치해야 하도록 의도적으로 설계되었습니다**. 이를 통해 사용자는 여러 LightRAG 인스턴스를 동시에 실행하고 각 인스턴스에 서로 다른 `.env` 파일을 설정할 수 있습니다. **`.env` 파일을 수정한 후에는 새 설정이 적용되도록 터미널을 재실행해야 합니다.**

시작 시 `.env` 파일의 설정은 커맨드라인 파라미터로 재정의할 수 있습니다. 일반적인 커맨드라인 파라미터:

- `--host`: 서버 수신 주소 (기본값: 0.0.0.0)
- `--port`: 서버 수신 포트 (기본값: 9621)
- `--timeout`: LLM 요청 타임아웃 (기본값: 150초)
- `--log-level`: 로그 수준 (기본값: INFO)
- `--working-dir`: 데이터베이스 지속성 디렉터리 (기본값: ./rag_storage)
- `--input-dir`: 업로드 파일 디렉터리 (기본값: ./inputs)
- `--workspace`: 워크스페이스 이름, 여러 LightRAG 인스턴스 간 데이터 격리에 사용 (기본값: 빈 문자열)
- `--api-prefix`: 브라우저에 노출되는 역방향 프록시 경로 접두사, `LIGHTRAG_API_PREFIX`로도 설정 가능
- `--rerank-binding`: 리랭크 공급자 (`null`, `cohere`, `jina`, `aliyun`)

### 경로 접두사 및 다중 사이트 WebUI

한 호스트가 리버스 프록시 뒤에서 여러 LightRAG 인스턴스를 서비스하고, 프록시가 사이트 접두사를 제거한 후 백엔드로 전달할 때 `LIGHTRAG_API_PREFIX` 또는 `--api-prefix`를 설정하세요:

```bash
LIGHTRAG_API_PREFIX=/site01
lightrag-server --port 9621
```

백엔드가 이 값을 FastAPI에 `root_path`로 전달하고 동일한 런타임 접두사를 WebUI에 주입합니다. WebUI는 항상 서버 내부의 `/webui`에 마운트되므로 하나의 프론트엔드 빌드가 어떤 접두사도 서비스할 수 있습니다. 전체 Nginx, Docker, Kubernetes 예시는 [단일 서버 다중 사이트 배포](./MultiSiteDeployment.md)를 참고하세요.

### Docker로 LightRAG Server 시작

Docker Compose를 사용하는 것이 LightRAG Server를 배포하고 실행하는 가장 편리한 방법입니다.

- 프로젝트 디렉터리를 생성하세요.
- LightRAG 저장소의 `docker-compose.yml` 파일을 프로젝트 디렉터리에 복사하세요.
- `.env` 파일 준비: 샘플 파일 [`env.example`](https://ai.znipower.com:5013/c/env.example)을 복제하여 커스터마이징된 `.env` 파일을 생성하고, 특정 요구사항에 따라 LLM 및 임베딩 파라미터를 설정하세요.
- 다음 명령으로 LightRAG Server를 시작하세요:

```shell
docker compose up
# 시작 후 백그라운드로 실행하려면 명령어 끝에 -d 파라미터를 추가하세요.
```

공식 Docker Compose 파일: [docker-compose.yml](https://raw.githubusercontent.com/HKUDS/LightRAG/refs/heads/main/docker-compose.yml). LightRAG Docker 이미지 히스토리: [LightRAG Docker Images](https://github.com/HKUDS/LightRAG/pkgs/container/lightrag). 자세한 Docker 배포 내용은 [DockerDeployment.md](./DockerDeployment.md)를 참고하세요.

### 단계별 설정 레시피

LightRAG가 처음이라면 가장 작은 작동 설정부터 시작하고 이전 단계가 정상인 경우에만 기능을 추가하세요:

1. 호스팅 LLM 및 임베딩 모델을 사용한 최소 Docker 실행
2. 쿼리 품질 향상을 위해 리랭킹 추가
3. MinerU와 비전 지원 모델로 멀티모달 파싱 추가
4. GPU 지원 및 데이터베이스 스토리지를 갖춘 Docker 관리 배포로 전환

#### 1. 최소 Docker 실행

외부 데이터베이스, 파서 서비스, 로컬 모델 서비스 없이 WebUI와 API를 먼저 실행할 때 사용합니다. `docker-compose.yml` 옆에 최소한의 OpenAI 호환 설정으로 `.env`를 생성하세요:

```bash
###########################
### 서버 설정
###########################
PORT=9621
WEBUI_TITLE='My First LightRAG KB'
WEBUI_DESCRIPTION='Simple and Fast Graph Based RAG System'
OLLAMA_EMULATING_MODEL_TAG=latest

########################################
### 문서 처리 설정
########################################
SUMMARY_LANGUAGE=English
ENTITY_EXTRACTION_USE_JSON=true
LIGHTRAG_PARSER=*:native-teP,*:legacy-R
VLM_PROCESS_ENABLE=false

###########################################################################
### LLM 설정
###########################################################################
LLM_BINDING=openai
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key
LLM_MODEL=gpt-5-mini

KEYWORD_LLM_MODEL=gpt-5-nano
QUERY_LLM_MODEL=gpt-5

#######################################################################################
### 임베딩 설정 (첫 번째 파일이 처리된 후에는 변경하지 마세요)
#######################################################################################
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=https://api.openai.com/v1
EMBEDDING_BINDING_API_KEY=your_api_key
EMBEDDING_MODEL=text-embedding-3-large
EMBEDDING_DIM=3072
EMBEDDING_TOKEN_LIMIT=8192
EMBEDDING_SEND_DIM=false
EMBEDDING_USE_BASE64=true

############################
### 데이터 스토리지 선택
############################
LIGHTRAG_KV_STORAGE=JsonKVStorage
LIGHTRAG_DOC_STATUS_STORAGE=JsonDocStatusStorage
LIGHTRAG_GRAPH_STORAGE=NetworkXStorage
LIGHTRAG_VECTOR_STORAGE=NanoVectorDBStorage
```

서비스를 시작하고 문서를 업로드하기 전에 검증하세요:

```bash
docker compose up -d
curl http://localhost:9621/health
```

그런 다음 `http://localhost:9621/webui`에서 WebUI를 열고, 작은 텍스트 또는 DOCX 파일을 업로드하고, 인덱싱이 완료될 때까지 기다린 후 `hybrid` 또는 `mix` 쿼리를 실행하세요.

#### 2. 리랭킹 추가

리랭킹은 쿼리 시점의 개선 사항입니다. 리랭커를 활성화, 비활성화, 또는 변경해도 보통 기존 문서를 재인덱싱할 필요가 없습니다.

Cohere 공식 호스팅 리랭크 서비스:

```bash
RERANK_BINDING=cohere
RERANK_MODEL=rerank-v3.5
RERANK_BINDING_HOST=https://api.cohere.com/v2/rerank
RERANK_BINDING_API_KEY=your_cohere_api_key
```

Cohere 호환 API를 노출하는 로컬 vLLM 리랭커:

```bash
RERANK_BINDING=cohere
RERANK_MODEL=BAAI/bge-reranker-v2-m3
RERANK_BINDING_HOST=http://localhost:8000/rerank
RERANK_BINDING_API_KEY=your_rerank_api_key_here
```

LightRAG 자체가 Docker 내에서 실행되고 리랭커가 호스트에서 실행되는 경우 `localhost` 대신 `host.docker.internal` 같은 호스트에서 접근 가능한 주소를 사용하세요.

#### 3. MinerU 공식 API를 사용한 멀티모달 파싱 추가

기본 문서 처리 흐름이 작동한 후 사용하세요. MinerU 공식 API는 로컬 파서 서비스 실행을 피할 수 있지만, LightRAG 서버 시작 전에 `MINERU_API_TOKEN`이 설정되어야 합니다. VLM 역할은 이미지 입력을 지원하는 공급자/모델을 사용해야 합니다.

```bash
LIGHTRAG_PARSER=*:native-iteP,*:mineru-iteP,*:legacy-R

VLM_PROCESS_ENABLE=true
VLM_LLM_MODEL=gpt-5-mini

MINERU_API_MODE=official
MINERU_API_TOKEN=your_mineru_api_token
MINERU_OFFICIAL_ENDPOINT=https://mineru.net
MINERU_MODEL_VERSION=vlm
MINERU_IS_OCR=false
```

#### 4. GPU 올인원 스타일 배포

로컬 GPU 지원 배포의 경우 마법사가 `.env`와 `docker-compose.final.yml`을 생성하도록 하세요:

```bash
make env-base
```

권장 답변:
- 기본 LLM을 호스팅 또는 OpenAI 호환 공급자로 설정합니다.
- `Run embedding model locally via Docker (vLLM)?`에 `yes`로 답합니다.
- 임베딩 장치로 `cuda`를 선택합니다.
- 리랭킹을 활성화하고 `Run rerank service locally via Docker?`에 `yes`로 답하며, 리랭크 장치로 `cuda`를 선택합니다.

그런 다음 스토리지를 설정합니다:

```bash
make env-storage
```

권장 스토리지 선택:
- `LIGHTRAG_KV_STORAGE=PGKVStorage`
- `LIGHTRAG_DOC_STATUS_STORAGE=PGDocStatusStorage`
- `LIGHTRAG_VECTOR_STORAGE=MilvusVectorDBStorage`
- `LIGHTRAG_GRAPH_STORAGE=MemgraphStorage`

마지막으로 서버 설정을 구성하고 결과를 검증합니다:

```bash
make env-server
make env-validate
make env-security-check
docker compose -f docker-compose.final.yml up -d
```

### Nginx 역방향 프록시 설정

LightRAG Server 앞에 Nginx를 역방향 프록시로 사용할 때 대용량 파일 업로드 처리를 위해 `/documents/upload` 엔드포인트에 `client_max_body_size`를 설정해야 합니다.

**권장 설정:**

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 전역 기본값: LLM 쿼리에 8MB
    client_max_body_size 8M;

    # 업로드 엔드포인트: 대형 파일 업로드에 100MB
    location /documents/upload {
        client_max_body_size 100M;

        proxy_pass http://localhost:9621;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 대형 파일 업로드를 위한 타임아웃 증가
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }

    # 스트리밍 엔드포인트: LLM 응답 스트리밍
    location ~ ^/(query/stream|api/chat|api/generate) {
        gzip off;  # 스트리밍 응답에서 압축 비활성화

        proxy_pass http://localhost:9621;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # LLM 생성을 위한 긴 타임아웃
        proxy_read_timeout 300s;
    }

    # 기타 엔드포인트
    location / {
        proxy_pass http://localhost:9621;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**핵심 사항:**

1. **전역 한도 (8MB)**: 긴 대화 기록과 컨텍스트가 있는 LLM 쿼리에 충분합니다 (128K 토큰 ≈ 512KB + JSON 오버헤드).
2. **업로드 엔드포인트 (100MB)**: `.env` 파일의 `MAX_UPLOAD_SIZE`와 같거나 커야 합니다. 기본 `MAX_UPLOAD_SIZE`는 100MB입니다.
3. **스트리밍 엔드포인트**: 실시간 응답 전달을 보장하기 위해 스트리밍 엔드포인트에서 gzip 압축을 비활성화하세요 (`gzip off`). LightRAG는 응답 버퍼링을 비활성화하기 위해 자동으로 `X-Accel-Buffering: no` 헤더를 설정합니다.
4. **타임아웃 설정**: 대형 파일 업로드와 LLM 생성에는 더 긴 타임아웃이 필요합니다.
5. **크기 검증 계층**: Nginx가 먼저 `Content-Length` 헤더를 검증하고, LightRAG가 업로드 중 스트리밍 검증을 수행합니다.

### 오프라인 배포

공식 LightRAG Docker 이미지는 오프라인 또는 에어갭 환경과 완전히 호환됩니다. 자세한 내용은 [오프라인 배포 가이드](./OfflineDeployment.md)를 참고하세요.

### 여러 LightRAG 인스턴스 시작

여러 LightRAG 인스턴스를 시작하는 두 가지 방법이 있습니다. 첫 번째는 각 인스턴스에 완전히 독립적인 작업 환경을 설정하는 것입니다. 두 번째는 모든 인스턴스가 동일한 `.env` 파일을 공유하고 커맨드라인 인수로 서로 다른 포트와 워크스페이스를 지정하는 것입니다:

```
# 인스턴스 1 시작
lightrag-server --port 9621 --workspace space1

# 인스턴스 2 시작
lightrag-server --port 9622 --workspace space2
```

워크스페이스의 목적은 서로 다른 인스턴스 간의 데이터 격리를 달성하는 것입니다. 따라서 서로 다른 인스턴스의 `workspace` 파라미터는 달라야 합니다.

### LightRAG 인스턴스 간 데이터 격리

워크스페이스 구현 방식은 스토리지 유형별로 다릅니다:

- **파일 기반 데이터베이스의 경우 워크스페이스 하위 디렉터리를 통한 격리:** `JsonKVStorage`, `JsonDocStatusStorage`, `NetworkXStorage`, `NanoVectorDBStorage`, `FaissVectorDBStorage`.
- **컬렉션에 데이터를 저장하는 데이터베이스의 경우 컬렉션 이름에 워크스페이스 접두사 추가:** `RedisKVStorage`, `RedisDocStatusStorage`, `MilvusVectorDBStorage`, `MongoKVStorage`, `MongoDocStatusStorage`, `MongoVectorDBStorage`, `MongoGraphStorage`, `PGGraphStorage`.
- **Qdrant 벡터 데이터베이스의 경우 페이로드 기반 파티셔닝 (Qdrant 권장 멀티테넌시 방식):** `QdrantVectorDBStorage`는 공유 컬렉션과 페이로드 필터링을 사용합니다.
- **관계형 데이터베이스의 경우 테이블에 `workspace` 필드를 추가하여 논리적 데이터 분리:** `PGKVStorage`, `PGVectorStorage`, `PGDocStatusStorage`.
- **그래프 데이터베이스의 경우 레이블을 통한 논리적 데이터 격리:** `Neo4JStorage`, `MemgraphStorage`
- **OpenSearch의 경우 인덱스 이름 접두사를 통한 격리:** `OpenSearchKVStorage`, `OpenSearchDocStatusStorage`, `OpenSearchGraphStorage`, `OpenSearchVectorDBStorage`

레거시 데이터와의 호환성을 위해 워크스페이스가 설정되지 않은 경우 PostgreSQL의 기본 워크스페이스는 `default`, Neo4j는 `base`입니다. 스토리지별 워크스페이스 환경 변수: `REDIS_WORKSPACE`, `MILVUS_WORKSPACE`, `QDRANT_WORKSPACE`, `MONGODB_WORKSPACE`, `POSTGRES_WORKSPACE`, `NEO4J_WORKSPACE`, `MEMGRAPH_WORKSPACE`, `OPENSEARCH_WORKSPACE`.

### Gunicorn + Uvicorn 다중 워커

LightRAG Server는 `Gunicorn + Uvicorn` 프리로드 모드로 운영될 수 있습니다. Gunicorn의 다중 워커(멀티프로세스) 기능은 문서 인덱싱 작업이 RAG 쿼리를 차단하는 것을 방지합니다.

동시 처리와 관련된 환경 변수:

```
### 워커 프로세스 수, (2 x 코어 수) + 1 이하
WORKERS=2
### 한 배치에서 병렬로 처리할 파일 수
MAX_PARALLEL_INSERT=2
### LLM에 대한 최대 동시 요청 수
MAX_ASYNC=4
```

macOS에서 Gunicorn 다중 워커 모드는 Python 프로세스 시작 전에 Objective-C fork-safety 재정의가 있어야 합니다. `.env`에 의존하지 마세요; `.env`는 Python 시작 후 로드되어 Objective-C 런타임에는 너무 늦습니다:

```shell
export OBJC_DISABLE_INITIALIZE_FORK_SAFETY=YES
lightrag-gunicorn --workers 2
```

### LightRAG를 Linux 서비스로 설치

샘플 파일 `lightrag.service.example`에서 서비스 파일 `lightrag.service`를 생성하세요. 서비스 파일에서 시작 옵션을 수정합니다:

```text
# Python 가상 환경으로 환경 설정
Environment="PATH=/home/netman/lightrag-xyj/venv/bin"
WorkingDirectory=/home/netman/lightrag-xyj
# ExecStart=/home/netman/lightrag-xyj/venv/bin/lightrag-server
ExecStart=/home/netman/lightrag-xyj/venv/bin/lightrag-gunicorn
```

> ExecStart 명령은 반드시 `lightrag-gunicorn` 또는 `lightrag-server`여야 합니다. 래퍼 스크립트는 허용되지 않습니다. 서비스 종료는 메인 프로세스가 이 두 실행 파일 중 하나여야 하기 때문입니다.

Ubuntu 시스템에서의 LightRAG 서비스 설치:

```shell
sudo cp lightrag.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl start lightrag.service
sudo systemctl status lightrag.service
sudo systemctl enable lightrag.service
```

## Ollama 에뮬레이션

LightRAG를 Ollama 채팅 모델로 에뮬레이션하는 Ollama 호환 인터페이스를 제공합니다. 이를 통해 Open WebUI 같은 Ollama를 지원하는 AI 채팅 프론트엔드가 LightRAG에 쉽게 접근할 수 있습니다.

### Open WebUI를 LightRAG에 연결

lightrag-server를 시작한 후 Open WebUI 관리 패널에서 Ollama 유형 연결을 추가할 수 있습니다. 그러면 Open WebUI의 모델 관리 인터페이스에 `lightrag:latest`라는 모델이 나타납니다. 사용자는 채팅 인터페이스를 통해 LightRAG에 쿼리를 보낼 수 있습니다.

Open WebUI는 LLM을 사용하여 세션 제목과 세션 키워드 생성 작업을 수행합니다. 따라서 Ollama 채팅 완성 API는 OpenWebUI 세션 관련 요청을 감지하고 기본 LLM으로 직접 전달합니다.

### 채팅에서 쿼리 모드 선택

Ollama 인터페이스에서 메시지(쿼리)를 보내면 기본 쿼리 모드는 `hybrid`입니다. 쿼리 접두사를 포함한 메시지를 전송하여 쿼리 모드를 선택할 수 있습니다.

지원되는 접두사:

```
/local
/global
/hybrid
/naive
/mix

/bypass
/context
/localcontext
/globalcontext
/hybridcontext
/naivecontext
/mixcontext
```

예를 들어, `/mix What's LightRAG?`는 LightRAG에 대한 mix 모드 쿼리를 트리거합니다. 쿼리 접두사 없는 채팅 메시지는 기본적으로 hybrid 모드 쿼리를 트리거합니다.

`/bypass`는 LightRAG 쿼리 모드가 아닙니다. 채팅 기록을 포함하여 쿼리를 기본 LLM에 직접 전달하도록 API 서버에 지시합니다. `/context`도 LightRAG 쿼리 모드가 아닙니다. LightRAG가 LLM을 위해 준비된 컨텍스트 정보만 반환하도록 지시합니다.

### 채팅에서 사용자 프롬프트 추가

LightRAG를 콘텐츠 쿼리에 사용할 때는 검색 프로세스와 관련 없는 출력 처리를 결합하지 마세요. 사용자 프롬프트는 RAG 검색 단계에는 참여하지 않지만 쿼리 완료 후 LLM이 검색된 결과를 처리하는 방법을 안내합니다. 쿼리 접두사에 대괄호를 추가하여 LLM에 사용자 프롬프트를 제공할 수 있습니다:

```
/[Use mermaid format for diagrams] Please draw a character relationship diagram for Scrooge
/mix[Use mermaid format for diagrams] Please draw a character relationship diagram for Scrooge
```

## API 키 및 인증

기본적으로 LightRAG Server는 인증 없이 접근할 수 있습니다. API 키 또는 계정 자격증명으로 서버를 보안할 수 있습니다.

* API 키:

```
LIGHTRAG_API_KEY=your-secure-api-key-here
WHITELIST_PATHS=/health,/api/*
```

> 기본적으로 상태 확인 및 Ollama 에뮬레이션 엔드포인트는 API 키 확인에서 제외됩니다. 보안상 Ollama 서비스가 필요하지 않다면 `WHITELIST_PATHS`에서 `/api/*`를 제거하세요.

API 키는 요청 헤더 `X-API-Key`를 사용하여 전달됩니다:

```
curl -X 'POST' \
  'http://localhost:9621/documents/scan' \
  -H 'accept: application/json' \
  -H 'X-API-Key: your-secure-api-key-here-123' \
  -d ''
```

* 계정 자격증명 (Web UI는 접근 권한 부여 전 로그인 필요):

LightRAG API Server는 HS256 알고리즘을 사용한 JWT 기반 인증을 구현합니다:

```bash
# JWT 인증용
AUTH_ACCOUNTS='admin:{bcrypt}$2b$12$replace-with-generated-hash,user1:pass456'
TOKEN_SECRET='your-key'
TOKEN_EXPIRE_HOURS=4
```

접두사가 없는 비밀번호는 일반 텍스트로 처리됩니다. bcrypt 비밀번호를 저장하려면 생성된 해시 앞에 `{bcrypt}`를 붙이세요:

```bash
lightrag-hash-password --username admin
```

> 현재는 관리자 계정과 비밀번호 설정만 지원됩니다. 포괄적인 계정 시스템은 아직 개발 중입니다.

## Azure OpenAI 백엔드 설정

Azure CLI를 사용하여 Azure OpenAI API를 생성할 수 있습니다:

```bash
RESOURCE_GROUP_NAME=LightRAG
LOCATION=swedencentral
RESOURCE_NAME=LightRAG-OpenAI

az login
az group create --name $RESOURCE_GROUP_NAME --location $LOCATION
az cognitiveservices account create --name $RESOURCE_NAME --resource-group $RESOURCE_GROUP_NAME  --kind OpenAI --sku S0 --location swedencentral
az cognitiveservices account deployment create --resource-group $RESOURCE_GROUP_NAME  --model-format OpenAI --name $RESOURCE_NAME --deployment-name gpt-4o --model-name gpt-4o --model-version "2024-08-06"  --sku-capacity 100 --sku-name "Standard"
az cognitiveservices account deployment create --resource-group $RESOURCE_GROUP_NAME  --model-format OpenAI --name $RESOURCE_NAME --deployment-name text-embedding-3-large --model-name text-embedding-3-large --model-version "1"  --sku-capacity 80 --sku-name "Standard"
az cognitiveservices account show --name $RESOURCE_NAME --resource-group $RESOURCE_GROUP_NAME --query "properties.endpoint"
az cognitiveservices account keys list --name $RESOURCE_NAME -g $RESOURCE_GROUP_NAME
```

```
# .env의 Azure OpenAI 설정:
LLM_BINDING=azure_openai
LLM_BINDING_HOST=your-azure-endpoint
LLM_MODEL=your-model-deployment-name
LLM_BINDING_API_KEY=your-azure-api-key
### API 버전은 선택 사항, 기본값은 최신 버전
AZURE_OPENAI_API_VERSION=2024-08-01-preview

### Azure OpenAI를 임베딩에도 사용하는 경우
EMBEDDING_BINDING=azure_openai
EMBEDDING_MODEL=your-embedding-deployment-name
```

## LightRAG Server 상세 설정

API Server는 두 가지 방법으로 설정할 수 있습니다 (높은 우선순위 순):

* 커맨드라인 인수
* 환경 변수 또는 .env 파일

### 지원하는 LLM 및 임베딩 백엔드

LightRAG 지원 LLM 바인딩: `ollama`, `openai`(호환 포함), `azure_openai`, `lollms`, `bedrock`, `gemini`

LightRAG 지원 임베딩 바인딩: `lollms`, `ollama`, `openai`(호환 포함), `azure_openai`, `bedrock`, `jina`, `gemini`, `voyageai`

`LLM_BINDING` 환경 변수 또는 `--llm-binding` CLI 인수로 LLM 백엔드 유형을 선택하고, `EMBEDDING_BINDING` 환경 변수 또는 `--embedding-binding` CLI 인수로 임베딩 백엔드 유형을 선택합니다.

Bedrock은 `LLM_BINDING_API_KEY`와 `EMBEDDING_BINDING_API_KEY`를 무시합니다. AWS 자격증명 체인을 통해 SigV4 자격증명을 사용하거나, Bedrock API 키/베어러 토큰 인증을 위해 시작 전에 프로세스 수준의 `AWS_BEARER_TOKEN_BEDROCK` 환경 변수를 설정하세요:

```bash
LLM_BINDING=bedrock
LLM_BINDING_HOST=DEFAULT_BEDROCK_ENDPOINT
LLM_MODEL=us.amazon.nova-lite-v1:0
AWS_REGION=us-west-2
```

개체 관계 추출 단계 중 과도하게 길거나 무한한 출력 루프를 방지하기 위해 max_tokens를 설정하세요:

```
# vLLM/SGLang 배포 모델 또는 대부분의 OpenAI 호환 API 공급자
OPENAI_LLM_MAX_TOKENS=9000

# Ollama 배포 모델
OLLAMA_LLM_NUM_PREDICT=9000

# OpenAI o1-mini 또는 최신 모델
OPENAI_LLM_MAX_COMPLETION_TOKENS=9000
```

### 역할별 LLM/VLM 설정

서버는 클라이언트 API를 변경하지 않고 서로 다른 단계에 서로 다른 모델을 사용할 수 있습니다. 4가지 역할이 지원됩니다:

| 역할 | 목적 |
| --- | --- |
| `EXTRACT` | 개체/관계 추출 및 병합 요약 |
| `KEYWORD` | 검색 전 쿼리 키워드 생성 |
| `QUERY` | 최종 답변, bypass 쿼리, Ollama 호환 채팅 응답 |
| `VLM` | 이미지, 표, 수식 등 사이드카 항목의 멀티모달 분석 |

역할이 설정되지 않으면 기본 `LLM_*` 설정을 상속합니다. 최소 동일 공급자 예시:

```bash
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key

EXTRACT_LLM_MODEL=gpt-5-mini
KEYWORD_LLM_MODEL=gpt-5-nano
QUERY_LLM_MODEL=gpt-5
VLM_LLM_MODEL=gpt-5-mini
```

### 멀티모달 분석 설정

파서는 드로잉/이미지, 표, 수식에 대한 사이드카를 생성할 수 있습니다. VLM 분석은 두 조건이 모두 충족될 때만 실행됩니다:

- 문서의 `process_options`에 일치하는 모달리티 플래그가 포함되어 있음: 이미지는 `i`, 표는 `t`, 수식은 `e`.
- `VLM_PROCESS_ENABLE=true`이고 유효한 VLM 바인딩이 이미지 입력을 지원함.

현재 비전 지원 공급자: `openai`, `azure_openai`, `gemini`, `bedrock`, `ollama`, `anthropic`. `lollms`는 VLM 사용에서 거부됩니다. 일반적인 설정:

```bash
VLM_PROCESS_ENABLE=true
VLM_LLM_BINDING=openai
VLM_LLM_MODEL=gpt-4o
VLM_LLM_BINDING_HOST=https://api.openai.com/v1
VLM_LLM_BINDING_API_KEY=your_vlm_api_key
VLM_MAX_IMAGE_BYTES=5242880
SURROUNDING_LEADING_MAX_TOKENS=2000
SURROUNDING_TRAILING_MAX_TOKENS=2000
```

### 개체 추출 설정

중요한 서버 측 옵션:

- `ENABLE_LLM_CACHE_FOR_EXTRACT`: 개체 추출에 LLM 캐시 활성화 (기본값: `true`). 테스트 환경과 재처리 중에 유용합니다.
- `ENTITY_EXTRACTION_USE_JSON`: JSON 구조화 추출 출력 요청. v1.5에서는 안정성을 위해 권장하지만 지연 시간이 증가할 수 있습니다.
- `ENTITY_TYPE_PROMPT_FILE`: 개체 유형 안내 및 예시를 위한 YAML 프로필의 파일 이름(경로 아님). `PROMPT_DIR/entity_type`에서 파일이 로드됩니다.
- `MAX_EXTRACT_INPUT_TOKENS`: 하나의 추출 입력 컨텍스트의 최대 토큰 예산.
- `MAX_EXTRACTION_RECORDS`: 응답당 총 개체 및 관계 레코드 상한.
- `MAX_EXTRACTION_ENTITIES`: 응답당 개체 레코드 상한.

예시:

```bash
ENTITY_EXTRACTION_USE_JSON=true
ENTITY_TYPE_PROMPT_FILE=entity_type_prompt.yml
PROMPT_DIR=/opt/lightrag/prompts
MAX_EXTRACT_INPUT_TOKENS=20480
MAX_EXTRACTION_RECORDS=100
MAX_EXTRACTION_ENTITIES=40
ENABLE_LLM_CACHE_FOR_EXTRACT=true
```

### 지원하는 스토리지 유형

LightRAG는 4가지 유형의 스토리지를 사용합니다:

* KV_STORAGE: LLM 응답 캐시, 텍스트 청크, 문서 정보
* VECTOR_STORAGE: 개체 벡터, 관계 벡터, 청크 벡터
* GRAPH_STORAGE: 개체 관계 그래프
* DOC_STATUS_STORAGE: 문서 인덱싱 상태

환경 변수로 스토리지 구현을 선택합니다:

```
LIGHTRAG_KV_STORAGE=PGKVStorage
LIGHTRAG_VECTOR_STORAGE=PGVectorStorage
LIGHTRAG_GRAPH_STORAGE=PGGraphStorage
LIGHTRAG_DOC_STATUS_STORAGE=PGDocStatusStorage
```

문서를 LightRAG에 추가한 후에는 스토리지 구현 선택을 변경할 수 없습니다. 한 스토리지 구현에서 다른 구현으로의 데이터 마이그레이션은 아직 지원되지 않습니다.

### LightRAG API Server 커맨드라인 옵션

| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| `--host` | `0.0.0.0` | 서버 호스트 |
| `--port` | `9621` | 서버 포트 |
| `--working-dir` | `./rag_storage` | RAG 스토리지 작업 디렉터리 |
| `--input-dir` | `./inputs` | 업로드/입력 문서 디렉터리 |
| `--timeout` | `150` | Gunicorn 워커 타임아웃 및 폴백 요청 타임아웃 |
| `--max-async` | `4` | 최대 동시 LLM 작업 수 |
| `--log-level` | `INFO` | 로그 수준 (`DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`) |
| `--verbose` | `False` | 상세 디버그 출력, 디버그 로깅과 함께 유효 |
| `--key` | `None` | 인증용 API 키 |
| `--ssl` | `False` | HTTPS 활성화 |
| `--ssl-certfile` | `None` | SSL 인증서 파일 경로, `--ssl` 활성화 시 필수 |
| `--ssl-keyfile` | `None` | SSL 개인 키 파일 경로, `--ssl` 활성화 시 필수 |
| `--workspace` | `""` | 스토리지 격리를 위한 기본 워크스페이스 |
| `--api-prefix` | `""` | 역방향 프록시 경로 접두사, `LIGHTRAG_API_PREFIX`로도 설정 가능 |
| `--workers` | `1` | Gunicorn 워커 수 |
| `--llm-binding` | `ollama` | LLM 바인딩 유형 |
| `--embedding-binding` | `ollama` | 임베딩 바인딩 유형 |
| `--rerank-binding` | `null` | 리랭크 바인딩 유형 (`null`, `cohere`, `jina`, `aliyun`) |

### 리랭킹 설정

리랭킹은 더 효과적인 관련성 점수 모델을 기반으로 문서를 재정렬하여 검색 품질을 크게 향상시킬 수 있습니다. LightRAG가 현재 지원하는 리랭크 공급자:

- **Cohere / vLLM**: Cohere AI의 `v2/rerank` 엔드포인트와 완전한 API 통합. vLLM이 Cohere 호환 리랭커 API를 제공하므로 vLLM으로 배포된 모든 리랭커 모델도 지원됩니다.
- **Jina AI**: 모든 Jina 리랭크 모델과 완전한 구현 호환성.
- **Aliyun**: Aliyun의 리랭크 API 형식을 지원하는 커스텀 구현.

vLLM을 사용하여 로컬로 배포된 리랭크 모델 설정 예시:

```
RERANK_BINDING=cohere
RERANK_MODEL=BAAI/bge-reranker-v2-m3
RERANK_BINDING_HOST=http://localhost:8000/rerank
RERANK_BINDING_API_KEY=your_rerank_api_key_here
```

Aliyun 리랭커 서비스 설정 예시:

```
RERANK_BINDING=aliyun
RERANK_MODEL=gte-rerank-v2
RERANK_BINDING_HOST=https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
RERANK_BINDING_API_KEY=your_rerank_api_key_here
```

리랭커 호출에는 자체 동시성 및 타임아웃 제어가 있습니다:

```bash
MAX_ASYNC_RERANK=4
RERANK_TIMEOUT=30
```

### 리랭킹 활성화

리랭킹은 쿼리별로 활성화하거나 비활성화할 수 있습니다.

`/query` 및 `/query/stream` API 엔드포인트에는 `enable_rerank` 파라미터가 있으며 기본값은 `true`입니다. `enable_rerank` 파라미터의 기본값을 `false`로 변경하려면:

```
RERANK_BY_DEFAULT=False
```

### 참조에 청크 콘텐츠 포함

기본적으로 `/query` 및 `/query/stream` 엔드포인트는 `reference_id`와 `file_path`만 포함된 참조를 반환합니다. 평가, 디버깅, 인용 목적으로 실제 검색된 청크 콘텐츠를 참조에 포함하도록 요청할 수 있습니다.

`include_chunk_content` 파라미터 (기본값: `false`)는 검색된 청크의 실제 텍스트 콘텐츠를 응답 참조에 포함할지 제어합니다. 다음에 특히 유용합니다:

- **RAG 평가**: RAGAS 같은 검색 컨텍스트에 접근해야 하는 테스팅 시스템
- **디버깅**: 답변 생성에 실제로 사용된 콘텐츠 확인
- **인용 표시**: 응답을 지지하는 정확한 텍스트 구절을 사용자에게 표시
- **투명성**: RAG 검색 프로세스에 대한 완전한 가시성 제공

**중요**: `content` 필드는 **문자열 배열**로, 각 문자열은 동일한 파일의 청크를 나타냅니다. 단일 파일이 여러 청크에 해당할 수 있으므로 콘텐츠는 청크 경계를 보존하기 위해 목록으로 반환됩니다.

**API 요청 예시:**

```json
{
  "query": "What is LightRAG?",
  "mode": "mix",
  "include_references": true,
  "include_chunk_content": true
}
```

**응답 예시 (청크 콘텐츠 포함):**

```json
{
  "response": "LightRAG is a graph-based RAG system...",
  "references": [
    {
      "reference_id": "1",
      "file_path": "/documents/intro.md",
      "content": [
        "LightRAG is a retrieval-augmented generation system that combines knowledge graphs with vector similarity search...",
        "The system uses a dual-indexing approach with both vector embeddings and graph structures for enhanced retrieval..."
      ]
    },
    {
      "reference_id": "2",
      "file_path": "/documents/features.md",
      "content": [
        "The system provides multiple query modes including local, global, hybrid, and mix modes..."
      ]
    }
  ]
}
```

**참고사항**:
- 이 파라미터는 `include_references=true`일 때만 작동합니다.
- **하위 호환성 변경**: 이전 버전은 `content`를 하나의 연결된 문자열로 반환했습니다. 이제 개별 청크 경계를 보존하기 위해 문자열 배열을 반환합니다. 단일 문자열이 필요하면 배열 요소를 원하는 구분자로 결합하세요 (예: `"\n\n".join(content)`).

### .env 예시

```bash
### 서버 설정
# HOST=0.0.0.0
PORT=9621
WORKERS=2
# LIGHTRAG_API_PREFIX=/site01

### 문서 인덱싱 설정
ENABLE_LLM_CACHE_FOR_EXTRACT=true
ENTITY_EXTRACTION_USE_JSON=true
# ENTITY_TYPE_PROMPT_FILE=entity_type_prompt.yml
# MAX_EXTRACT_INPUT_TOKENS=20480
# MAX_EXTRACTION_RECORDS=100
# MAX_EXTRACTION_ENTITIES=40
SUMMARY_LANGUAGE=Chinese
MAX_PARALLEL_INSERT=2
LIGHTRAG_PARSER=*:native-teP,*:legacy-R
# CHUNK_R_SEPARATORS=["\n\n","\n","。","！","？","；","，"," ",""]
# CHUNK_P_SIZE=2000

### LLM 설정
TIMEOUT=150
MAX_ASYNC=4

LLM_BINDING=openai
LLM_MODEL=gpt-4o-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your-api-key
KEYWORD_LLM_MODEL=gpt-4o-mini
QUERY_LLM_MODEL=gpt-4o

### i/t/e 처리 옵션을 사용하는 문서를 위한 선택적 VLM 설정
VLM_PROCESS_ENABLE=false
# VLM_LLM_MODEL=gpt-4o
# VLM_MAX_IMAGE_BYTES=5242880
# SURROUNDING_LEADING_MAX_TOKENS=2000
# SURROUNDING_TRAILING_MAX_TOKENS=2000

### 선택적 리랭커 설정
RERANK_BINDING=null
# MAX_ASYNC_RERANK=4
# RERANK_TIMEOUT=30

### 임베딩 설정
EMBEDDING_MODEL=bge-m3:latest
EMBEDDING_DIM=1024
EMBEDDING_BINDING=ollama
EMBEDDING_BINDING_HOST=http://localhost:11434
# 선택적 비대칭 임베딩 (접두사 기반 모델):
# EMBEDDING_ASYMMETRIC=true
# EMBEDDING_QUERY_PREFIX="search_query: "
# EMBEDDING_DOCUMENT_PREFIX="search_document: "
# 의도적으로 접두사가 없어야 하는 측에는 NO_PREFIX 사용

### JWT 인증용
# AUTH_ACCOUNTS='admin:{bcrypt}$2b$12$replace-with-generated-hash,user1:pass456'
# TOKEN_SECRET=your-key-for-LightRAG-API-Server-xxx
# TOKEN_EXPIRE_HOURS=48

# LIGHTRAG_API_KEY=your-secure-api-key-here-123
# WHITELIST_PATHS=/api/*
# WHITELIST_PATHS=/health,/api/*
```

## 문서 및 청크 처리

v1.5에서는 단계별 문서 파이프라인을 도입합니다. 파일은 먼저 콘텐츠 추출 엔진을 거치고, 선택적 멀티모달 분석, 텍스트 청킹, 그런 다음 파일이 지식 그래프 구축을 비활성화하지 않는 한 개체/관계 추출 단계를 거칩니다.

### 빠른 레시피

v1.4 호환 동작 유지:

```bash
LIGHTRAG_PARSER=*:legacy-F
```

외부 파서 서비스 없이 권장되는 시작점:

```bash
LIGHTRAG_PARSER=*:native-teP,*:legacy-R
```

MinerU 공식 API와 VLM을 사용한 전체 멀티모달 설정:

```bash
LIGHTRAG_PARSER=*:native-iteP,*:mineru-iteP,*:legacy-R
VLM_PROCESS_ENABLE=true
VLM_LLM_MODEL=gpt-4o
MINERU_API_MODE=official
MINERU_API_TOKEN=your_mineru_api_token
MINERU_OFFICIAL_ENDPOINT=https://mineru.net
MINERU_MODEL_VERSION=vlm
MINERU_IS_OCR=false
```

`docling`으로 파일을 라우팅할 때 `DOCLING_ENDPOINT=http://localhost:5001`을 사용하세요.

### 파서 엔진 및 라우팅

`LIGHTRAG_PARSER`는 파일 확장자별 기본 추출 규칙을 정의합니다. 규칙은 왼쪽에서 오른쪽으로 매칭되며 쉼표 또는 세미콜론으로 구분할 수 있습니다:

```bash
LIGHTRAG_PARSER=pdf:mineru-R,docx:native-ietP,*:legacy-R
```

지원 엔진:

| 엔진 | 용도 |
| --- | --- |
| `legacy` | 원본 추출 동작. 호환성과 간단한 텍스트형 파일에 적합. |
| `native` | 내장 구조화 파서, 현재 `.docx`와 LightRAG Document 사이드카에 중점. |
| `mineru` | PDF, Office 파일, 이미지를 위한 외부 MinerU 파서. `MINERU_API_MODE`와 `MINERU_LOCAL_ENDPOINT` 또는 `MINERU_API_TOKEN` 필요. |
| `docling` | PDF, Office 파일, Markdown/HTML, 이미지를 위한 외부 docling-serve 파서. `DOCLING_ENDPOINT` 필요. |

파일명 힌트는 업로드된 단일 파일의 기본 규칙을 재정의합니다:

```text
paper.[mineru-iteP].pdf
memo.[native-R!].docx
notes.[-R].md
```

### 처리 옵션

처리 옵션은 엔진 뒤에 하이픈으로 추가하거나, 파일명 힌트에서 `[-OPTIONS]`로 단독으로 제공합니다.

| 옵션 | 의미 |
| --- | --- |
| `i` | 사이드카가 존재하는 경우 이미지/드로잉에 VLM 분석 실행 |
| `t` | 사이드카가 존재하는 경우 표에 VLM 분석 실행 |
| `e` | 사이드카가 존재하는 경우 수식에 VLM 분석 실행 |
| `!` | 개체/관계 추출 및 그래프 쓰기 건너뜀; 청크 벡터는 여전히 저장 |
| `F` | 고정 토큰 청킹, 레거시 청킹 방법 |
| `R` | 설정 가능한 구분자 계단식을 사용한 재귀 문자 청킹 |
| `V` | 시맨틱 벡터 청킹; 크기 초과 청크는 `R`로 재분할 |
| `P` | 구조화 LightRAG Document 콘텐츠를 위한 단락 시맨틱 청킹; 구조화 콘텐츠가 없으면 `R`로 폴백 |

### 파이프라인 동시성

`MAX_PARALLEL_INSERT`는 병렬로 처리되는 파일 수를 제어합니다. `MAX_ASYNC`는 추출, 병합, 쿼리 키워드 생성, 최종 답변 생성을 포함한 동시 LLM 호출을 제어합니다. 파서 중심 배포를 위해 `MAX_PARALLEL_PARSE_NATIVE`, `MAX_PARALLEL_PARSE_MINERU`, `MAX_PARALLEL_PARSE_DOCLING`, `MAX_PARALLEL_ANALYZE` 같은 선택적 단계별 파이프라인 변수를 사용할 수 있습니다.

처리 루프가 바쁜 동안에도 업로드와 텍스트 삽입이 허용됩니다. 실행 중인 루프는 새로운 대기 작업을 처리하도록 촉구됩니다. 문서 지우기/삭제 및 `/documents/scan`의 분류 단계 같은 파괴적 작업은 스토리지 일관성을 보호하기 위해 동시 enqueue를 여전히 거부합니다. 실패한 파일은 WebUI에서 또는 `/documents/scan`을 트리거하여 재처리할 수 있습니다.

## API 엔드포인트

모든 지원 백엔드는 동일한 LightRAG REST API 표면을 노출합니다. API Server가 실행 중일 때:

- Swagger UI: http://localhost:9621/docs
- ReDoc: http://localhost:9621/redoc

`/health` 엔드포인트는 역할 LLM 설정, LLM/임베딩/리랭크 큐 상태, 워크스페이스/스토리지 워크스페이스 매핑, VLM 활성화, 리랭크 활성화, 파이프라인 busy/scanning/destructive 상태를 포함한 운영 상태와 선택된 설정을 보고합니다.

## 비동기 문서 인덱싱 및 진행 상황 추적

LightRAG는 비동기 문서 인덱싱을 구현하여 프론트엔드가 문서 처리 진행 상황을 모니터링하고 쿼리할 수 있습니다. 지정된 엔드포인트를 통해 파일을 업로드하거나 텍스트를 삽입하면 고유한 Track ID가 반환되어 실시간 진행 상황 모니터링이 가능합니다.

**Track ID 생성을 지원하는 API 엔드포인트:**

* `/documents/upload`
* `/documents/text`
* `/documents/texts`

**문서 처리 상태 쿼리 엔드포인트:**
* `/documents/track_status/{track_id}`

이 엔드포인트는 다음을 포함한 포괄적인 상태 정보를 제공합니다:
* 문서 처리 상태 (pending/processing/processed/failed)
* 콘텐츠 요약 및 메타데이터
* 처리 실패 시 오류 메시지
* 생성 및 업데이트 타임스탬프
