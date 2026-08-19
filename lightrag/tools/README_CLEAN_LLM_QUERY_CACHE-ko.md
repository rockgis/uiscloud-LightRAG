# LLM Query Cache Cleanup Tool - 사용자 가이드

## 개요

이 도구는 KV 스토리지 구현체에서 LightRAG의 LLM 쿼리 캐시를 정리합니다. RAG 쿼리 작업(모드: `mix`, `hybrid`, `local`, `global`) 중에 생성된 쿼리 캐시를 대상으로 하며, query 캐시와 keywords 캐시를 모두 포함합니다.

## 지원되는 스토리지 유형

1. **JsonKVStorage** - 파일 기반 JSON 스토리지
2. **RedisKVStorage** - Redis 데이터베이스 스토리지
3. **PGKVStorage** - PostgreSQL 데이터베이스 스토리지
4. **MongoKVStorage** - MongoDB 데이터베이스 스토리지
5. **OpenSearchKVStorage** - OpenSearch 인덱스 스토리지

## 캐시 유형

이 도구는 다음과 같은 쿼리 캐시 유형을 정리합니다.

### 쿼리 캐시 모드 (4가지 유형)
- `mix:*` - Mixed 모드 쿼리 캐시
- `hybrid:*` - Hybrid 모드 쿼리 캐시
- `local:*` - Local 모드 쿼리 캐시
- `global:*` - Global 모드 쿼리 캐시

### 캐시 콘텐츠 유형 (2가지 유형)
- `*:query:*` - 쿼리 결과 캐시
- `*:keywords:*` - 키워드 추출 캐시

### 캐시 키 형식
```
<mode>:<cache_type>:<hash>
```

예시:
- `mix:query:5ce04d25e957c290216cee5bfe6344fa`
- `mix:keywords:fee77b98244a0b047ce95e21060de60e`
- `global:query:abc123def456...`
- `local:keywords:789xyz...`

**중요 참고 사항**: 이 도구는 추출 캐시(`default:extract:*` 및 `default:summary:*`)를 정리하지 않습니다. 해당 캐시에는 마이그레이션 도구를 사용하거나 수동으로 삭제하세요.

## 사전 요구 사항

- 이 도구는 환경 변수에서 스토리지 설정을 읽어옵니다
- 대상 스토리지가 올바르게 설정되어 접근 가능한지 확인하세요
- 정리 작업을 실행하기 전에 중요한 데이터를 백업하세요

## 사용법

### 기본 사용법

LightRAG 프로젝트 루트 디렉터리에서 실행하세요.

```bash
python -m lightrag.tools.clean_llm_query_cache
# 또는
python lightrag/tools/clean_llm_query_cache.py
```

### 대화형 워크플로우

이 도구는 다음 단계를 통해 안내합니다.

#### 1. 스토리지 유형 선택
```
============================================================
LLM Query Cache Cleanup Tool - LightRAG
============================================================

=== Storage Setup ===

Supported KV Storage Types:
[1] JsonKVStorage
[2] RedisKVStorage
[3] PGKVStorage
[4] MongoKVStorage
[5] OpenSearchKVStorage

Select storage type (1-5) (Press Enter to exit): 1
```

**참고**: 어느 프롬프트에서든 Enter 키를 누르거나 `0`을 입력하면 정상적으로 종료할 수 있습니다.

#### 2. 스토리지 검증
이 도구는 다음을 수행합니다.
- 필수 환경 변수 확인
- 워크스페이스 설정 자동 감지
- 스토리지 초기화 및 연결
- 연결 상태 확인

```
Checking configuration...
✓ All required environment variables are set

Initializing storage...
- Storage Type: JsonKVStorage
- Workspace: space1
- Connection Status: ✓ Success
```

#### 3. 캐시 통계 확인

이 도구는 모드 및 유형별 쿼리 캐시의 상세 분석을 표시합니다.

```
Counting query cache records...

📊 Query Cache Statistics (Before Cleanup):
┌────────────┬────────────┬────────────┬────────────┐
│ Mode       │ Query      │ Keywords   │ Total      │
├────────────┼────────────┼────────────┼────────────┤
│ mix        │      1,234 │        567 │      1,801 │
│ hybrid     │        890 │        423 │      1,313 │
│ local      │      2,345 │      1,123 │      3,468 │
│ global     │        678 │        345 │      1,023 │
├────────────┼────────────┼────────────┼────────────┤
│ Total      │      5,147 │      2,458 │      7,605 │
└────────────┴────────────┴────────────┴────────────┘
```

