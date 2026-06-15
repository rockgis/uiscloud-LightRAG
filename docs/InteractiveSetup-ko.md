# 대화형 설정 가이드

`.env`를 직접 편집하는 대신 LightRAG가 설정을 안내하도록 하고 싶을 때 대화형 설정 마법사를 사용하세요.

마법사는 `make` 타겟을 통해 노출됩니다:

- `make env-base`
- `make env-storage`
- `make env-server`
- `make env-validate`
- `make env-security-check`
- `make env-backup`
- `make env-base-rewrite`
- `make env-storage-rewrite`

기본 셸 스크립트를 직접 호출할 필요가 없습니다.

## 마법사의 용도

설정 마법사는 세 부분으로 LightRAG를 설정하는 데 도움을 줍니다:

- `env-base`는 LLM, 임베딩 모델, 선택적 리랭커를 설정합니다.
- `env-storage`는 PostgreSQL, Neo4j, Redis, Milvus, Qdrant, MongoDB, Memgraph 등의 스토리지 백엔드를 추가하거나 변경합니다.
- `env-server`는 서버 호스트 및 포트, WebUI 레이블, 인증, API 키, SSL을 설정합니다.

각 단계는 나중에 다시 실행할 수 있습니다. 마법사는 기존 `.env`를 로드하고 현재 값을 기본값으로 표시하므로 변경해야 할 항목만 수정하면 됩니다.

## 시작 전

- 저장소 루트에서 명령어를 실행하세요.
- `make env-*` 타겟은 자동으로 호환되는 Bash 4+ 인터프리터를 선택합니다.
- 설정 스크립트를 직접 호출하는 대신 문서화된 `make env-*` 타겟을 사용하세요.
- `make env-base`는 초기 `.env`를 생성하는 일반적인 시작점입니다.
- `make env-storage`와 `make env-server`는 기존 `.env`가 필요합니다.
- 마법사 관리 Docker 서비스를 선택하면, 마법사가 Docker 시작 경로에 맞게 LightRAG를 준비합니다.

## 설정 경로 선택

다음 빠른 가이드를 사용하여 실행할 항목을 결정하세요:

- 원격 모델 프로바이더로 가장 빠른 첫 실행을 원한다면: `make env-base`
- 임베딩 또는 리랭킹을 Docker에서 로컬로 실행하려면: `make env-base`
- 모델은 이미 설정했고 이제 데이터베이스를 원한다면: `make env-storage`
- 모델은 이미 설정했고 이제 인증, API 키, SSL을 원한다면: `make env-server`
- 현재 설정이 유효한지 확인하고 싶다면: `make env-validate`
- 노출 전에 현재 설정을 감사하고 싶다면: `make env-security-check`
- 설정 변경 없이 독립적인 백업을 원한다면: `make env-backup`
- 번들 템플릿에서 생성된 compose 서비스를 복구해야 한다면: `make env-base-rewrite` 또는 `make env-storage-rewrite`

## 시나리오 1: 최초 로컬 설정

원격 모델 엔드포인트나 API 키가 이미 있고 최소한의 설정으로 LightRAG를 실행하려는 경우 사용하세요.

**명령어**

```bash
make env-base
```

**마법사가 묻는 내용**

- LLM 프로바이더, 모델, 엔드포인트, API 키
- 임베딩 모델을 Docker를 통해 로컬에서 실행할지 여부
- 임베딩이 원격인 경우: 임베딩 프로바이더, 모델, 차원, 엔드포인트, API 키
- 리랭킹 활성화 여부
- 리랭킹이 활성화된 경우: 리랭크 서비스를 Docker를 통해 로컬에서 실행할지 여부
- 리랭킹이 원격인 경우: 리랭크 프로바이더, 모델, 엔드포인트, API 키

**작성되는 내용**

- `.env`
- 마법사 관리 Docker 서비스를 활성화한 경우에만 `docker-compose.final.yml`

**다음 단계**

- 마법사 관리 Docker 서비스를 활성화하지 않은 경우:

```bash
lightrag-server
```

- 마법사 관리 Docker 서비스를 활성화한 경우:

```bash
docker compose -f docker-compose.final.yml up -d
```

## 시나리오 2: Docker 호스팅 임베딩 또는 리랭크를 사용한 로컬 설정

Docker를 통해 임베딩 및/또는 리랭킹을 위한 로컬 추론 서비스를 LightRAG에서 실행하려는 경우 사용하세요.

**명령어**

```bash
make env-base
```

**권장 답변**

