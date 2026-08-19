# 📊 RAGAS 기반 평가 프레임워크

## RAGAS란?

**RAGAS**(Retrieval Augmented Generation Assessment)는 LLM을 사용하여 RAG 시스템을 참조 없이(reference-free) 평가하는 프레임워크입니다. RAGAS는 최신 평가 지표를 사용합니다:

### 핵심 지표

| 지표 | 측정 대상 | 우수한 점수 |
| ------ | ---------------- | ---------- |
| **Faithfulness (충실도)** | 검색된 컨텍스트를 기반으로 답변이 사실적으로 정확한가? | > 0.80 |
| **Answer Relevance (답변 관련성)** | 답변이 사용자의 질문과 관련이 있는가? | > 0.80 |
| **Context Recall (컨텍스트 재현율)** | 문서에서 관련 정보가 모두 검색되었는가? | > 0.80 |
| **Context Precision (컨텍스트 정밀도)** | 검색된 컨텍스트에 관련 없는 노이즈가 없이 깨끗한가? | > 0.80 |
| **RAGAS Score (RAGAS 점수)** | 전체 품질 지표(위 지표들의 평균) | > 0.80 |

### 📁 LightRAG 평가 프레임워크 디렉터리 구조

```
lightrag/evaluation/
├── eval_rag_quality.py      # Main evaluation script
├── sample_dataset.json        # 3 test questions about LightRAG
├── sample_documents/          # Matching markdown files for testing
│   ├── 01_lightrag_overview.md
│   ├── 02_rag_architecture.md
│   ├── 03_lightrag_improvements.md
│   ├── 04_supported_databases.md
│   ├── 05_evaluation_and_deployment.md
│   └── README.md
├── __init__.py              # Package init
├── results/                 # Output directory
│   ├── results_YYYYMMDD_HHMMSS.json    # Raw metrics in JSON
│   └── results_YYYYMMDD_HHMMSS.csv     # Metrics in CSV format
└── README.md                # This file
```

**빠른 테스트:** `sample_documents/`의 파일들을 LightRAG에 색인한 다음 평가기를 실행하면 결과를 재현할 수 있습니다(질문당 약 89-100% RAGAS 점수).



## 🚀 빠른 시작

### 1. 의존성 설치

```bash
pip install ragas datasets langfuse
```

또는 프로젝트 의존성을 사용하세요(pyproject.toml에 이미 포함되어 있음):

```bash
pip install -e ".[evaluation]"
```

### 2. 평가 실행

**선택적인 오프라인 샘플 검색 확인(API/모델 호출 없음):**
```bash
python lightrag/evaluation/offline_retrieval_check.py --strict
```

이 명령은 LightRAG, 임베딩, LLM 호출, RAGAS를 실행하기 전에 번들로 제공되는 샘플 질문이 기대하는 샘플 문서를 어휘적으로(lexically) 검색할 수 있는지 확인합니다.

**기본 사용법(기본값 사용):**
```bash
cd /path/to/LightRAG
python lightrag/evaluation/eval_rag_quality.py
```

**사용자 지정 데이터셋 지정:**
```bash
python lightrag/evaluation/eval_rag_quality.py --dataset my_test.json
```

**사용자 지정 RAG 엔드포인트 지정:**
```bash
python lightrag/evaluation/eval_rag_quality.py --ragendpoint http://my-server.com:9621
```

**둘 다 지정(축약형):**
```bash
python lightrag/evaluation/eval_rag_quality.py -d my_test.json -r http://localhost:9621
```

**도움말 보기:**
```bash
python lightrag/evaluation/eval_rag_quality.py --help
```

### 3. 결과 확인

결과는 `lightrag/evaluation/results/`에 자동으로 저장됩니다:

```
results/
├── results_20241023_143022.json     ← Raw metrics in JSON format
└── results_20241023_143022.csv      ← Metrics in CSV format (for spreadsheets)
```

**결과에 포함되는 내용:**
- ✅ 전체 RAGAS 점수
- 📊 지표별 평균(Faithfulness, Answer Relevance, Context Recall, Context Precision)
- 📋 개별 테스트 케이스 결과
- 📈 질문별 성능 분석



## 📋 명령줄 인수

평가 스크립트는 손쉬운 설정을 위해 명령줄 인수를 지원합니다:

| 인수 | 축약형 | 기본값 | 설명 |
| -------- | ----- | ------- | ----------- |
| `--dataset` | `-d` | `sample_dataset.json` | 테스트 데이터셋 JSON 파일 경로 |
| `--ragendpoint` | `-r` | `http://localhost:9621` 또는 `$LIGHTRAG_API_URL` | LightRAG API 엔드포인트 URL |

### 사용 예시

**기본 데이터셋과 엔드포인트 사용:**
```bash
python lightrag/evaluation/eval_rag_quality.py
```

**기본 엔드포인트에 사용자 지정 데이터셋 적용:**
```bash
python lightrag/evaluation/eval_rag_quality.py --dataset path/to/my_dataset.json
```

**기본 데이터셋에 사용자 지정 엔드포인트 적용:**
```bash
python lightrag/evaluation/eval_rag_quality.py --ragendpoint http://my-server.com:9621
```

**사용자 지정 데이터셋과 엔드포인트:**
```bash
python lightrag/evaluation/eval_rag_quality.py -d my_dataset.json -r http://localhost:9621
```

**데이터셋의 절대 경로:**
```bash
python lightrag/evaluation/eval_rag_quality.py -d /path/to/custom_dataset.json
```

**도움말 메시지 표시:**
```bash
python lightrag/evaluation/eval_rag_quality.py --help
```



## ⚙️ 설정

### 환경 변수

평가 프레임워크는 환경 변수를 통한 사용자 지정을 지원합니다:

**⚠️ 중요: LLM과 Embedding 엔드포인트는 반드시 OpenAI 호환이어야 합니다**
- RAGAS 프레임워크는 OpenAI 호환 API 인터페이스를 필요로 합니다
- 사용자 지정 엔드포인트는 OpenAI API 형식을 구현해야 합니다(예: vLLM, SGLang, LocalAI)
- 호환되지 않는 엔드포인트는 평가 실패를 유발합니다

| 변수 | 기본값 | 설명 |
| -------- | ------- | ----------- |
| **LLM 설정** | | |
| `EVAL_LLM_MODEL` | `gpt-4o-mini` | RAGAS 평가에 사용되는 LLM 모델 |
| `EVAL_LLM_BINDING_API_KEY` | `OPENAI_API_KEY`로 폴백 | LLM 평가용 API 키 |
| `EVAL_LLM_BINDING_HOST` | (선택 사항) | LLM용 사용자 지정 OpenAI 호환 엔드포인트 URL |
| **Embedding 설정** | | |
| `EVAL_EMBEDDING_MODEL` | `text-embedding-3-large` | 평가용 임베딩 모델 |
| `EVAL_EMBEDDING_BINDING_API_KEY` | `EVAL_LLM_BINDING_API_KEY` → `OPENAI_API_KEY` 순으로 폴백 | 임베딩용 API 키 |
| `EVAL_EMBEDDING_BINDING_HOST` | `EVAL_LLM_BINDING_HOST`로 폴백 | 임베딩용 사용자 지정 OpenAI 호환 엔드포인트 URL |
| **성능 튜닝** | | |
| `EVAL_MAX_CONCURRENT` | 2 | 동시 테스트 케이스 평가 수(1=순차 처리) |
| `EVAL_QUERY_TOP_K` | 10 | 쿼리당 검색할 문서 수 |
| `EVAL_LLM_MAX_RETRIES` | 5 | 최대 LLM 요청 재시도 횟수 |
| `EVAL_LLM_TIMEOUT` | 180 | LLM 요청 타임아웃(초) |

### 사용 예시

**예시 1: 기본 설정(OpenAI 공식 API)**
```bash
export OPENAI_API_KEY=sk-xxx
python lightrag/evaluation/eval_rag_quality.py
```
LLM과 임베딩 모두 기본 모델로 OpenAI 공식 API를 사용합니다.

**예시 2: OpenAI에서 사용자 지정 모델 사용**
```bash
export OPENAI_API_KEY=sk-xxx
export EVAL_LLM_MODEL=gpt-4o-mini
export EVAL_EMBEDDING_MODEL=text-embedding-3-large
python lightrag/evaluation/eval_rag_quality.py
```

**예시 3: LLM과 임베딩에 동일한 사용자 지정 OpenAI 호환 엔드포인트 사용**
```bash
# Both LLM and embeddings use the same custom endpoint
export EVAL_LLM_BINDING_API_KEY=your-custom-key
export EVAL_LLM_BINDING_HOST=http://localhost:8000/v1
export EVAL_LLM_MODEL=qwen-plus
export EVAL_EMBEDDING_MODEL=BAAI/bge-m3
python lightrag/evaluation/eval_rag_quality.py
```
임베딩은 LLM 엔드포인트 설정을 자동으로 상속받습니다.