#### 4. 정리 범위 선택

삭제할 캐시 유형을 선택합니다.

```
=== Cleanup Options ===
[1] Delete all query caches (both query and keywords)
[2] Delete query caches only (keep keywords)
[3] Delete keywords caches only (keep query)
[0] Cancel

Select cleanup option (0-3): 1
```

**정리 유형:**
- **옵션 1 (all)**: 모든 모드에 걸쳐 query와 keywords 캐시를 모두 삭제
- **옵션 2 (query)**: query 캐시만 삭제하고 keywords 캐시는 보존
- **옵션 3 (keywords)**: keywords 캐시만 삭제하고 query 캐시는 보존

#### 5. 삭제 확인

정리 계획을 검토하고 확인합니다.

```
============================================================
Cleanup Confirmation
============================================================
Storage: JsonKVStorage (workspace: space1)
Cleanup Type: all
Records to Delete: 7,605 / 7,605

⚠️  WARNING: This will delete ALL query caches across all modes!

Continue with deletion? (y/n): y
```

#### 6. 정리 실행

이 도구는 실시간 진행 상황과 함께 배치 삭제를 수행합니다.

**JsonKVStorage 예시:**
```
=== Starting Cleanup ===
💡 Processing 1,000 records at a time from JsonKVStorage

Batch 1/8: ████░░░░░░░░░░░░░░░░ 1,000/7,605 (13.1%) ✓
Batch 2/8: ████████░░░░░░░░░░░░ 2,000/7,605 (26.3%) ✓
...
Batch 8/8: ████████████████████ 7,605/7,605 (100.0%) ✓

Persisting changes to storage...
✓ Changes persisted successfully
```

**RedisKVStorage 예시:**
```
=== Starting Cleanup ===
💡 Processing Redis keys in batches of 1,000

Batch 1: Deleted 1,000 keys (Total: 1,000) ✓
Batch 2: Deleted 1,000 keys (Total: 2,000) ✓
...
```

**PostgreSQL 예시:**
```
=== Starting Cleanup ===
💡 Executing PostgreSQL DELETE query

✓ Deleted 7,605 records in 0.45s
```

**MongoDB 예시:**
```
=== Starting Cleanup ===
💡 Executing MongoDB deleteMany operations

Pattern 1/8: Deleted 1,234 records ✓
Pattern 2/8: Deleted 567 records ✓
...
Total deleted: 7,605 records
```

**OpenSearchKVStorage 예시:**
```
=== Starting Cleanup ===
💡 Processing 1,000 records at a time from OpenSearchKVStorage

Batch 1/8: ████░░░░░░░░░░░░░░░░ 1,000/7,605 (13.1%) ✓
Batch 2/8: ████████░░░░░░░░░░░░ 2,000/7,605 (26.3%) ✓
...
```

#### 7. 정리 보고서 확인

이 도구는 종합적인 최종 보고서를 제공합니다.

**정리 성공:**
```
============================================================
Cleanup Complete - Final Report
============================================================

📊 Statistics:
  Total records to delete:  7,605
  Total batches:            8
  Successful batches:       8
  Failed batches:           0
  Successfully deleted:     7,605
  Failed to delete:         0
  Success rate:             100.00%

📈 Before/After Comparison:
  Total caches before:      7,605
  Total caches after:       0
  Net reduction:            7,605

============================================================
✓ SUCCESS: All records cleaned up successfully!
============================================================

📊 Query Cache Statistics (After Cleanup):
┌────────────┬────────────┬────────────┬────────────┐
│ Mode       │ Query      │ Keywords   │ Total      │
├────────────┼────────────┼────────────┼────────────┤
│ mix        │          0 │          0 │          0 │
│ hybrid     │          0 │          0 │          0 │
│ local      │          0 │          0 │          0 │
│ global     │          0 │          0 │          0 │
├────────────┼────────────┼────────────┼────────────┤
│ Total      │          0 │          0 │          0 │
└────────────┴────────────┴────────────┴────────────┘
```