- 로컬 임베딩을 원한다면 `Run embedding model locally via Docker (vLLM)?`에 `yes` 답변
- 로컬 리랭킹을 원한다면 `Enable reranking?`에 `yes`, 그 후 `Run rerank service locally via Docker?`에 `yes` 답변

**로컬 서비스를 활성화한 후 마법사가 묻는 내용**

- 로컬 vLLM의 임베딩 모델명
- 로컬 vLLM의 리랭크 모델명
- 메인 LLM이 외부인 경우 원격 LLM 세부 정보

**작성되는 내용**

- `.env`
- 선택된 로컬 서비스가 포함된 `docker-compose.final.yml`

**다음 단계**

```bash
docker compose -f docker-compose.final.yml up -d
```

이 명령은 선택된 로컬 서비스와 함께 생성된 Docker 기반 LightRAG 스택을 시작합니다.

## 시나리오 3: 기본 설정 후 스토리지 추가

이미 `make env-base`로 `.env`가 있고 기본 로컬 파일 스토리지에서 데이터베이스 기반 스토리지로 전환하려는 경우 사용하세요.

**명령어**

```bash
make env-storage
```

**전제 조건**

- `.env`가 이미 존재해야 합니다

**마법사가 묻는 내용**

- KV 스토리지 백엔드
- 벡터 스토리지 백엔드
- 그래프 스토리지 백엔드
- 문서 상태 스토리지 백엔드
- 각 필요한 데이터베이스에 대해 Docker를 통해 로컬에서 실행할지 여부
- 각 필요한 데이터베이스에 대한 연결 세부 정보 (호스트, URI, 포트, 사용자, 비밀번호, 데이터베이스 이름 또는 장치 유형)

**중요 규칙**

- `MongoVectorDBStorage`는 Atlas Search / Vector Search 지원이 필요합니다.
- 마법사 관리 Docker MongoDB 서비스를 선택하면, 마법사가 MongoDB Atlas Local을 프로비저닝하므로 `MongoVectorDBStorage`가 로컬 Docker 배포에서 실행될 수 있습니다. 생성된 호스트 측 `MONGO_URI`는 `?directConnection=true`를 사용합니다.
- 마법사 관리 Docker MongoDB 서비스를 사용하지 않는 경우, `MONGO_URI`에 Atlas 가능한 외부 MongoDB 엔드포인트를 제공하세요 (예: `mongodb+srv://` Atlas 클러스터 URI 또는 Atlas Local `mongodb://...?...directConnection=true` URI).
- 외부 `mongodb://...?...directConnection=true` URI의 경우, 마법사는 URI 형식만 검증할 수 있습니다. 대상 배포가 실제로 Atlas Search / Vector Search 지원을 제공하는지 정적으로 확인할 수 없습니다.

**작성되는 내용**

- `.env`
- 마법사 관리 스토리지 서비스를 선택한 경우 `docker-compose.final.yml`

**다음 단계**

- Docker 관리 스토리지 서비스를 선택한 경우:

```bash
docker compose -f docker-compose.final.yml up -d
```

- 외부 데이터베이스를 가리킨 경우, LightRAG 시작 전에 해당 서비스에 접근 가능한지 확인하세요.

## 시나리오 4: 인증 및 SSL로 배포 강화

이미 `.env`가 있고 공유 또는 외부 사용을 위해 서버를 준비해야 하는 경우 사용하세요.

**명령어**

```bash
make env-server
make env-security-check
```

**전제 조건**

- `.env`가 이미 존재해야 합니다

**`env-server`가 묻는 내용**

- 서버 호스트 및 포트
- WebUI 제목 및 설명
- 요약 언어
- 인증 및 API 키 설정 구성 여부
- 인증 계정, JWT 시크릿, 토큰 유효 기간, API 키, 화이트리스트 경로
- SSL/TLS 활성화 여부
- SSL 인증서 파일 경로 및 SSL 키 파일 경로

**작성되는 내용**

- `.env`
- 현재 설정이 이미 마법사 관리 Docker 서비스를 사용하는 경우 `docker-compose.final.yml` 업데이트 가능

**다음 단계**

- `make env-security-check` 실행
- 스택이 Docker를 사용하는 경우, compose 파일로 LightRAG 서비스 재생성
- 스택이 호스트에서 실행되는 경우, `lightrag-server` 재시작

더 광범위한 배포 가이드는 [DockerDeployment.md](./DockerDeployment-ko.md)를 참고하세요.

## 유효성 검사, 감사 및 백업

이 명령들은 전체 설정 흐름을 안내하지는 않지만 일반 운영의 일부입니다.

