# LLM Cache Migration Tool - 사용자 가이드

## 개요

이 도구는 LightRAG의 LLM 응답 캐시를 서로 다른 KV 스토리지 구현 간에 마이그레이션합니다. 특히 파일 추출(모드 `default`) 중에 생성된 캐시, 즉 엔티티 추출 및 요약 캐시를 마이그레이션합니다.

## 지원되는 스토리지 유형

1. **JsonKVStorage** - 파일 기반 JSON 스토리지
2. **RedisKVStorage** - Redis 데이터베이스 스토리지
3. **PGKVStorage** - PostgreSQL 데이터베이스 스토리지
4. **MongoKVStorage** - MongoDB 데이터베이스 스토리지
5. **OpenSearchKVStorage** - OpenSearch 인덱스 스토리지

## 캐시 유형

이 도구는 다음 캐시 유형을 마이그레이션합니다:
- `default:extract:*` - 엔티티 및 관계 추출 캐시
- `default:summary:*` - 엔티티 및 관계 요약 캐시

**참고**: 쿼리 캐시(`mix`, `local`, `global` 등의 모드)는 마이그레이션되지 않습니다.

## 사전 준비 사항

LLM Cache Migration Tool은 LightRAG Server의 스토리지 설정을 읽고, 원본 및 대상 스토리지를 선택할 수 있는 LLM 마이그레이션 옵션을 제공합니다. 캐시 마이그레이션 전에 원본과 대상 스토리지가 모두 올바르게 구성되어 있고 LightRAG Server를 통해 접근 가능한지 확인하세요.

## 사용법

### 기본 사용법

LightRAG 프로젝트 루트 디렉터리에서 실행하세요:

```bash
python -m lightrag.tools.migrate_llm_cache
# or
python lightrag/tools/migrate_llm_cache.py
```

### 대화형 워크플로우

이 도구는 다음 단계를 안내합니다:

#### 1. 원본 스토리지 유형 선택
```
Supported KV Storage Types:
[1] JsonKVStorage
[2] RedisKVStorage
[3] PGKVStorage
[4] MongoKVStorage
[5] OpenSearchKVStorage

Select Source storage type (1-5) (Press Enter to exit): 1
```

**참고**: 모든 스토리지 선택 프롬프트에서 Enter를 누르거나 `0`을 입력하면 안전하게 종료할 수 있습니다.

#### 2. 원본 스토리지 검증
이 도구는 다음을 수행합니다:
- 필수 환경 변수 확인
- 워크스페이스 설정 자동 감지
- 스토리지 초기화 및 연결
- 마이그레이션 가능한 캐시 레코드 수 집계

```
Checking environment variables...
✓ All required environment variables are set

Initializing Source storage...
- Storage Type: JsonKVStorage
- Workspace: space1
- Connection Status: ✓ Success

Counting cache records...
- Total: 8,734 records
```

**스토리지 유형별 진행 상황 표시:**
- **JsonKVStorage**: 빠른 인메모리 집계, 단계별 진행 표시 없이 최종 개수만 표시
  ```
  Counting cache records...
  - Total: 8,734 records
  ```
- **RedisKVStorage**: 단계별 개수와 함께 실시간 스캔 진행 상황 표시
  ```
  Scanning Redis keys... found 8,734 records
  ```
- **PostgreSQL**: 빠른 COUNT(*) 쿼리, 작업에 1초 이상 걸릴 경우에만 소요 시간 표시
  ```
  Counting PostgreSQL records... (took 2.3s)
  ```
- **MongoDB**: 빠른 count_documents(), 작업에 1초 이상 걸릴 경우에만 소요 시간 표시
  ```
  Counting MongoDB documents... (took 1.8s)
  ```
- **OpenSearchKVStorage**: PIT 기반 스캔, 시간이 눈에 띄게 걸릴 경우 소요 시간 표시
  ```
  Scanning OpenSearch documents... (took 1.5s)
  ```

#### 3. 대상 스토리지 유형 선택

이 도구는 원본 스토리지 유형을 대상 선택 목록에서 자동으로 제외하고, 남은 옵션의 번호를 순차적으로 다시 매깁니다:

```
Available Storage Types for Target (source: JsonKVStorage excluded):
[1] RedisKVStorage
[2] PGKVStorage
[3] MongoKVStorage
[4] OpenSearchKVStorage

Select Target storage type (1-4) (Press Enter or 0 to exit): 1
```

**중요 참고 사항:**
- 원본과 대상에 **동일한** 스토리지 유형을 선택할 수 **없습니다**
- 옵션은 자동으로 다시 번호가 매겨집니다(예: [2], [3], [4] 대신 [1], [2], [3])
- 이 단계에서도 Enter를 누르거나 `0`을 입력하여 종료할 수 있습니다