**예시 4: 엔드포인트 분리(비용 최적화)**
```bash
# Use OpenAI for LLM (high quality)
export EVAL_LLM_BINDING_API_KEY=sk-openai-key
export EVAL_LLM_MODEL=gpt-4o-mini
# No EVAL_LLM_BINDING_HOST means use OpenAI official API

# Use local vLLM for embeddings (cost-effective)
export EVAL_EMBEDDING_BINDING_API_KEY=local-key
export EVAL_EMBEDDING_BINDING_HOST=http://localhost:8001/v1
export EVAL_EMBEDDING_MODEL=BAAI/bge-m3

python lightrag/evaluation/eval_rag_quality.py
```
LLM은 OpenAI 공식 API를 사용하고, 임베딩은 로컬 사용자 지정 엔드포인트를 사용합니다.

**예시 5: LLM과 임베딩에 서로 다른 사용자 지정 엔드포인트 사용**
```bash
# LLM on one OpenAI-compatible server
export EVAL_LLM_BINDING_API_KEY=key1
export EVAL_LLM_BINDING_HOST=http://llm-server:8000/v1
export EVAL_LLM_MODEL=custom-llm

# Embeddings on another OpenAI-compatible server
export EVAL_EMBEDDING_BINDING_API_KEY=key2
export EVAL_EMBEDDING_BINDING_HOST=http://embedding-server:8001/v1
export EVAL_EMBEDDING_MODEL=custom-embedding

python lightrag/evaluation/eval_rag_quality.py
```
LLM과 임베딩 모두 서로 다른 사용자 지정 OpenAI 호환 엔드포인트를 사용합니다.

**예시 6: .env 파일의 환경 변수 사용**
```bash
# Create .env file in project root
cat > .env << EOF
EVAL_LLM_BINDING_API_KEY=your-key
EVAL_LLM_BINDING_HOST=http://localhost:8000/v1
EVAL_LLM_MODEL=qwen-plus
EVAL_EMBEDDING_MODEL=BAAI/bge-m3
EOF

# Run evaluation (automatically loads .env)
python lightrag/evaluation/eval_rag_quality.py
```

### 동시성 제어 및 속도 제한

평가 프레임워크는 API 속도 제한 문제를 방지하기 위한 내장 동시성 제어 기능을 포함합니다:

**동시성 제어가 중요한 이유:**
- RAGAS는 각 테스트 케이스마다 내부적으로 많은 동시 LLM 호출을 수행합니다
- Context Precision 지표는 검색된 문서마다 한 번씩 LLM을 호출합니다
- 제어가 없으면 API 속도 제한을 쉽게 초과할 수 있습니다

**기본 설정(보수적):**
```bash
EVAL_MAX_CONCURRENT=2    # Serial evaluation (one test at a time)
EVAL_QUERY_TOP_K=10      # OP_K query parameter of LightRAG
EVAL_LLM_MAX_RETRIES=5   # Retry failed requests 5 times
EVAL_LLM_TIMEOUT=180     # 3-minute timeout per request
```

**일반적인 문제와 해결책:**

| 문제 | 해결책 |
| ----- | -------- |
| **경고: "LM returned 1 generations instead of 3"** | `EVAL_MAX_CONCURRENT`를 1로 낮추거나 `EVAL_QUERY_TOP_K`를 줄이세요 |
| **Context Precision이 NaN을 반환** | `EVAL_QUERY_TOP_K`를 낮춰 테스트 케이스당 LLM 호출 수를 줄이세요 |
| **속도 제한 오류(429)** | `EVAL_LLM_MAX_RETRIES`를 늘리고 `EVAL_MAX_CONCURRENT`를 줄이세요 |
| **요청 타임아웃** | `EVAL_LLM_TIMEOUT`을 180 이상으로 늘리세요 |



## 📝 테스트 데이터셋

`sample_dataset.json`에는 LightRAG에 관한 3개의 일반적인 질문이 포함되어 있습니다. 여러분이 색인한 문서에 맞는 질문으로 교체하세요.

**사용자 지정 테스트 케이스:**

