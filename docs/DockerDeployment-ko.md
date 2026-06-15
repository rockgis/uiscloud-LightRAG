# LightRAG Docker 배포

다중 LLM 백엔드를 지원하는 경량 지식 그래프 기반 검색 증강 생성(RAG) 시스템.

## 🚀 준비 사항

### 저장소 복제:

```bash
# Linux/MacOS
git clone https://github.com/HKUDS/LightRAG.git
cd LightRAG
```
```powershell
# Windows PowerShell
git clone https://github.com/HKUDS/LightRAG.git
cd LightRAG
```

### 환경 설정:

```bash
# Linux/MacOS
cp .env.example .env
# .env를 선호하는 설정으로 편집
```
```powershell
# Windows PowerShell
Copy-Item .env.example .env
# .env를 선호하는 설정으로 편집
```

LightRAG는 `.env` 파일의 환경 변수로 설정할 수 있습니다:

**서버 설정**

- `HOST`: 서버 호스트 (기본값: 0.0.0.0)
- `PORT`: 서버 포트 (기본값: 9621)

**LLM 설정**

- `LLM_BINDING`: 사용할 LLM 백엔드 (lollms/ollama/openai)
- `LLM_BINDING_HOST`: LLM 서버 호스트 URL
- `LLM_MODEL`: 사용할 모델명

**임베딩(Embedding) 설정**

- `EMBEDDING_BINDING`: 임베딩 백엔드 (lollms/ollama/openai)
- `EMBEDDING_BINDING_HOST`: 임베딩 서버 호스트 URL
- `EMBEDDING_MODEL`: 임베딩 모델명
- `EMBEDDING_ASYMMETRIC`: 쿼리/문서 비대칭 임베딩 명시적 활성화
- `EMBEDDING_DOCUMENT_PREFIX`: 접두사 기반 비대칭 임베딩의 문서 접두사 (또는 `NO_PREFIX`)
- `EMBEDDING_QUERY_PREFIX`: 접두사 기반 비대칭 임베딩의 쿼리 접두사 (또는 `NO_PREFIX`)

접두사 유효성 검사 규칙과 프로바이더별 동작은 [비대칭 임베딩 설정](./AsymmetricEmbedding-ko.md)을 참고하세요.

**RAG 설정**

- `MAX_ASYNC`: 최대 비동기 작업 수
- `MAX_TOKENS`: 최대 토큰 크기
- `EMBEDDING_DIM`: 임베딩 차원

## 🐳 Docker 배포

Docker 지침은 Docker Desktop이 설치된 모든 플랫폼에서 동일하게 작동합니다.

### 빌드 최적화

Dockerfile은 BuildKit 캐시 마운트를 사용하여 빌드 성능을 크게 향상시킵니다:

- **자동 캐시 관리**: `# syntax=docker/dockerfile:1` 지시어를 통해 BuildKit이 자동으로 활성화됩니다
- **빠른 재빌드**: `uv.lock` 또는 `bun.lock` 파일이 수정될 때만 변경된 의존성을 다운로드합니다
- **효율적인 패키지 캐싱**: UV 및 Bun 패키지 다운로드가 빌드 전반에 걸쳐 캐시됩니다
- **수동 설정 불필요**: Docker Compose와 GitHub Actions에서 바로 사용 가능합니다

### LightRAG 서버 시작:

```bash
docker compose up -d
```

대화형 설정을 사용한 경우 생성된 스택으로 시작:

```bash
docker compose -f docker-compose.final.yml up -d
```

대화형 설정은 `.env`를 호스트에서 사용할 수 있도록 유지합니다. `postgres` 또는 `host.docker.internal` 같은 컨테이너 전용 호스트 이름과 `/app/data/certs/` 아래의 SSL 경로는 `.env`에 다시 저장되는 대신 `lightrag` 서비스에 대해 생성된 `docker-compose.final.yml`에 주입됩니다.
재실행 시 `docker-compose.final.yml`에서 변경되지 않은 마법사 관리 서비스 블록은 기본적으로 보존됩니다. 번들 템플릿에서 관리 블록을 복구하거나 완전히 재생성하려면 `make env-base-rewrite` 또는 `make env-storage-rewrite`로 해당 설정 타겟을 재실행하세요.