이후 이 도구는 원본과 동일한 절차(환경 변수 확인, 연결 초기화, 레코드 수 집계)로 대상 스토리지를 검증합니다.

#### 4. 마이그레이션 확인

```
==================================================
Migration Confirmation
Source: JsonKVStorage (workspace: space1) - 8,734 records
Target: MongoKVStorage (workspace: space1) - 0 records
Batch Size: 1,000 records/batch
Memory Mode: Streaming (memory-optimized)

⚠️  Warning: Target storage already has 0 records
Migration will overwrite records with the same keys

Continue? (y/n): y
```

#### 5. 마이그레이션 실행

이 도구는 메모리 효율을 위해 기본적으로 **스트리밍 마이그레이션**을 사용합니다. 마이그레이션 진행 상황을 확인해 보세요:

```
=== Starting Streaming Migration ===
💡 Memory-optimized mode: Processing 1,000 records at a time

Batch 1/9: ████████░░░░░░░░░░░░ 1000/8734 (11.4%) - default:extract ✓
Batch 2/9: ████████████░░░░░░░░ 2000/8734 (22.9%) - default:extract ✓
...
Batch 9/9: ████████████████████ 8734/8734 (100.0%) - default:summary ✓

Persisting data to disk...
✓ Data persisted successfully
```

**주요 기능:**
- **스트리밍 모드**: 전체 데이터셋을 메모리에 로드하지 않고 배치 단위로 데이터를 처리
- **실시간 진행 상황**: 정확한 퍼센트와 캐시 유형이 표시되는 진행률 표시줄 제공
- **성공 표시**: 성공한 배치는 ✓, 실패한 배치는 ✗로 표시
- **일정한 메모리 사용량**: 수백만 개의 레코드도 효율적으로 처리

#### 6. 마이그레이션 보고서 검토

이 도구는 통계와 발생한 오류를 보여주는 포괄적인 최종 보고서를 제공합니다:

**마이그레이션 성공:**
```
Migration Complete - Final Report

📊 Statistics:
  Total source records:    8,734
  Total batches:           9
  Successful batches:      9
  Failed batches:          0
  Successfully migrated:   8,734
  Failed to migrate:       0
  Success rate:            100.00%

✓ SUCCESS: All records migrated successfully!
```

**오류가 발생한 마이그레이션:**
```
Migration Complete - Final Report

📊 Statistics:
  Total source records:    8,734
  Total batches:           9
  Successful batches:      8
  Failed batches:          1
  Successfully migrated:   7,734
  Failed to migrate:       1,000
  Success rate:            88.55%

⚠️  Errors encountered: 1

Error Details:
------------------------------------------------------------

Error Summary:
  - ConnectionError: 1 occurrence(s)

First 5 errors:

  1. Batch 2
     Type: ConnectionError
     Message: Connection timeout after 30s
     Records lost: 1,000

⚠️  WARNING: Migration completed with errors!
   Please review the error details above.
```

## 기술 세부 사항

### 워크스페이스 처리

이 도구는 다음 우선순위에 따라 워크스페이스를 가져옵니다:

1. **스토리지별 워크스페이스 환경 변수**
   - PGKVStorage: `POSTGRES_WORKSPACE`
   - MongoKVStorage: `MONGODB_WORKSPACE`
   - RedisKVStorage: `REDIS_WORKSPACE`
   - OpenSearchKVStorage: `OPENSEARCH_WORKSPACE`

2. **범용 워크스페이스 환경 변수**
   - `WORKSPACE`

3. **기본값**
   - 빈 문자열(스토리지의 기본 워크스페이스 사용)

### 배치 마이그레이션

- 기본 배치 크기: 1000 records/batch
- 한 번에 너무 많은 데이터를 로드하여 메모리가 넘치는 것을 방지
- 각 배치는 독립적으로 커밋되며, 재개(resume) 기능을 지원

### 메모리 효율적인 페이지네이션

대용량 데이터셋의 경우, 이 도구는 스토리지별 페이지네이션 전략을 구현합니다:

- **JsonKVStorage**: 직접 인메모리 접근(데이터가 공유 스토리지에 이미 로드되어 있음)
- **RedisKVStorage**: 파이프라인 배치 처리를 사용한 커서 기반 SCAN(1000 keys/batch)
- **PGKVStorage**: SQL LIMIT/OFFSET 페이지네이션(1000 records/batch)
- **MongoKVStorage**: batch_size를 사용한 커서 스트리밍(1000 documents/batch)
- **OpenSearchKVStorage**: KV 인덱스에 대한 PIT + `search_after` 스캔(1000 documents/batch)