```json
{
  "test_cases": [
    {
      "question": "Your question here",
      "ground_truth": "Expected answer from your data",
      "project": "evaluation_project_name"
    }
  ]
}
```

---

## 📊 결과 해석

### 점수 범위

- **0.80-1.00**: ✅ 우수(프로덕션 준비 완료)
- **0.60-0.80**: ⚠️ 양호(개선 여지 있음)
- **0.40-0.60**: ❌ 미흡(최적화 필요)
- **0.00-0.40**: 🔴 심각(주요 문제)

### 낮은 점수가 의미하는 것

| 지표 | 낮은 점수가 의미하는 것 |
| ------ | ------------------- |
| **Faithfulness** | 응답에 환각(hallucination) 또는 잘못된 정보가 포함됨 |
| **Answer Relevance** | 답변이 사용자가 질문한 내용과 일치하지 않음 |
| **Context Recall** | 검색 시 중요한 정보가 누락됨 |
| **Context Precision** | 검색된 문서에 관련 없는 노이즈가 포함됨 |

### 최적화 팁

1. **낮은 Faithfulness**:
   - 엔티티 추출 품질 개선
   - 문서 청킹 개선
   - 검색 온도(retrieval temperature) 조정

2. **낮은 Answer Relevance**:
   - 프롬프트 엔지니어링 개선
   - 쿼리 이해 능력 개선
   - 의미 유사도 임계값 확인

3. **낮은 Context Recall**:
   - 검색 `top_k` 결과 수 증가
   - 임베딩 모델 개선
   - 문서 전처리 개선

4. **낮은 Context Precision**:
   - 더 작고 집중된 청크 사용
   - 필터링 개선
   - 청킹 전략 개선

---

## 📚 참고 자료