### 현재 설정 유효성 검사

```bash
make env-validate
```

현재 `.env`가 내부적으로 일관성이 있는지 확인하려는 경우 사용하세요. 누락된 필수 값, 잘못된 인증 설정, 유효하지 않은 URI, 유효하지 않은 포트, 또는 SSL 파일 누락 등의 문제를 보고합니다.

### 노출 전 보안 감사

```bash
make env-security-check
```

LightRAG를 localhost 외부에 노출하기 전에 사용하세요. 누락된 인증, 약하거나 없는 JWT 시크릿, 안전하지 않은 화이트리스트 설정, 또는 해결되지 않은 민감한 플레이스홀더 등 위험한 설정을 보고합니다.

### 독립적인 백업 생성

```bash
make env-backup
```

설정 흐름을 실행하지 않고 수동 백업을 원할 때 사용하세요.

## 출력 및 의미

### `.env`

마법사는 저장소 루트에 `.env`를 작성합니다. 이 파일은 최신 마법사 실행으로 생성된 현재 런타임 설정이 됩니다.

실제로 이는 다음을 의미합니다:

- 마법사를 재실행하면 `.env`가 업데이트됩니다
- 기존 값은 이후 실행에서 기본값으로 재사용됩니다
- `.env`를 가장 최근에 설정한 워크플로우의 활성 설정으로 취급해야 합니다
- `env-base`, `env-storage`, `env-server`가 `.env`를 작성하기 전에, 마법사는 파일이 있는 경우 타임스탬프가 붙은 백업을 자동으로 생성합니다

### `docker-compose.final.yml`

마법사는 마법사 관리 Docker 서비스를 선택하거나 기존 마법사 생성 compose 설정이 새 서버 설정과 맞춰야 할 때만 `docker-compose.final.yml`을 생성하거나 업데이트합니다.

설정 흐름 중 하나가 기존 생성된 compose 파일을 교체하거나 제거하려 할 때, 먼저 타임스탬프가 붙은 백업을 자동으로 생성합니다.

MongoDB 기반 스토리지의 경우, 마법사 관리 Docker 경로는 MongoDB Community Edition 대신 MongoDB Atlas Local을 사용하여 로컬 Atlas Search / Vector Search 워크플로우가 가능합니다.

생성된 Docker 스택을 시작할 때 이 파일을 사용하세요:

```bash
docker compose -f docker-compose.final.yml up -d
```

기본 `docker-compose.yml`은 일반 프로젝트 compose 파일로 유지됩니다. 생성된 `docker-compose.final.yml`은 마법사가 관리하는 출력입니다.

## 문제 해결 및 고급 참고 사항

- `make env-storage` 또는 `make env-server`가 `.env`가 누락되었다고 하면, 먼저 `make env-base`를 실행하세요.
- `env-base`, `env-storage`, `env-server`를 재실행하기 전에 `make env-backup`을 실행할 필요가 없습니다. 해당 흐름들이 이미 기존 `.env`를 백업하고, 변경 전에 생성된 compose 파일도 백업합니다.
- 현재 번들 템플릿에서 마법사 관리 compose 서비스를 완전히 재구성해야 한다면 `make env-base-rewrite` 또는 `make env-storage-rewrite`를 사용하세요.
- 호스트 지향 워크플로우와 Docker 지향 워크플로우 사이를 전환하는 경우, 이전 설정을 수동으로 병합하려 하지 말고 관련 설정 단계를 재실행하세요.
- 생성된 스택에 로컬 Milvus가 포함된 경우, `docker compose -f docker-compose.final.yml up -d`를 실행하기 전에 `MINIO_ACCESS_KEY_ID`와 `MINIO_SECRET_ACCESS_KEY`가 사용 가능한지 확인하세요.
- 대화형 마법사 이후의 Docker 배포 세부 사항은 [DockerDeployment.md](./DockerDeployment-ko.md)를 참고하세요.

## 일반적인 명령어 순서

### 원격 모델, 로컬 서버

```bash
make env-base
lightrag-server
```

### 원격 LLM, Docker의 로컬 임베딩 및 리랭크

```bash
make env-base
docker compose -f docker-compose.final.yml up -d
```

### 기본 설정 후 스토리지 추가

```bash
make env-base
make env-storage
docker compose -f docker-compose.final.yml up -d
```

### 노출 전 보안 및 SSL 추가

```bash
make env-base
make env-storage
make env-server
make env-security-check
docker compose -f docker-compose.final.yml up -d
```
