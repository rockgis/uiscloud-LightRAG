# KubeBlocks를 사용한 데이터베이스 배포 및 관리

KubeBlocks를 통해 Kubernetes(K8s) 환경에서 다양한 데이터베이스를 빠르게 배포하고 관리하는 방법을 알아봅니다.

## KubeBlocks 소개

KubeBlocks는 SQL, NoSQL, 벡터, 문서 등 모든 데이터베이스를 Kubernetes에서 실행할 수 있는 프로덕션 준비 완료 오픈소스 툴킷입니다.
빠른 개발 테스트부터 완전한 프로덕션 클러스터까지 원활하게 확장되어, 여러 데이터 스토어를 함께 사용해야 하는 FastGPT 같은 RAG 워크로드에 적합한 선택입니다.

## 사전 요구사항

다음 도구들이 설치 및 설정되어 있는지 확인하세요:

* **Kubernetes 클러스터**
  * 실행 중인 Kubernetes 클러스터가 필요합니다.
  * 로컬 개발 또는 데모의 경우 [Minikube](https://minikube.sigs.k8s.io/docs/start/)를 사용할 수 있습니다 (CPU ≥ 2개, RAM ≥ 4GB, Docker/VM-드라이버 지원 필요).
  * EKS, GKE, AKS 등 표준 클라우드 또는 온프레미스 Kubernetes 클러스터도 지원됩니다.

* **kubectl**
  * Kubernetes 커맨드라인 인터페이스.
  * 공식 가이드 참고: [kubectl 설치 및 설정](https://kubernetes.io/docs/tasks/tools/#kubectl).

* **Helm** (v3.x 이상)
  * 아래 스크립트에서 사용하는 Kubernetes 패키지 매니저.
  * 공식 가이드를 통해 설치: [Helm 설치](https://helm.sh/docs/intro/install/).

## 설치

1. **원하는 데이터베이스 설정**

   `00-config.sh` 파일을 편집합니다. 요구사항에 따라 설치할 데이터베이스의 변수를 `true`로 설정하세요.
   예를 들어 PostgreSQL과 Neo4j를 설치하려면:

   ```bash
   ENABLE_POSTGRESQL=true
   ENABLE_REDIS=false
   ENABLE_ELASTICSEARCH=false
   ENABLE_QDRANT=false
   ENABLE_MONGODB=false
   ENABLE_NEO4J=true
   ```

2. **환경 준비 및 KubeBlocks 애드온 설치**

   ```bash
   bash ./01-prepare.sh
   ```

   *스크립트 동작*
   `01-prepare.sh`는 기본 사전 확인(Helm, kubectl, 클러스터 접근성)을 수행하고, KubeBlocks Helm 저장소를 추가하며, KubeBlocks 자체에 필요한 핵심 CRD 또는 컨트롤러를 설치합니다. 또한 `00-config.sh`에서 활성화한 모든 데이터베이스의 애드온을 설치하지만 **실제 데이터베이스 클러스터는 아직 생성하지 않습니다**.

3. **(선택 사항) 데이터베이스 설정 수정**

   배포 전에 각 `<db>/` 디렉터리 내의 `values.yaml` 파일을 편집하여 `version`, `replicas`, `CPU`, `memory`, `storage size` 등을 변경할 수 있습니다.

4. **데이터베이스 클러스터 설치**

   ```bash
   bash ./02-install-database.sh
   ```

   *스크립트 동작*
   `02-install-database.sh`는 **선택한 데이터베이스를 Kubernetes에 실제로 배포합니다**.

   스크립트가 완료되면 클러스터가 실행 중인지 확인하세요. 모든 클러스터가 준비 상태가 되는 데 몇 분이 걸릴 수 있습니다.
   특히 Kubernetes가 레지스트리에서 컨테이너 이미지를 처음 pull하는 경우 더 오래 걸릴 수 있습니다.
   다음 명령으로 진행 상황을 모니터링할 수 있습니다:

   ```bash
   kubectl get clusters -n rag
   NAME              CLUSTER-DEFINITION   TERMINATION-POLICY   STATUS    AGE
   es-cluster                             Delete               Running   11m
   mongodb-cluster   mongodb              Delete               Running   11m
   pg-cluster        postgresql           Delete               Running   11m
   qdrant-cluster    qdrant               Delete               Running   11m
   redis-cluster     redis                Delete               Running   11m
   ```

   KubeBlocks가 생성한 모든 데이터베이스 `Pod`를 확인할 수 있습니다.
   처음에는 `ContainerCreating` 또는 `Pending` 상태의 파드가 보일 수 있습니다. 이미지를 pull하고 컨테이너가 시작되는 동안의 정상적인 상태입니다.
   모든 파드가 `Running` 상태가 될 때까지 기다리세요:

   ```bash
   kubectl get po -n rag
   NAME                        READY   STATUS    RESTARTS   AGE
   es-cluster-mdit-0           2/2     Running   0          11m
   mongodb-cluster-mongodb-0   2/2     Running   0          11m
   pg-cluster-postgresql-0     4/4     Running   0          11m
   pg-cluster-postgresql-1     4/4     Running   0          11m
   qdrant-cluster-qdrant-0     2/2     Running   0          11m
   redis-cluster-redis-0       2/2     Running   0          11m
   ```

   예상보다 오래 걸리는 경우 특정 파드의 상세 상태를 확인할 수 있습니다:

   ```bash
   kubectl describe pod <pod-name> -n rag
   ```

## 데이터베이스 연결

데이터베이스에 연결하려면 다음 단계에 따라 사용 가능한 계정을 확인하고, 자격증명을 가져와 연결을 설정합니다:

### 1. 사용 가능한 데이터베이스 클러스터 목록 확인

먼저 네임스페이스에서 실행 중인 데이터베이스 클러스터를 확인합니다:

```bash
kubectl get cluster -n rag
```

### 2. 인증 자격증명 가져오기

PostgreSQL의 경우 Kubernetes 시크릿에서 사용자 이름과 비밀번호를 가져옵니다:

```bash
# PostgreSQL 사용자 이름 가져오기
kubectl get secrets -n rag pg-cluster-postgresql-account-postgres -o jsonpath='{.data.username}' | base64 -d
# PostgreSQL 비밀번호 가져오기
kubectl get secrets -n rag pg-cluster-postgresql-account-postgres -o jsonpath='{.data.password}' | base64 -d
```

올바른 시크릿 이름을 찾기 어려운 경우 모든 시크릿을 나열합니다:

```bash
kubectl get secrets -n rag
```

### 3. 로컬 머신으로 포트 포워딩

포트 포워딩을 사용하여 로컬 머신에서 PostgreSQL에 접근합니다:

```bash
# PostgreSQL 포트(5432)를 로컬 머신으로 포워딩
# kubectl get svc -n rag 명령으로 모든 서비스를 확인할 수 있습니다
kubectl port-forward -n rag svc/pg-cluster-postgresql-postgresql 5432:5432
```

### 4. 데이터베이스 클라이언트로 연결

가져온 자격증명으로 선호하는 PostgreSQL 클라이언트를 사용하여 연결합니다:

```bash
# 예시: psql로 연결
export PGUSER=$(kubectl get secrets -n rag pg-cluster-postgresql-account-postgres -o jsonpath='{.data.username}' | base64 -d)
export PGPASSWORD=$(kubectl get secrets -n rag pg-cluster-postgresql-account-postgres -o jsonpath='{.data.password}' | base64 -d)
psql -h localhost -p 5432 -U $PGUSER
```

데이터베이스에 연결하는 동안 포트 포워딩 터미널을 계속 실행 상태로 유지하세요.


## 제거

1. **데이터베이스 클러스터 삭제**

   ```bash
   bash ./03-uninstall-database.sh
   ```

   스크립트는 `00-config.sh`에서 활성화된 데이터베이스 클러스터를 삭제합니다.

2. **KubeBlocks 애드온 정리**

   ```bash
   bash ./04-cleanup.sh
   ```

   `01-prepare.sh`가 설치한 애드온을 제거합니다.

## 참고 자료
* [KubeBlocks 문서](https://kubeblocks.io/docs/preview/user_docs/overview/introduction)