**오류가 발생한 정리:**
```
============================================================
Cleanup Complete - Final Report
============================================================

📊 Statistics:
  Total records to delete:  7,605
  Total batches:            8
  Successful batches:       7
  Failed batches:           1
  Successfully deleted:     6,605
  Failed to delete:         1,000
  Success rate:             86.85%

📈 Before/After Comparison:
  Total caches before:      7,605
  Total caches after:       1,000
  Net reduction:            6,605

⚠️  Errors encountered: 1

Error Details:
------------------------------------------------------------

Error Summary:
  - ConnectionError: 1 occurrence(s)

First 5 errors:

  1. Batch 3
     Type: ConnectionError
     Message: Connection timeout after 30s
     Records lost: 1,000

============================================================
⚠️  WARNING: Cleanup completed with errors!
   Please review the error details above.
============================================================
```

## 기술 세부 사항

### 워크스페이스 처리

이 도구는 다음 우선순위 순서로 워크스페이스를 조회합니다.

1. **스토리지별 워크스페이스 환경 변수**
   - PGKVStorage: `POSTGRES_WORKSPACE`
   - MongoKVStorage: `MONGODB_WORKSPACE`
   - RedisKVStorage: `REDIS_WORKSPACE`
   - OpenSearchKVStorage: `OPENSEARCH_WORKSPACE`

2. **범용 워크스페이스 환경 변수**
   - `WORKSPACE`

3. **기본값**
   - 빈 문자열(스토리지의 기본 워크스페이스 사용)

### 배치 삭제

- 기본 배치 크기: 배치당 1000개 레코드
- 메모리 오버플로 및 연결 타임아웃 방지
- 각 배치는 독립적으로 처리됨
- 실패한 배치는 로그로 기록되지만 정리 작업을 중단시키지 않음

### 스토리지별 삭제 전략

#### JsonKVStorage
- 일치하는 모든 키를 먼저 수집(스냅샷 방식)
- 락 보호와 함께 배치 단위로 삭제
- 빠른 인메모리 연산

#### RedisKVStorage
- 패턴 매칭과 함께 SCAN 사용
- 배치 작업을 위한 파이프라인 DELETE
- 대용량 데이터셋을 위한 커서 기반 반복

#### PostgreSQL
- OR 조건이 포함된 단일 DELETE 쿼리
- 효율적인 서버 측 대량 삭제
- 모드/유형 매칭에 LIKE 패턴 사용

#### MongoDB
- 여러 deleteMany 작업(패턴당 하나씩)
- 정규식 기반 문서 매칭
- 정확한 삭제 개수 반환

### 패턴 매칭 구현

**JsonKVStorage:**
```python
# Direct key prefix matching
if key.startswith("mix:query:") or key.startswith("mix:keywords:")
```

**RedisKVStorage:**
```python
# SCAN with namespace-prefixed patterns
pattern = f"{namespace}:mix:query:*"
cursor, keys = await redis.scan(cursor, match=pattern)
```

**PostgreSQL:**
```python
# SQL LIKE conditions
WHERE id LIKE 'mix:query:%' OR id LIKE 'mix:keywords:%'
```

**MongoDB:**
```python
# Regex queries on _id field
{"_id": {"$regex": "^mix:query:"}}
```

**OpenSearchKVStorage:**
```python
# Scan raw hits, then match cache key prefixes in Python
if hit["_id"].startswith("mix:query:"):
```

## 오류 처리 및 복원력

이 도구는 종합적인 오류 추적을 구현합니다.

### 배치 수준 오류 추적
- 각 배치는 독립적으로 오류를 확인
- 실패한 배치는 전체 세부 정보와 함께 로그로 기록
- 이후 배치가 실패하더라도 성공한 배치는 커밋됨
- 실시간 진행 상황에 ✓(성공) 또는 ✗(실패)가 표시됨

### 오류 보고
정리 작업이 완료되면 상세 보고서에 다음 내용이 포함됩니다.
- **통계**: 총 레코드 수, 성공/실패 개수, 성공률
- **정리 전/후 비교**: 캐시 개수의 순감소량
- **오류 요약**: 오류 유형별 발생 횟수 그룹화
- **오류 세부 정보**: 배치 번호, 오류 유형, 메시지, 손실된 레코드 수
- **권장 사항**: 성공 여부 또는 검토 필요 여부를 명확히 표시

### 검증
- 정리 후 개수 검증
- 정리 전/후 통계 비교
- 부분 정리 시나리오 식별

## 중요 참고 사항

1. **되돌릴 수 없는 작업**
   - 삭제된 캐시는 복구할 수 없습니다
   - 정리 작업 전에 항상 중요한 데이터를 백업하세요
   - 먼저 비운영 데이터에서 테스트하세요

2. **성능 영향**
   - 정리 후 일시적으로 쿼리 성능이 저하될 수 있습니다
   - 캐시는 이후 쿼리 시 다시 구축됩니다
   - 트래픽이 적은 시간대에 정리 작업을 고려하세요

