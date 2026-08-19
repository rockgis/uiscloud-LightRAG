# Workspace Isolation Test Suite

## 개요
LightRAG의 workspace isolation 기능에 대한 포괄적인 테스트 커버리지로, 서로 다른 workspace(프로젝트)가 데이터 오염이나 리소스 충돌 없이 독립적으로 공존할 수 있음을 보장합니다.

## 테스트 아키텍처

### 설계 원칙
1. **동시성 기반 검증(Concurrency-Based Assertions)**: 타이밍 기반 테스트(불안정할 수 있음) 대신, 실제 동시 lock 보유자 수를 측정합니다
2. **타임라인 검증(Timeline Validation)**: 유한 상태 머신(finite state machine)이 올바른 순차 실행을 검증합니다
3. **성능 지표(Performance Metrics)**: 각 테스트는 디버깅과 최적화를 위해 실행 지표를 리포트합니다
4. **구성 가능한 스트레스 테스트(Configurable Stress Testing)**: 환경 변수로 테스트 강도를 제어합니다

## 테스트 카테고리

### 1. 데이터 격리 테스트
**테스트:** 1, 4, 8, 9, 10
**목적:** 한 workspace의 데이터가 다른 workspace로 누출되지 않는지 확인

- **Test 1: Pipeline Status Isolation** - 핵심 공유 데이터 구조가 서로 분리되어 유지되는지 확인
- **Test 4: Multi-Workspace Concurrency** - 동시 작업이 서로 간섭하지 않는지 확인
- **Test 8: Update Flags Isolation** - 플래그 관리가 workspace 경계를 준수하는지 확인
- **Test 9: Empty Workspace Standardization** - 빈 workspace 문자열에 대한 엣지 케이스 처리
- **Test 10: JsonKVStorage Integration** - 스토리지 계층이 데이터를 올바르게 격리하는지 확인

### 2. Lock 메커니즘 테스트
**테스트:** 2, 5, 6
**목적:** lock 메커니즘이 workspace 간에는 병렬성을 허용하면서 동일 workspace 내에서는 직렬화를 강제하는지 검증

- **Test 2: Lock Mechanism** - 서로 다른 workspace는 병렬로 실행되고, 동일한 workspace는 직렬화됨
- **Test 5: Re-entrance Protection** - 재진입(re-entrant) lock 획득으로 인한 데드락 방지
- **Test 6: Namespace Lock Isolation** - 동일 workspace 내의 서로 다른 namespace가 독립적인지 확인

### 3. 하위 호환성 테스트
**테스트:** 3
**목적:** workspace 파라미터가 없는 레거시 코드도 여전히 올바르게 동작하는지 확인

- 기본 workspace 폴백 동작
- 빈 workspace 처리
- None과 빈 문자열의 정규화

### 4. 오류 처리 테스트
**테스트:** 7
**목적:** 잘못된 구성에 대한 안전장치(guardrail) 검증

- workspace 누락 검증
- workspace 정규화
- 엣지 케이스 처리

### 5. 엔드투엔드 통합 테스트
**테스트:** 11
**목적:** LightRAG의 전체 워크플로우가 격리 상태를 유지하는지 검증

- 전체 문서 삽입 파이프라인
- 파일 시스템 분리
- 데이터 콘텐츠 검증

## 테스트 실행

### 기본 사용법
```bash
# Run all workspace isolation tests
pytest tests/workspace/test_workspace_isolation.py -v

# Run specific test
pytest tests/workspace/test_workspace_isolation.py::test_lock_mechanism -v

# Run with detailed output
pytest tests/workspace/test_workspace_isolation.py -v -s
```

### 환경 설정

#### 스트레스 테스트
구성 가능한 워커 수로 스트레스 테스트를 활성화합니다:
```bash
# Enable stress mode with default 3 workers
LIGHTRAG_STRESS_TEST=true pytest tests/workspace/test_workspace_isolation.py -v

# Custom number of workers (e.g., 10)
LIGHTRAG_STRESS_TEST=true LIGHTRAG_TEST_WORKERS=10 pytest tests/workspace/test_workspace_isolation.py -v
```

#### 테스트 산출물 유지
수동 점검을 위해 임시 디렉터리를 보존합니다:
```bash
# Keep test artifacts (useful for debugging)
LIGHTRAG_KEEP_ARTIFACTS=true pytest tests/workspace/test_workspace_isolation.py -v
```

#### 조합 예시
```bash
# Stress test with 20 workers and keep artifacts
LIGHTRAG_STRESS_TEST=true \
LIGHTRAG_TEST_WORKERS=20 \
LIGHTRAG_KEEP_ARTIFACTS=true \
pytest tests/workspace/test_workspace_isolation.py::test_lock_mechanism -v -s
```

### CI/CD 통합
```bash
# Recommended CI/CD command (no artifacts, default workers)
pytest tests/workspace/test_workspace_isolation.py -v --tb=short
```

## 테스트 구현 세부 사항

### 헬퍼 함수

#### `_measure_lock_parallelism`
벽시계 시간(wall-clock time)이 아닌 실제 동시성을 측정합니다.

**반환값:**
- `max_parallel`: 동시 lock 보유자의 최대 수
- `timeline`: (task_name, event) 튜플의 순서가 지정된 목록
- `metrics`: 성능 데이터(duration, concurrency, workers)를 담은 Dict

**예시:**
```python
workload = [
    ("task1", "workspace1", "namespace"),
    ("task2", "workspace2", "namespace"),
]
max_parallel, timeline, metrics = await _measure_lock_parallelism(workload)

# Assert on actual behavior, not timing
assert max_parallel >= 2  # Two different workspaces should run concurrently
```