이를 통해 이 도구는 메모리 문제 없이 수백만 개의 캐시 레코드를 처리할 수 있습니다.

### 접두사 필터링 구현

이 도구는 스토리지 유형별로 최적화된 필터링 방식을 사용합니다:

- **JsonKVStorage**: 잠금(lock) 보호가 적용된 직접 딕셔너리 순회
- **RedisKVStorage**: 네임스페이스 접두사가 붙은 패턴을 사용한 SCAN 명령 + 대량 GET을 위한 파이프라인
- **PGKVStorage**: 적절한 필드 매핑(id, return_value 등)이 적용된 SQL LIKE 쿼리
- **MongoKVStorage**: 커서 스트리밍을 사용한 `_id` 필드에 대한 MongoDB 정규식 쿼리
- **OpenSearchKVStorage**: `_id` 접두사 필터링과 `_source` 패스스루를 사용한 전체 인덱스 스캔

## 오류 처리 및 복원력

이 도구는 투명하고 복원력 있는 마이그레이션을 보장하기 위해 포괄적인 오류 추적을 구현합니다:

### 배치 단위 오류 추적
- 각 배치는 독립적으로 오류를 확인
- 실패한 배치는 로그로 기록되지만 마이그레이션을 중단시키지 않음
- 이후 배치가 실패하더라도 성공한 배치는 그대로 커밋됨
- 실시간 진행 상황에서 각 배치별로 ✓(성공) 또는 ✗(실패)를 표시

### 오류 보고
마이그레이션이 완료되면 다음을 포함하는 상세 보고서가 제공됩니다:
- **통계**: 전체 레코드 수, 성공/실패 개수, 성공률
- **오류 요약**: 오류 유형별로 그룹화한 발생 횟수
- **오류 세부 정보**: 배치 번호, 오류 유형, 메시지, 손실된 레코드 수
- **권장 사항**: 성공 여부 또는 검토 필요 여부에 대한 명확한 안내

### 데이터 이중 로드 없음
- 전통적인 검증 방식과 달리, 이 도구는 대상 데이터를 전부 다시 로드하지 **않습니다**
- 오류는 마이그레이션 이후가 아니라 마이그레이션 도중에 감지됨
- 이를 통해 메모리 오버헤드를 없애고 대상에 기존 데이터가 있는 경우도 올바르게 처리

## 중요 참고 사항

1. **데이터 덮어쓰기 경고**
   - 마이그레이션은 대상 스토리지에 동일한 키를 가진 레코드를 덮어씁니다
   - 대상 스토리지에 이미 데이터가 있는 경우 도구가 경고를 표시합니다
   - 데이터 마이그레이션은 반복적으로 수행할 수 있습니다
   - 대상 스토리지에 기존 데이터가 있어도 올바르게 처리됩니다
3. **중단 및 재개**
   - 마이그레이션은 언제든지 중단할 수 있습니다(Ctrl+C)
   - 이미 마이그레이션된 데이터는 대상 스토리지에 남아 있습니다
   - 다시 실행하면 기존 레코드를 덮어씁니다
   - 실패한 배치는 수동으로 재시도할 수 있습니다
4. **성능 고려 사항**
   - 대용량 데이터 마이그레이션은 상당한 시간이 걸릴 수 있습니다
   - 트래픽이 적은 시간대에 마이그레이션하는 것을 권장합니다
   - (원격 데이터베이스의 경우) 안정적인 네트워크 연결을 확보하세요
   - 메모리 사용량은 데이터셋 크기와 무관하게 일정하게 유지됩니다

## 스토리지 설정

이 도구는 다음과 같은 우선순위로 여러 설정 방식을 지원합니다:

1. **환경 변수**(가장 높은 우선순위)
2. **기본값**(가장 낮은 우선순위)

#### 옵션 A: 환경 변수 설정

`.env` 파일에 스토리지 설정을 구성하세요:

#### 워크스페이스 설정(선택 사항)

```bash
# Generic workspace (shared by all storages)
WORKSPACE=space1

# Or configure independent workspace for specific storage
POSTGRES_WORKSPACE=pg_space
MONGODB_WORKSPACE=mongo_space
REDIS_WORKSPACE=redis_space
OPENSEARCH_WORKSPACE=os_space
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
OPENSEARCH_WORKSPACE=os_space
```

환경 변수가 제공되지 않으면 이 도구는 가능한 경우 내장된 기본값을 사용합니다. JsonKVStorage는 `WORKING_DIR`을 사용하거나, 없으면 `./rag_storage`를 기본값으로 사용합니다.

## 문제 해결