3. **선택적 정리**
   - 정리 범위를 신중하게 선택하세요
   - keywords 캐시는 향후 쿼리에 유용할 수 있습니다
   - query 캐시는 keywords 캐시보다 빠르게 재구축됩니다

4. **워크스페이스 격리**
   - 정리는 선택한 워크스페이스에만 영향을 줍니다
   - 다른 워크스페이스는 영향을 받지 않습니다
   - 확인 전에 워크스페이스를 검증하세요

5. **중단 및 재개**
   - 정리 작업은 언제든지 중단할 수 있습니다(Ctrl+C)
   - 이미 삭제된 레코드는 복구할 수 없습니다
   - 자동 재개 기능이 없으므로 도구를 다시 실행해야 합니다

## 스토리지 설정

이 도구는 다음 우선순위에 따라 여러 설정 방법을 지원합니다.

1. **환경 변수** (최우선)
2. **기본값** (최하위 우선순위)

### 환경 변수 설정

`.env` 파일에서 스토리지 설정을 구성하세요.

#### 워크스페이스 설정 (선택 사항)

```bash
# Generic workspace (shared by all storages)
WORKSPACE=space1

# Or configure independent workspace for specific storage
POSTGRES_WORKSPACE=pg_space
MONGODB_WORKSPACE=mongo_space
REDIS_WORKSPACE=redis_space
```

**워크스페이스 우선순위**: 스토리지별 설정 > 범용 WORKSPACE > 빈 문자열

#### JsonKVStorage

```bash
WORKING_DIR=./rag_storage
```

#### RedisKVStorage

```bash
REDIS_URI=redis://localhost:6379
```

#### PGKVStorage

```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=your_username
POSTGRES_PASSWORD=your_password
POSTGRES_DATABASE=your_database
```

#### MongoKVStorage

```bash
MONGO_URI=mongodb://root:root@localhost:27017/
MONGO_DATABASE=LightRAG
```

#### OpenSearchKVStorage

```bash
OPENSEARCH_HOSTS=localhost:9200
OPENSEARCH_WORKSPACE=search_space
```

환경 변수가 제공되지 않으면 이 도구는 사용 가능한 경우 내장된 기본값을 사용합니다.

## 문제 해결

### 환경 변수 누락
```
⚠️  Warning: Missing environment variables: POSTGRES_USER, POSTGRES_PASSWORD
```
**해결 방법**: `.env` 파일에 누락된 변수를 추가하세요

### 연결 실패
```
✗ Initialization failed: Connection refused
```
**해결 방법**:
- 데이터베이스 서비스가 실행 중인지 확인하세요
- 연결 매개변수(호스트, 포트, 자격 증명)를 확인하세요
- 방화벽 설정을 확인하세요
- 원격 데이터베이스의 경우 네트워크 연결을 확인하세요

### 캐시를 찾을 수 없음
```
⚠️  No query caches found in storage
```
**가능한 원인**:
- 아직 쿼리가 실행되지 않았습니다
- 캐시가 이미 정리되었습니다
- 워크스페이스를 잘못 선택했습니다
- 쿼리에 다른 스토리지 유형이 사용되었습니다

### 부분 정리
```
⚠️  WARNING: Cleanup completed with errors!
```
**해결 방법**:
- 보고서의 오류 세부 정보를 확인하세요
- 스토리지 연결 안정성을 확인하세요
- 남은 캐시를 정리하기 위해 도구를 다시 실행하세요
- 스토리지 용량과 권한을 확인하세요

## 사용 사례

### 사용 사례 1: 모든 쿼리 캐시 정리

**시나리오**: 모든 쿼리 캐시를 제거하여 스토리지 공간 확보

```bash
# Run tool
python -m lightrag.tools.clean_llm_query_cache

# Select: Storage type -> Option 1 (all) -> Confirm (y)
```

**결과**: query 및 keywords 캐시가 모두 삭제되어 최대한의 스토리지 공간이 확보됨

### 사용 사례 2: 쿼리 캐시만 새로 고침

**시나리오**: keywords는 유지하면서 쿼리 캐시 재구축을 강제 실행

```bash
# Run tool
python -m lightrag.tools.clean_llm_query_cache

# Select: Storage type -> Option 2 (query only) -> Confirm (y)
```

**결과**: query 캐시가 삭제되고, keywords는 더 빠른 재구축을 위해 보존됨

