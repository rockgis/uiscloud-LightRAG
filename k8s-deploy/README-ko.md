# LightRAG Helm 차트

LightRAG를 Kubernetes 클러스터에 배포하기 위한 Helm 차트입니다.

LightRAG에는 두 가지 권장 배포 방법이 있습니다:
1. **경량 배포**: 내장 경량 스토리지를 사용하며, 테스트 및 소규모 사용에 적합
2. **프로덕션 배포**: 외부 데이터베이스(PostgreSQL, Neo4J 등)를 사용하며, 프로덕션 환경 및 대규모 사용에 적합

> 배포 과정의 영상 가이드를 원하신다면 YouTube의 [동영상 튜토리얼](https://youtu.be/JW1z7fzeKTw?si=vPzukqqwmdzq9Q4q)을 참고하세요. 시각적인 안내를 선호하는 분들에게 도움이 될 수 있습니다.

## 사전 요구사항

다음 도구들이 설치 및 설정되어 있는지 확인하세요:

* **Kubernetes 클러스터**
  * 실행 중인 Kubernetes 클러스터가 필요합니다.
  * 로컬 개발 또는 데모의 경우 [Minikube](https://minikube.sigs.k8s.io/docs/start/)를 사용할 수 있습니다 (CPU ≥ 2개, RAM ≥ 4GB, Docker/VM-드라이버 지원 필요).
  * EKS, GKE, AKS 등 표준 클라우드 또는 온프레미스 Kubernetes 클러스터도 지원됩니다.

* **kubectl**
  * 클러스터를 관리하기 위한 Kubernetes 커맨드라인 도구.
  * 공식 가이드 참고: [kubectl 설치 및 설정](https://kubernetes.io/docs/tasks/tools/#kubectl).

* **Helm** (v3.x 이상)
  * LightRAG 설치에 사용되는 Kubernetes 패키지 매니저.
  * 공식 가이드를 통해 설치: [Helm 설치](https://helm.sh/docs/intro/install/).

## 경량 배포 (외부 데이터베이스 불필요)

이 배포 옵션은 내장 경량 스토리지 컴포넌트를 사용하며, 테스트, 데모, 소규모 사용 시나리오에 적합합니다. 외부 데이터베이스 설정이 필요하지 않습니다.

제공된 편의 스크립트 또는 직접 Helm 명령으로 LightRAG를 배포할 수 있습니다. 두 방법 모두 `lightrag/values.yaml` 파일에 정의된 동일한 환경 변수를 설정합니다.

### 편의 스크립트 사용 (권장):

```bash
export OPENAI_API_BASE=<YOUR_OPENAI_API_BASE>
export OPENAI_API_KEY=<YOUR_OPENAI_API_KEY>
bash ./install_lightrag_dev.sh
```

### 또는 Helm으로 직접 배포:

```bash
# 원하는 환경 파라미터를 재정의할 수 있습니다
helm upgrade --install lightrag ./lightrag \
  --namespace rag \
  --set-string env.LIGHTRAG_KV_STORAGE=JsonKVStorage \
  --set-string env.LIGHTRAG_VECTOR_STORAGE=NanoVectorDBStorage \
  --set-string env.LIGHTRAG_GRAPH_STORAGE=NetworkXStorage \
  --set-string env.LIGHTRAG_DOC_STATUS_STORAGE=JsonDocStatusStorage \
  --set-string env.LLM_BINDING=openai \
  --set-string env.LLM_MODEL=gpt-4o-mini \
  --set-string env.LLM_BINDING_HOST=$OPENAI_API_BASE \
  --set-string env.LLM_BINDING_API_KEY=$OPENAI_API_KEY \
  --set-string env.EMBEDDING_BINDING=openai \
  --set-string env.EMBEDDING_MODEL=text-embedding-ada-002 \
  --set-string env.EMBEDDING_DIM=1536 \
  --set-string env.EMBEDDING_BINDING_API_KEY=$OPENAI_API_KEY
```

### 애플리케이션 접근:

```bash
# 1. 터미널에서 이 포트 포워드 명령을 실행합니다:
kubectl --namespace rag port-forward svc/lightrag-dev 9621:9621

# 2. 명령이 실행 중인 동안 브라우저를 열고 다음 주소로 이동합니다:
# http://localhost:9621
```

## 프로덕션 배포 (외부 데이터베이스 사용)

### 1. 데이터베이스 설치
> 데이터베이스가 이미 준비되어 있다면 이 단계를 건너뛸 수 있습니다. 자세한 내용은 [README.md](databases%2FREADME-ko.md)를 참고하세요.

데이터베이스 배포에는 KubeBlocks를 권장합니다. KubeBlocks는 Kubernetes에서 모든 데이터베이스를 프로덕션 규모로 쉽게 실행할 수 있게 해주는 클라우드 네이티브 데이터베이스 오퍼레이터입니다.

먼저 KubeBlocks와 KubeBlocks-Addons를 설치합니다 (이미 설치된 경우 건너뜀):
```bash
bash ./databases/01-prepare.sh
```

그런 다음 필요한 데이터베이스를 설치합니다. 기본적으로 PostgreSQL과 Neo4J가 설치되지만, [00-config.sh](databases%2F00-config.sh)를 수정하여 필요에 따라 다른 데이터베이스를 선택할 수 있습니다:
```bash
bash ./databases/02-install-database.sh
```

클러스터가 실행 중인지 확인합니다:
```bash
kubectl get clusters -n rag
# 예상 출력:
# NAME            CLUSTER-DEFINITION   TERMINATION-POLICY   STATUS     AGE
# neo4j-cluster                        Delete               Running    39s
# pg-cluster      postgresql           Delete               Running    42s

kubectl get po -n rag
# 예상 출력:
# NAME                      READY   STATUS    RESTARTS   AGE
# neo4j-cluster-neo4j-0     1/1     Running   0          58s
# pg-cluster-postgresql-0   4/4     Running   0          59s
# pg-cluster-postgresql-1   4/4     Running   0          59s
```

### 2. LightRAG 설치

LightRAG와 데이터베이스는 동일한 Kubernetes 클러스터 내에 배포되므로 설정이 간단합니다.
설치 스크립트가 KubeBlocks에서 모든 데이터베이스 연결 정보를 자동으로 가져오므로 데이터베이스 자격증명을 수동으로 설정할 필요가 없습니다:

```bash
export OPENAI_API_BASE=<YOUR_OPENAI_API_BASE>
export OPENAI_API_KEY=<YOUR_OPENAI_API_KEY>
bash ./install_lightrag.sh
```

### 애플리케이션 접근:

```bash
# 1. 터미널에서 이 포트 포워드 명령을 실행합니다:
kubectl --namespace rag port-forward svc/lightrag 9621:9621

# 2. 명령이 실행 중인 동안 브라우저를 열고 다음 주소로 이동합니다:
# http://localhost:9621
```

## 설정

### 리소스 설정 수정

`values.yaml` 파일을 수정하여 LightRAG의 리소스 사용량을 설정할 수 있습니다:

```yaml
replicaCount: 1  # 레플리카 수, 필요에 따라 늘릴 수 있음

resources:
  limits:
    cpu: 1000m    # CPU 한도, 필요에 따라 조정 가능
    memory: 2Gi   # 메모리 한도, 필요에 따라 조정 가능
  requests:
    cpu: 500m     # CPU 요청량, 필요에 따라 조정 가능
    memory: 1Gi   # 메모리 요청량, 필요에 따라 조정 가능
```

### 영속 스토리지 수정

```yaml
persistence:
  enabled: true
  ragStorage:
    size: 10Gi    # RAG 스토리지 크기, 필요에 따라 조정 가능
  inputs:
    size: 5Gi     # 입력 데이터 스토리지 크기, 필요에 따라 조정 가능
```

### 환경 변수 설정

`values.yaml` 파일의 `env` 섹션은 `.env` 파일과 유사하게 LightRAG의 모든 환경 설정을 포함합니다. `helm upgrade` 또는 `helm install` 명령에서 `--set` 플래그로 이를 재정의할 수 있습니다.

```yaml
env:
  HOST: 0.0.0.0
  PORT: 9621
  WEBUI_TITLE: Graph RAG Engine
  WEBUI_DESCRIPTION: Simple and Fast Graph Based RAG System

  # LLM 설정
  LLM_BINDING: openai            # LLM 서비스 공급자
  LLM_MODEL: gpt-4o-mini         # LLM 모델
  LLM_BINDING_HOST:              # API 기본 URL (선택 사항)
  LLM_BINDING_API_KEY:           # API 키

  # 임베딩 설정
  EMBEDDING_BINDING: openai                 # 임베딩 서비스 공급자
  EMBEDDING_MODEL: text-embedding-ada-002   # 임베딩 모델
  EMBEDDING_DIM: 1536                       # 임베딩 차원
  EMBEDDING_BINDING_API_KEY:                # API 키

  # 스토리지 설정
  LIGHTRAG_KV_STORAGE: PGKVStorage              # 키-값 스토리지 유형
  LIGHTRAG_VECTOR_STORAGE: PGVectorStorage      # 벡터 스토리지 유형
  LIGHTRAG_GRAPH_STORAGE: Neo4JStorage          # 그래프 스토리지 유형
  LIGHTRAG_DOC_STATUS_STORAGE: PGDocStatusStorage  # 문서 상태 스토리지 유형
```

## 참고사항

- 배포 전에 필요한 모든 환경 변수(API 키 및 데이터베이스 비밀번호)가 설정되어 있는지 확인하세요
- 보안을 위해 민감한 정보는 스크립트나 values 파일에 직접 작성하는 것보다 환경 변수를 통해 전달하는 것이 권장됩니다
- 경량 배포는 테스트 및 소규모 사용에 적합하지만 데이터 영속성과 성능에 제한이 있을 수 있습니다
- 프로덕션 환경 및 대규모 사용에는 프로덕션 배포(PostgreSQL + Neo4J)를 권장합니다
- 더 커스터마이징된 설정은 LightRAG 공식 문서를 참고하세요