### 환경 변수 누락
```
✗ Missing required environment variables: POSTGRES_USER, POSTGRES_PASSWORD
```
**해결 방법**: `.env` 파일에 누락된 변수를 추가하세요

### 연결 실패
```
✗ Initialization failed: Connection refused
```
**해결 방법**:
- 데이터베이스 서비스가 실행 중인지 확인
- 연결 매개변수(호스트, 포트, 자격 증명) 확인
- 방화벽 설정 확인

**해결 방법**:
- 오류 로그가 있는지 마이그레이션 프로세스를 확인
- 마이그레이션 도구를 다시 실행
- 대상 스토리지의 용량과 권한을 확인

## 예시 시나리오

### 시나리오 1: JSON에서 MongoDB로 마이그레이션

사용 사례: 단일 머신 개발 환경에서 프로덕션 환경으로 마이그레이션

```bash
# 1. Configure environment variables
WORKSPACE=production
MONGO_URI=mongodb://user:pass@prod-server:27017/
MONGO_DATABASE=LightRAG

# 2. Run tool
python -m lightrag.tools.migrate_llm_cache

# 3. Select: 1 (JsonKVStorage) -> 1 (MongoKVStorage - renumbered from 4)
```

**참고**: JsonKVStorage를 원본으로 선택하면, 원본을 제외한 후 옵션 번호가 다시 매겨지므로 대상 선택 목록에서 MongoKVStorage가 옵션 [1]로 표시됩니다.

### 시나리오 2: Redis에서 PostgreSQL로 마이그레이션

사용 사례: 캐시 스토리지에서 관계형 데이터베이스로 마이그레이션

```bash
# 1. Ensure both databases are accessible
REDIS_URI=redis://old-redis:6379
POSTGRES_HOST=new-postgres-server
# ... Other PostgreSQL configs

# 2. Run tool
python -m lightrag.tools.migrate_llm_cache

# 3. Select: 2 (RedisKVStorage) -> 2 (PGKVStorage - renumbered from 3)
```

**참고**: RedisKVStorage를 원본으로 선택하면, 대상 선택 목록에서 PGKVStorage가 옵션 [2]로 표시됩니다.

### 시나리오 3: 서로 다른 워크스페이스 간 마이그레이션

사용 사례: 서로 다른 워크스페이스 환경 간의 데이터 마이그레이션

```bash
# Configure separate workspaces for source and target
POSTGRES_WORKSPACE=dev_workspace  # For development environment
MONGODB_WORKSPACE=prod_workspace  # For production environment

# Run tool
python -m lightrag.tools.migrate_llm_cache

# Select: 3 (PGKVStorage with dev_workspace) -> 3 (MongoKVStorage with prod_workspace)
```

**참고**: 이를 통해 스토리지 백엔드를 변경하면서 서로 다른 논리적 데이터 파티션 간에 마이그레이션할 수 있습니다.

## 도구의 제한 사항

1. **동일한 스토리지 유형 간 마이그레이션 불가**
   - 동일한 스토리지 유형 간에는 마이그레이션할 수 없습니다(예: PostgreSQL에서 PostgreSQL로)
   - 이 도구가 대상 선택 목록에서 원본 스토리지 유형을 자동으로 제외함으로써 강제됩니다
   - 동일 스토리지 간 마이그레이션(예: 데이터베이스 전환)이 필요한 경우, 데이터베이스 자체의 네이티브 도구를 사용하세요
2. **기본(Default) 모드 캐시만 지원**
   - `default:extract:*` 및 `default:summary:*`만 마이그레이션합니다
   - 쿼리 캐시는 포함되지 않습니다
4. **네트워크 의존성**
   - 이 도구는 원격 데이터베이스에 대해 안정적인 네트워크 연결이 필요합니다
   - 대용량 데이터셋은 연결이 끊기면 실패할 수 있습니다

## 모범 사례

1. **마이그레이션 전 백업**
   - 마이그레이션 전에는 항상 데이터를 백업하세요
   - 먼저 프로덕션이 아닌 데이터로 마이그레이션을 테스트하세요

2. **결과 검증**
   - 마이그레이션 후 검증 출력을 확인하세요
   - 필요하다면 캐시 항목 몇 개를 수동으로 확인하세요

3. **성능 모니터링**
   - 마이그레이션 중 데이터베이스 리소스 사용량을 관찰하세요
   - 필요하다면 더 작은 배치로 마이그레이션하는 것을 고려하세요

4. **오래된 데이터 정리**
   - 마이그레이션이 성공한 후에는 이전 캐시 데이터를 정리하는 것을 고려하세요
   - 삭제하기 전 적절한 기간 동안 백업을 보관하세요