### 사용 사례 3: 오래된 keywords 정리

**시나리오**: 최근 쿼리 결과는 유지하면서 오래된 keywords 제거

```bash
# Run tool
python -m lightrag.tools.clean_llm_query_cache

# Select: Storage type -> Option 3 (keywords only) -> Confirm (y)
```

**결과**: keywords가 삭제되고, query 캐시는 보존됨

### 사용 사례 4: 워크스페이스별 정리

**시나리오**: 특정 워크스페이스의 캐시 정리

```bash
# Configure workspace
export WORKSPACE=development

# Run tool
python -m lightrag.tools.clean_llm_query_cache

# Select: Storage type -> Cleanup option -> Confirm (y)
```

**결과**: development 워크스페이스의 캐시만 정리됨

## 모범 사례

1. **정리 전 백업**
   - 대규모 정리 작업 전에는 항상 스토리지를 백업하세요
   - 먼저 비운영 데이터에서 정리 작업을 테스트하세요
   - 정리 결정 사항을 문서화하세요

2. **성능 모니터링**
   - 정리 작업 중 스토리지 지표를 관찰하세요
   - 정리 후 쿼리 성능을 모니터링하세요
   - 캐시가 재구축될 시간을 허용하세요

3. **예약 정리**
   - 주기적으로(주간/월간) 캐시를 정리하세요
   - 개발 환경에서는 정리 작업을 자동화하세요
   - 안전을 위해 운영 환경의 정리는 수동으로 유지하세요

4. **선택적 삭제**
   - 필요에 따라 정리 범위를 고려하세요
   - keywords 캐시는 재구축하기 더 어렵습니다
   - query 캐시는 자동으로 재구축됩니다

5. **스토리지 용량**
   - 스토리지 사용량 추이를 모니터링하세요
   - 용량 한도에 도달하기 전에 캐시를 정리하세요
   - 필요한 경우 오래된 데이터를 아카이브하세요

## 마이그레이션 도구와의 비교

| Feature | Cleanup Tool | Migration Tool |
|---------|-------------|----------------|
| **목적** | 쿼리 캐시 삭제 | 추출 캐시 마이그레이션 |
| **캐시 유형** | mix/hybrid/local/global | default:extract/summary |
| **모드** | query, keywords | extract, summary |
| **작업** | 삭제 | 스토리지 간 복사 |
| **되돌리기 가능 여부** | 불가능 | 가능(원본 데이터 유지) |
| **사용 사례** | 스토리지 확보, 캐시 새로 고침 | 스토리지 백엔드 변경 |

## 제한 사항

1. **단일 스토리지 작업**
   - 한 번에 하나의 스토리지 유형만 정리 가능
   - 여러 스토리지를 정리하려면 도구를 여러 번 실행해야 함

2. **드라이 런 모드 없음**
   - 확인 후 즉시 삭제가 진행됨
   - 미리보기 전용 모드가 없음
   - 먼저 비운영 환경에서 테스트해야 함

3. **모드별 선택 정리 불가**
   - 특정 모드(예: `mix`만)만 정리할 수 없음
   - 정리는 선택한 캐시 유형의 모든 모드에 적용됨
   - 캐시 유형별로 전부 삭제하거나 전혀 삭제하지 않음

4. **예약 정리 없음**
   - 수동 실행이 필요함
   - 내장된 스케줄링 기능이 없음
   - 자동화가 필요하면 cron/스케줄러를 사용해야 함

5. **검증 제한**
   - 오류 시나리오에서는 정리 후 검증이 실패할 수 있음
   - 중요한 작업에는 수동 검증을 권장함

## 향후 개선 사항

향후 버전을 위한 잠재적 개선 사항:

- 모드별 선택 정리(예: `mix` 모드만 정리)
- 기간 기반 정리(X일 이상 지난 캐시 삭제)
- 크기 기반 정리(가장 큰 캐시부터 삭제)
- 안전한 미리보기를 위한 드라이 런 모드
- 자동 스케줄링 지원
- 캐시 통계 내보내기
- 일시 정지/재개가 가능한 점진적 정리

## 지원

문제, 질문 또는 기능 요청이 있는 경우:
- 정리 보고서의 오류 세부 정보를 확인하세요
- 스토리지 설정을 검토하세요
- 워크스페이스 설정을 확인하세요
- 먼저 소규모 데이터셋으로 테스트하세요
- 프로젝트 이슈 트래커를 통해 버그를 신고하세요