- [RAGAS Documentation](https://docs.ragas.io/)
- [RAGAS GitHub](https://github.com/explodinggradients/ragas)

---

## 🐛 문제 해결

### "ModuleNotFoundError: No module named 'ragas'"

```bash
pip install ragas datasets
```

### "Warning: LM returned 1 generations instead of requested 3" 또는 Context Precision NaN

**원인**: 이 경고는 API 속도 제한 또는 동시 요청 과부하를 나타냅니다:
- RAGAS는 테스트 케이스마다 여러 번 LLM을 호출합니다(faithfulness, relevancy, recall, precision)
- Context Precision은 검색된 문서마다 한 번씩 LLM을 호출합니다(`EVAL_QUERY_TOP_K=10`이면 10회 호출)
- 동시 평가는 이러한 호출 수를 곱합니다: `EVAL_MAX_CONCURRENT × 테스트당 LLM 호출 수`

**해결책**(효과가 큰 순서대로):

1. **순차 평가**(기본값):
   ```bash
   export EVAL_MAX_CONCURRENT=1
   python lightrag/evaluation/eval_rag_quality.py
   ```

2. **검색 문서 수 줄이기**:
   ```bash
   export EVAL_QUERY_TOP_K=5  # Halves Context Precision LLM calls
   python lightrag/evaluation/eval_rag_quality.py
   ```

3. **재시도 및 타임아웃 늘리기**:
   ```bash
   export EVAL_LLM_MAX_RETRIES=10
   export EVAL_LLM_TIMEOUT=180
   python lightrag/evaluation/eval_rag_quality.py
   ```

4. **더 높은 할당량의 API 사용**(가능한 경우):
   - 더 높은 RPM 한도를 위해 OpenAI Tier 2+로 업그레이드
   - 속도 제한이 없는 자체 호스팅 OpenAI 호환 서비스 사용

### "AttributeError: 'InstructorLLM' object has no attribute 'agenerate_prompt'" 또는 NaN 결과

이 오류는 LLM과 Embeddings가 명시적으로 설정되지 않았을 때 RAGAS 0.3.x에서 발생합니다. 평가 프레임워크는 이제 다음을 통해 이 문제를 자동으로 처리합니다:
- 환경 변수를 사용하여 평가 모델 설정
- RAGAS용으로 적절한 LLM 및 Embeddings 인스턴스 생성

**해결책**: 다음 중 하나가 설정되어 있는지 확인하세요:
- `OPENAI_API_KEY` 환경 변수(기본값)
- 사용자 지정 API 키를 위한 `EVAL_LLM_BINDING_API_KEY`

프레임워크가 평가 모델을 자동으로 설정합니다.

### "No sample_dataset.json found"

프로젝트 루트에서 실행하고 있는지 확인하세요:

```bash
cd /path/to/LightRAG
python lightrag/evaluation/eval_rag_quality.py
```

### "LightRAG query API errors during evaluation"

평가는 사용자가 설정한 LLM(기본값은 OpenAI)을 사용합니다. 다음을 확인하세요:
- API 키가 `.env`에 설정되어 있는지
- 네트워크 연결이 안정적인지

### 평가를 위해서는 LightRAG API가 실행 중이어야 함

평가기는 `http://localhost:9621`에서 실행 중인 LightRAG API 서버에 쿼리합니다. 다음을 확인하세요:
1. LightRAG API 서버가 실행 중인지(`python lightrag/api/lightrag_server.py`)
2. 문서가 여러분의 LightRAG 인스턴스에 색인되어 있는지
3. API가 설정된 URL에서 접근 가능한지



## 📝 다음 단계

1. LightRAG API 서버 시작
2. WebUI를 통해 샘플 문서를 LightRAG에 업로드
3. `python lightrag/evaluation/eval_rag_quality.py` 실행
4. `results/` 폴더에서 결과(JSON/CSV) 확인

평가 결과 예시:

```
INFO: ======================================================================
INFO: 🔍 RAGAS Evaluation - Using Real LightRAG API
INFO: ======================================================================
INFO: Evaluation Models:
INFO:   • LLM Model:            gpt-4.1
INFO:   • Embedding Model:      text-embedding-3-large
INFO:   • Endpoint:             OpenAI Official API
INFO: Concurrency & Rate Limiting:
INFO:   • Query Top-K:          10 Entities/Relations
INFO:   • LLM Max Retries:      5
INFO:   • LLM Timeout:          180 seconds
INFO: Test Configuration:
INFO:   • Total Test Cases:     6
INFO:   • Test Dataset:         sample_dataset.json
INFO:   • LightRAG API:         http://localhost:9621
INFO:   • Results Directory:    results
INFO: ======================================================================
INFO: 🚀 Starting RAGAS Evaluation of LightRAG System
INFO: 🔧 RAGAS Evaluation (Stage 2): 2 concurrent
INFO: ======================================================================
INFO:
INFO: ===================================================================================================================
INFO: 📊 EVALUATION RESULTS SUMMARY
INFO: ===================================================================================================================
INFO: #    | Question                                           |  Faith | AnswRel | CtxRec | CtxPrec |  RAGAS | Status
INFO: -------------------------------------------------------------------------------------------------------------------
INFO: 1    | How does LightRAG solve the hallucination probl... | 1.0000 |  1.0000 | 1.0000 |  1.0000 | 1.0000 |      ✓
INFO: 2    | What are the three main components required in ... | 0.8500 |  0.5790 | 1.0000 |  1.0000 | 0.8573 |      ✓
INFO: 3    | How does LightRAG's retrieval performance compa... | 0.8056 |  1.0000 | 1.0000 |  1.0000 | 0.9514 |      ✓
INFO: 4    | What vector databases does LightRAG support and... | 0.8182 |  0.9807 | 1.0000 |  1.0000 | 0.9497 |      ✓
INFO: 5    | What are the four key metrics for evaluating RA... | 1.0000 |  0.7452 | 1.0000 |  1.0000 | 0.9363 |      ✓
INFO: 6    | What are the core benefits of LightRAG and how ... | 0.9583 |  0.8829 | 1.0000 |  1.0000 | 0.9603 |      ✓
INFO: ===================================================================================================================
INFO:
INFO: ======================================================================
INFO: 📊 EVALUATION COMPLETE
INFO: ======================================================================
INFO: Total Tests:    6
INFO: Successful:     6
INFO: Failed:         0
INFO: Success Rate:   100.00%
INFO: Elapsed Time:   161.10 seconds
INFO: Avg Time/Test:  26.85 seconds
INFO:
INFO: ======================================================================
INFO: 📈 BENCHMARK RESULTS (Average)
INFO: ======================================================================
INFO: Average Faithfulness:      0.9053
INFO: Average Answer Relevance:  0.8646
INFO: Average Context Recall:    1.0000
INFO: Average Context Precision: 1.0000
INFO: Average RAGAS Score:       0.9425
INFO: ----------------------------------------------------------------------
INFO: Min RAGAS Score:           0.8573
INFO: Max RAGAS Score:           1.0000
```

---

**Happy Evaluating! 🚀**