생성된 스택에 로컬 Milvus가 포함된 경우, compose는 시작 시 저장소 `.env` 또는 내보낸 셸 환경에서 `MINIO_ACCESS_KEY_ID`와 `MINIO_SECRET_ACCESS_KEY`를 해석합니다. 생성된 compose 파일은 해당 값을 스냅샷하지 않으며, 변수 중 하나라도 없으면 `docker compose`가 즉시 종료됩니다.

생성된 스택을 localhost 외부에 노출하기 전에 다음을 실행하세요:

```bash
make env-security-check
```

이 명령은 파일을 재작성하지 않고 누락된 인증, 안전하지 않은 화이트리스트 설정, 약한 JWT 시크릿 및 기타 설정 수준 보안 위험에 대해 현재 `.env`를 감사합니다.

LightRAG 서버는 데이터 저장에 다음 경로를 사용합니다:

```
data/
├── rag_storage/    # RAG 데이터 영구 저장
└── inputs/         # 입력 문서
```

### 선택 사항: 로컬 vLLM 임베딩 및 리랭커

vLLM로 임베딩 및/또는 리랭킹을 로컬에서 실행하려면 `make env-base`를 실행하고 Docker를 통해 임베딩 모델과 리랭크 서비스를 로컬에서 실행할지 묻는 프롬프트에 `yes`로 답하세요.
그러면 임베딩 서비스가 포트 8001에서 로컬 vLLM 서버로 `BAAI/bge-m3`를 사용하도록 설정되고, 포트 8000에서 `vllm-rerank` 서비스를 추가할 수도 있습니다.

GPU 호스트용 예시 `docker-compose.override.yml` (임베딩 + 리랭커):

```yaml
services:
  vllm-embed:
    image: vllm/vllm-openai:latest
    runtime: nvidia
    command: >
      --model BAAI/bge-m3
      --port 8001
      --dtype float16
    ports:
      - "8001:8001"
    volumes:
      - ./data/hf-cache:/root/.cache/huggingface
    ipc: host
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]

  vllm-rerank:
    image: vllm/vllm-openai:latest
    runtime: nvidia
    command: >
      --model BAAI/bge-reranker-v2-m3
      --port 8000
      --dtype float16
    ports:
      - "8000:8000"
    volumes:
      - ./data/hf-cache:/root/.cache/huggingface
    ipc: host
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
```

CPU 전용 호스트는 공식 CPU 이미지를 사용하세요:

```yaml
services:
  vllm-embed:
    image: vllm/vllm-openai-cpu:latest
    command: >
      --model BAAI/bge-m3
      --port 8001
      --dtype float32
    ports:
      - "8001:8001"
    volumes:
      - ./data/hf-cache:/root/.cache/huggingface

  vllm-rerank:
    image: vllm/vllm-openai-cpu:latest
    command: >
      --model BAAI/bge-reranker-v2-m3
      --port 8000
      --dtype float32
    ports:
      - "8000:8000"
    volumes:
      - ./data/hf-cache:/root/.cache/huggingface
```

`.env`에 임베딩 및 리랭크 설정 추가:

```bash
EMBEDDING_BINDING=openai
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIM=1024
EMBEDDING_BINDING_HOST=http://localhost:8001/v1
EMBEDDING_BINDING_API_KEY=local-key
VLLM_EMBED_DEVICE=cpu

RERANK_BINDING=cohere
RERANK_MODEL=BAAI/bge-reranker-v2-m3
RERANK_BINDING_HOST=http://localhost:8000/rerank
RERANK_BINDING_API_KEY=local-key
VLLM_RERANK_DEVICE=cpu
```

LightRAG가 Docker에서 실행되고 vLLM이 호스트에서 실행되는 경우, 생성된 compose 파일은 해당 엔드포인트를 다음으로 재작성합니다:

```bash
EMBEDDING_BINDING_HOST=http://host.docker.internal:8001/v1
RERANK_BINDING_HOST=http://host.docker.internal:8000/rerank
```

GPU의 경우 설정:

```bash
VLLM_EMBED_DEVICE=cuda
VLLM_RERANK_DEVICE=cuda
```

