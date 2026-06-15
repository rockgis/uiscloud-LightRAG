# uv.lock 업데이트 가이드

## uv.lock이란?

`uv.lock`은 uv의 락 파일(Lock File)입니다. 다음과 마찬가지로 모든 의존성(전이적 의존성 포함)의 정확한 버전을 기록합니다:
- Node.js의 `package-lock.json`
- Rust의 `Cargo.lock`
- Python Poetry의 `poetry.lock`

`uv.lock`을 버전 관리에 포함하면 모든 팀원이 동일한 의존성을 설치하는 것을 보장합니다.

## uv.lock이 변경되는 경우

### 자동으로 변경되지 않는 경우

- `uv sync --frozen` 실행
- `uv sync --frozen`을 호출하는 Docker 이미지 빌드
- 의존성 메타데이터를 건드리지 않고 소스 코드만 편집

### 변경되는 경우

1. **`uv lock` 또는 `uv lock --upgrade`**

   ```bash
   uv lock                # 현재 제약 조건에 따라 의존성 해결
   uv lock --upgrade      # 재해결 및 최신 호환 버전으로 업그레이드
   ```

   `pyproject.toml` 수정 후, 최신 의존성 버전을 원할 때, 또는 락 파일이 삭제되거나 손상된 경우 이 명령을 사용하세요.

2. **`uv add`**

   ```bash
    uv add requests           # 의존성 추가 및 두 파일 모두 업데이트
    uv add --dev pytest       # 개발 의존성 추가
   ```

   `uv add`는 `pyproject.toml`을 편집하고 `uv.lock`을 한 번에 갱신합니다.

3. **`uv remove`**

   ```bash
   uv remove requests
   ```

   `pyproject.toml`에서 의존성을 제거하고 `uv.lock`을 재작성합니다.

4. **`--frozen` 없이 `uv sync`**

   ```bash
   uv sync
   ```

   일반적으로 이미 잠긴 내용만 설치합니다. 그러나 `pyproject.toml`과 `uv.lock`이 일치하지 않거나 락 파일이 없는 경우, uv는 `uv.lock`을 재생성하고 업데이트합니다. CI 및 프로덕션 빌드에서는 의도치 않은 업데이트를 방지하기 위해 `uv sync --frozen`을 사용하는 것을 권장합니다.

## 예시 워크플로우

### 시나리오 1: 새 의존성 추가

```bash
# 권장: uv가 두 파일을 모두 처리하도록 합니다
uv add fastapi
git add pyproject.toml uv.lock
git commit -m "Add fastapi dependency"

# 수동 대안
# 1. pyproject.toml 편집
# 2. 락 파일 재생성
uv lock
git add pyproject.toml uv.lock
git commit -m "Add fastapi dependency"
```

### 시나리오 2: 버전 제약 조건 완화 또는 강화

```bash
# 1. pyproject.toml의 요구 사항을 편집합니다
#    예: openai>=1.0.0,<2.0.0 → openai>=1.5.0,<2.0.0

# 2. 락 파일 재해결
uv lock

# 3. 두 파일 모두 커밋
git add pyproject.toml uv.lock
git commit -m "Update openai to >=1.5.0"
```

### 시나리오 3: 최신 호환 버전으로 전체 업그레이드

```bash
uv lock --upgrade
git diff uv.lock
git add uv.lock
git commit -m "Upgrade dependencies to latest compatible versions"
```

### 시나리오 4: 팀원이 프로젝트 동기화

```bash
git pull               # 최신 코드와 락 파일 가져오기
uv sync --frozen       # uv.lock에 명시된 그대로 설치
```

## Docker에서 uv.lock 사용

```dockerfile
RUN uv sync --frozen --no-dev --extra api
```

`--frozen`은 uv가 잠긴 버전에서 벗어나는 것을 거부하기 때문에 재현 가능한 빌드를 보장합니다.
`--extra api`는 API 서버를 설치합니다.

## 오프라인 의존성을 포함하는 락 파일 생성

선택적 오프라인 스택을 `uv.lock`에 포함해야 하는 경우, 관련 extras를 활성화하여 재생성하세요:

```bash
uv lock --extra api --extra offline
```

이 명령은 기본 프로젝트 요구 사항과 `api` 및 `offline` 선택적 의존성 세트를 모두 해결하여, 이후 `uv sync --frozen --extra api --extra offline` 설치가 추가 해결 없이 작동하도록 합니다.

## 자주 묻는 질문

- **`uv.lock`이 거의 1MB입니다. 문제가 있나요?**
  아니요. 이 파일은 의존성 해결 중에만 읽힙니다.

- **`uv.lock`을 커밋해야 하나요?**
  예. 협업자와 CI 작업이 동일한 의존성 그래프를 공유할 수 있도록 커밋하세요.

- **락 파일을 실수로 삭제했나요?**
  `uv lock`을 실행하여 `pyproject.toml`에서 재생성하세요.

- **`uv.lock`과 `requirements.txt`가 공존할 수 있나요?**
  가능하지만 두 파일을 모두 유지하는 것은 중복입니다. 가능한 경우 `uv.lock`만 사용하는 것을 권장합니다.

- **잠긴 버전을 어떻게 확인하나요?**
  ```bash
  uv tree
  grep -A5 'name = "openai"' uv.lock
  ```

## 모범 사례

### 권장 사항

1. `uv.lock`을 `pyproject.toml`과 함께 커밋하세요.
2. CI, Docker, 기타 재현 가능한 환경에서는 `uv sync --frozen`을 사용하세요.
3. uv가 자동으로 락을 조정하도록 하려면 로컬 개발 시 일반 `uv sync`를 사용하세요.
4. 최신 호환 버전을 반영하기 위해 주기적으로 `uv lock --upgrade`를 실행하세요.
5. 의존성 제약 조건을 변경한 직후에 락 파일을 재생성하세요.

### 피해야 할 사항

1. CI 또는 프로덕션 파이프라인에서 `--frozen` 없이 `uv sync` 실행.
2. `uv.lock`을 수동으로 편집 — uv가 수동 편집 내용을 덮어씁니다.
3. 코드 리뷰에서 락 파일 변경 사항 무시 — 예상치 못한 의존성 변경이 빌드를 깨뜨릴 수 있습니다.

## 요약

| 명령어 | `uv.lock` 업데이트 | 일반적인 사용 |
|-----------------------|-------------------|-------------------------------------------|
| `uv lock` | ✅ 예 | 제약 조건 편집 후 |
| `uv lock --upgrade` | ✅ 예 | 최신 호환 버전으로 업그레이드 |
| `uv add <pkg>` | ✅ 예 | 의존성 추가 |
| `uv remove <pkg>` | ✅ 예 | 의존성 제거 |
| `uv sync` | ⚠️ 경우에 따라 | 로컬 개발; 락을 재생성할 수 있음 |
| `uv sync --frozen` | ❌ 아니요 | CI/CD, Docker, 재현 가능한 빌드 |

기억하세요: `uv.lock`은 업데이트하라는 명령을 실행할 때만 변경됩니다. 프로젝트와 동기화 상태를 유지하고 변경될 때마다 커밋하세요.