#### `_assert_no_timeline_overlap`
유한 상태 머신을 사용하여 순차 실행을 검증합니다.

**검증 항목:**
- lock 획득이 겹치지 않는지
- lock 해제 순서가 올바른지
- 모든 lock이 제대로 해제되었는지

**예시:**
```python
timeline = [
    ("task1", "start"),
    ("task1", "end"),
    ("task2", "start"),
    ("task2", "end"),
]
_assert_no_timeline_overlap(timeline)  # Passes - no overlap

timeline_bad = [
    ("task1", "start"),
    ("task2", "start"),  # ERROR: task2 started before task1 ended
    ("task1", "end"),
]
_assert_no_timeline_overlap(timeline_bad)  # Raises AssertionError
```

## 구성 변수

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `LIGHTRAG_STRESS_TEST` | bool | `false` | 스트레스 테스트 모드 활성화 |
| `LIGHTRAG_TEST_WORKERS` | int | `3` | 스트레스 모드에서의 병렬 워커 수 |
| `LIGHTRAG_KEEP_ARTIFACTS` | bool | `false` | 임시 테스트 디렉터리 유지 |

## 성능 벤치마크

### 예상 성능(참조 시스템 기준)
- **Test 1-9**: 각 1초 미만
- **Test 10**: 2초 미만(파일 I/O 포함)
- **Test 11**: 5초 미만(전체 RAG 파이프라인 포함)
- **전체 스위트**: 15초 미만

### 스트레스 테스트 성능
`LIGHTRAG_TEST_WORKERS=10`인 경우:
- **Test 2 (Parallel)**: 약 0.05초(워커 10개, 모두 동시 실행)
- **Test 2 (Serial)**: 약 0.10초(워커 2개, 직렬화됨)

## 문제 해결

### 일반적인 문제

#### 불안정한(Flaky) 테스트 실패
**증상:** 로컬에서는 통과하지만 CI/CD에서 실패
**원인:** 시스템 부하가 높거나 타이밍 기반 검증을 사용하는 경우
**해결책:** 저희 테스트는 타이밍이 아닌 동시성 기반 검증을 사용합니다. 실패가 계속되면 오류 메시지의 `timeline` 출력을 확인하세요.

#### 리소스 정리 오류
**증상:** "Directory not empty" 또는 "Cannot remove directory"
**원인:** 동시 테스트 실행 또는 OS 파일 잠금
**해결책:** 테스트를 순차적으로 실행하거나(`pytest -n 1`) `LIGHTRAG_KEEP_ARTIFACTS=true`를 사용해 상태를 점검하세요

#### Lock 타임아웃 오류
**증상:** "Lock acquisition timeout"
**원인:** 데드락 또는 리소스 부족(starvation)
**해결책:** 테스트 출력에서 데드락 패턴을 확인하고 lock 획득 순서를 검토하세요

### 디버그 팁

1. **상세 출력 활성화:**
   ```bash
   pytest tests/workspace/test_workspace_isolation.py -v -s
   ```

2. **산출물을 유지한 채 단일 테스트 실행:**
   ```bash
   LIGHTRAG_KEEP_ARTIFACTS=true pytest tests/workspace/test_workspace_isolation.py::test_json_kv_storage_workspace_isolation -v -s
   ```

3. **성능 지표 확인:**
   테스트 출력에서 duration과 concurrency를 보여주는 "Performance:" 라인을 확인하세요.

4. **실패 시 타임라인 점검:**
   타임라인 데이터는 assertion 오류 메시지에 포함됩니다.

## 기여

### 새 테스트 추가

1. **명명 규칙 준수:** `test_<feature>_<aspect>`
2. **목적/범위 주석 추가:** 무엇을, 왜 테스트하는지 설명
3. **헬퍼 함수 사용:** `_measure_lock_parallelism`, `_assert_no_timeline_overlap`
4. **검증 내용 문서화:** assertion에서 예상되는 동작을 설명
5. **이 README 업데이트:** 해당 카테고리에 테스트 추가

### 테스트 템플릿
```python
@pytest.mark.asyncio
async def test_new_feature():
    """
    Brief description of what this test validates.
    """
    # Purpose: Why this test exists
    # Scope: What functions/classes this tests
    print("\n" + "=" * 60)
    print("TEST N: Feature Name")
    print("=" * 60)

    # Test implementation
    # ...

    print("✅ PASSED: Feature Name")
    print(f"   Validation details")
```

## 관련 문서

- [Workspace Isolation Design Doc](../docs/LightRAG_concurrent_explain.md)
- [Project Intelligence](.clinerules/01-basic.md)
- [Memory Bank](../.memory-bank/)

## 테스트 커버리지 매트릭스

| Component | Data Isolation | Lock Mechanism | Backward Compat | Error Handling | E2E |
|-----------|:--------------:|:--------------:|:---------------:|:--------------:|:---:|
| shared_storage | ✅ T1, T4 | ✅ T2, T5, T6 | ✅ T3 | ✅ T7 | ✅ T11 |
| update_flags | ✅ T8 | - | - | - | - |
| JsonKVStorage | ✅ T10 | - | - | - | ✅ T11 |
| LightRAG Core | - | - | - | - | ✅ T11 |
| Namespace | ✅ T9 | - | ✅ T3 | ✅ T7 | - |

**범례:** T# = 테스트 번호

## 버전 이력

- **v2.0** (2025-01-18): 성능 지표, 스트레스 테스트, 구성 가능한 정리(cleanup) 기능 추가
- **v1.0** (초기 버전): 타이밍 기반 검증을 사용한 기본 workspace isolation 테스트