NVIDIA Container Toolkit이 설치되어 있고 호스트에 CUDA 드라이버가 있는지 확인하세요.

### SSL 인증서

설정 마법사는 compose 파일 생성 전에 TLS 인증서 파일을 `./data/certs/` 아래에 스테이징합니다. 이렇게 하면 생성된 호스트 마운트가 기본 Docker 배포에서 사용하는 것과 동일한 `./data` 루트 아래에 유지됩니다.

### PostgreSQL 이미지

대화형 설정은 PostgreSQL을 기본값으로 `gzdaniel/postgres-for-rag:16.6`으로 설정합니다. 이 이미지는 Apache AGE와 pgvector를 모두 번들로 포함하므로 추가 확장 설정 없이 `PGGraphStorage` 및 `PGVectorStorage`와 함께 생성된 스택이 작동합니다.

**중요 참고 사항**: 벡터 저장소에 PGGraphStorage가 필요하지 않은 경우, 위의 Docker 이미지를 최신 공식 pgvector 이미지인 `pgvector/pgvector:pg18`로 교체할 수 있습니다. 데이터 파일 형식은 서로 다른 PostgreSQL 메이저 버전 간에 호환되지 않으므로, 이 Docker 이미지가 배포된 후에는 이전 버전으로 롤백할 수 없습니다.

### 업데이트

Docker 컨테이너 업데이트:
```bash
docker compose pull
docker compose down
docker compose up
```

### 오프라인 배포

`transformers`, `torch`, 또는 `cuda`가 필요한 소프트웨어 패키지는 Docker 이미지에 사전 설치되지 않습니다. 따라서 Docling 같은 문서 추출 도구와 HuggingFace, LMDeploy 같은 로컬 LLM 모델은 오프라인 환경에서 사용할 수 없습니다. 이러한 고성능 연산 서비스는 LightRAG에 통합해서는 안 됩니다. Docling은 독립 서비스로 분리되어 배포될 예정입니다.

## 📦 Docker 이미지 빌드

### 로컬 개발 및 테스트용

```bash
# Docker Compose로 빌드 및 실행 (BuildKit 자동 활성화)
docker compose up --build

# 또는 필요한 경우 BuildKit을 명시적으로 활성화
DOCKER_BUILDKIT=1 docker compose up --build
```

**참고**: BuildKit은 Dockerfile의 `# syntax=docker/dockerfile:1` 지시어에 의해 자동으로 활성화되어 최적의 캐싱 성능을 보장합니다.

### 프로덕션 릴리스용

**멀티 아키텍처 빌드 및 푸시**:

```bash
# 제공된 빌드 스크립트 사용
./docker-build-push.sh
```

**빌드 스크립트가 수행하는 작업**:

- Docker 레지스트리 로그인 상태 확인
- buildx 빌더 자동 생성/사용
- AMD64 및 ARM64 아키텍처 모두 빌드
- GitHub Container Registry(ghcr.io)에 푸시
- 멀티 아키텍처 매니페스트 확인

**사전 요구 사항**:

멀티 아키텍처 이미지 빌드 전 다음이 필요합니다:

- Buildx 지원이 있는 Docker 20.10+
- 충분한 디스크 공간 (오프라인 이미지의 경우 20GB+ 권장)
- 레지스트리 접근 자격 증명 (이미지를 푸시하는 경우)

### Cosign으로 공식 GHCR 이미지 검증

GitHub Actions에 의해 GitHub Container Registry에 배포된 공식 LightRAG 이미지는 GitHub OIDC 키 없는 서명을 사용하는 Sigstore Cosign으로 서명됩니다.

`cosign`을 설치한 후 실행하려는 이미지 태그를 검증하세요:

```bash
cosign verify ghcr.io/HKUDS/LightRAG:<tag> \
  --certificate-identity-regexp '^https://github.com/HKUDS/LightRAG/.github/workflows/(docker-publish|docker-build-manual|docker-build-lite)\.yml@refs/.+$' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

`<tag>`를 검증할 버전 태그로 교체하세요. 예: 릴리스 태그, `latest`, `<tag>-lite`, `lite`.
