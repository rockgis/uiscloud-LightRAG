# 역할별 LLM/VLM 설정 가이드

LightRAG는 서로 다른 처리 단계에 다른 LLM 또는 VLM을 설정하는 것을 지원합니다. 이 메커니즘은 추출에는 저비용 모델을, 최종 답변에는 더 강력한 모델을, 또는 멀티모달 분석에는 전용 비전-언어 모델(VLM)을 사용할 때 유용합니다.

## 역할 개요

현재 4가지 역할이 지원됩니다:

| 역할 | 목적 |
| --- | --- |
| `EXTRACT` | 엔티티/관계 추출 및 엔티티/관계 설명 요약. |
| `KEYWORD` | 검색 전 고수준/저수준 키워드 생성을 위한 쿼리 키워드 추출. |
| `QUERY` | 최종 QA, 일반 쿼리, 바이패스 쿼리, Ollama 호환 API의 쿼리 경로. |
| `VLM` | 이미지, 표, 수식 등 콘텐츠의 VLM 분석을 위한 멀티모달 분석 단계. |

역할에 전용 설정이 없으면, LightRAG는 기본 `LLM_*` 설정을 사용합니다.

## 기본 LLM 설정

기본 설정은 기본 LLM 프로바이더, 모델, 서비스 엔드포인트, 인증 정보, 동시성 제어를 정의합니다:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key

# 모든 LLM 요청의 기본 타임아웃
LLM_TIMEOUT=180

# 모든 LLM 호출의 기본 최대 동시성
MAX_ASYNC=4
```

공통 필드:

| 변수 | 설명 |
| --- | --- |
| `LLM_BINDING` | 기본 LLM 프로바이더. 지원되는 값: `openai`, `ollama`, `lollms`, `azure_openai`, `bedrock`, `gemini`. |
| `LLM_MODEL` | 기본 모델명. Azure OpenAI의 경우 보통 배포명입니다. |
| `LLM_BINDING_HOST` | 기본 프로바이더 엔드포인트. SDK 기본 엔드포인트는 `DEFAULT_GEMINI_ENDPOINT` 또는 `DEFAULT_BEDROCK_ENDPOINT` 같은 해당 센티넬 값을 사용하세요. |
| `LLM_BINDING_API_KEY` | 기본 API 키. Bedrock은 이 필드를 사용하지 않습니다. |
| `LLM_TIMEOUT` | 기본 LLM 타임아웃. 역할 타임아웃이 설정되지 않은 경우 역할이 상속합니다. |
| `MAX_ASYNC` | 기본 최대 LLM 동시성. `{ROLE}_MAX_ASYNC_LLM`이 설정되지 않은 경우 역할이 상속합니다. |

## 역할 재정의 변수

각 역할은 바인딩, 모델, 엔드포인트, API 키, 동시성, 타임아웃을 재정의할 수 있습니다:

```env
QUERY_LLM_BINDING=openai
QUERY_LLM_MODEL=gpt-5
QUERY_LLM_BINDING_HOST=https://api.openai.com/v1
QUERY_LLM_BINDING_API_KEY=your_query_api_key
QUERY_MAX_ASYNC_LLM=2
QUERY_LLM_TIMEOUT=240
```

변수 형식:

| 변수 | 설명 |
| --- | --- |
| `{ROLE}_LLM_BINDING` | 역할 프로바이더 재정의. `ROLE`은 `EXTRACT`, `KEYWORD`, `QUERY`, `VLM` 중 하나. |
| `{ROLE}_LLM_MODEL` | 역할 모델명 재정의. |
| `{ROLE}_LLM_BINDING_HOST` | 역할 엔드포인트 재정의. |
| `{ROLE}_LLM_BINDING_API_KEY` | 역할 API 키 재정의. Bedrock은 지원하지 않음. |
| `{ROLE}_MAX_ASYNC_LLM` | 역할 최대 동시성 재정의. 미설정 시 `MAX_ASYNC` 상속. |
| `{ROLE}_LLM_TIMEOUT` | 역할 타임아웃 재정의. 미설정 시 `LLM_TIMEOUT` 상속. |

## 프로바이더 옵션 재정의

프로바이더별 옵션은 다음 형식을 사용합니다:

```env
{ROLE}_{PROVIDER_PREFIX}_{FIELD}
```

예시:

```env
# QUERY 역할에만 OpenAI 추론 노력도(Reasoning Effort) 재정의
QUERY_OPENAI_LLM_REASONING_EFFORT=medium

# EXTRACT 역할에만 Bedrock 생성 파라미터 재정의
EXTRACT_BEDROCK_LLM_TEMPERATURE=0.0
EXTRACT_BEDROCK_LLM_MAX_TOKENS=2048

# VLM 역할에만 Gemini 생성 파라미터 재정의
VLM_GEMINI_LLM_MAX_OUTPUT_TOKENS=4096
VLM_GEMINI_LLM_TEMPERATURE=0.2
```

공통 프로바이더 접두사:

| 프로바이더 | 기본 옵션 접두사 | 역할 옵션 예시 |
| --- | --- | --- |
| `openai` / `azure_openai` | `OPENAI_LLM_*` | `QUERY_OPENAI_LLM_REASONING_EFFORT` |
| `ollama` | `OLLAMA_LLM_*` | `EXTRACT_OLLAMA_LLM_NUM_PREDICT` |
| `lollms` | Ollama 호환 옵션 세트 사용 | `QUERY_OLLAMA_LLM_TEMPERATURE` |
| `bedrock` | `BEDROCK_LLM_*` | `EXTRACT_BEDROCK_LLM_MAX_TOKENS` |
| `gemini` | `GEMINI_LLM_*` | `VLM_GEMINI_LLM_THINKING_CONFIG` |

## 상속 규칙

### 동일 프로바이더 내 재정의

역할이 `{ROLE}_LLM_BINDING`을 설정하지 않거나 기본 `LLM_BINDING`과 동일한 값으로 설정한 경우, 역할은 기본 설정을 상속합니다:

- `{ROLE}_LLM_MODEL`이 설정되지 않으면 `LLM_MODEL` 상속.
- `{ROLE}_LLM_BINDING_HOST`가 설정되지 않으면 `LLM_BINDING_HOST` 상속.
- `{ROLE}_LLM_BINDING_API_KEY`가 설정되지 않으면 `LLM_BINDING_API_KEY` 상속.
- `{ROLE}_LLM_TIMEOUT`이 설정되지 않으면 `LLM_TIMEOUT` 상속.
- `{ROLE}_MAX_ASYNC_LLM`이 설정되지 않으면 `MAX_ASYNC` 상속.
- 프로바이더 옵션은 먼저 기본 프로바이더 옵션을 상속한 후 역할별 프로바이더 옵션을 적용합니다.

따라서 동일 프로바이더 내에서 모델만 변경하려면 모델명만 설정하면 됩니다:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key
OPENAI_LLM_REASONING_EFFORT=minimal

# QUERY는 호스트, API 키, 타임아웃, 동시성, OPENAI_LLM_REASONING_EFFORT를 상속
QUERY_LLM_MODEL=gpt-5
```

### 크로스 프로바이더 재정의

역할의 `{ROLE}_LLM_BINDING`이 기본 `LLM_BINDING`과 다른 경우, 이는 크로스 프로바이더 설정입니다. 현재 규칙:

- `{ROLE}_LLM_MODEL`을 반드시 설정해야 합니다.
- Bedrock 이외의 프로바이더는 `{ROLE}_LLM_BINDING_API_KEY`를 설정해야 합니다.
- `{ROLE}_LLM_BINDING_HOST`가 설정되지 않으면, LightRAG는 해당 프로바이더의 기본 호스트를 사용하려 합니다.
- 프로바이더 옵션은 기본 프로바이더 옵션을 상속하지 않습니다. 비어 있는 상태에서 시작하며 역할별 프로바이더 옵션만 적용합니다.

예시: 로컬 추출에는 Ollama를 기본으로, 최종 답변에는 OpenAI 사용:

```env
LLM_BINDING=ollama
LLM_MODEL=qwen3.5:9b
LLM_BINDING_HOST=http://localhost:11434
OLLAMA_LLM_NUM_CTX=32768

QUERY_LLM_BINDING=openai
QUERY_LLM_MODEL=gpt-5-mini
QUERY_LLM_BINDING_HOST=https://api.openai.com/v1
QUERY_LLM_BINDING_API_KEY=your_openai_api_key
QUERY_OPENAI_LLM_REASONING_EFFORT=minimal
```

크로스 프로바이더 설정의 경우, 기본 프로바이더 엔드포인트와의 혼동을 피하기 위해 `{ROLE}_LLM_BINDING_HOST`를 명시적으로 설정하는 것을 권장합니다.

### Bedrock 인증 규칙

Bedrock은 `LLM_BINDING_API_KEY`를 사용하지 않으며 `{ROLE}_LLM_BINDING_API_KEY`를 지원하지 않습니다. 사용 가능한 인증 방법:

- 글로벌 SigV4: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `AWS_REGION`.
- 역할 수준 SigV4: `{ROLE}_AWS_ACCESS_KEY_ID`, `{ROLE}_AWS_SECRET_ACCESS_KEY`, `{ROLE}_AWS_SESSION_TOKEN`, `{ROLE}_AWS_REGION`.
- 프로세스 수준 Bearer 토큰: `AWS_BEARER_TOKEN_BEDROCK`. 이는 AWS SDK 프로세스 수준 설정으로, 역할별로 재정의할 수 없습니다.

역할 수준 Bedrock 예시:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_openai_api_key

EXTRACT_LLM_BINDING=bedrock
EXTRACT_LLM_MODEL=us.amazon.nova-lite-v1:0
EXTRACT_LLM_BINDING_HOST=DEFAULT_BEDROCK_ENDPOINT
EXTRACT_AWS_REGION=us-west-2
EXTRACT_AWS_ACCESS_KEY_ID=your_extract_access_key
EXTRACT_AWS_SECRET_ACCESS_KEY=your_extract_secret_key
EXTRACT_AWS_SESSION_TOKEN=your_optional_session_token
EXTRACT_BEDROCK_LLM_TEMPERATURE=0.0
EXTRACT_BEDROCK_LLM_MAX_TOKENS=2048
```

## 프로바이더 동작 매트릭스

| 프로바이더 | 역할 수준 호스트/base_url | 역할 수준 API 키 | 인증 제한 |
| --- | --- | --- | --- |
| `openai` | 지원, `{ROLE}_LLM_BINDING_HOST`를 통해 OpenAI 호환 클라이언트에 전달. | `{ROLE}_LLM_BINDING_API_KEY` 지원; 동일 프로바이더 내 미설정 시 기본 `LLM_BINDING_API_KEY` 상속. | 현재 주로 API 키/Bearer 모드. |
| `ollama` | 지원, `{ROLE}_LLM_BINDING_HOST`를 통해 Ollama 클라이언트에 전달. | `{ROLE}_LLM_BINDING_API_KEY` 지원; 동일 프로바이더 내 미설정 시 기본 키 상속. 하위 레이어에 키가 없으면 `OLLAMA_API_KEY`로 폴백. | Bearer 헤더. |
| `lollms` | 지원, `{ROLE}_LLM_BINDING_HOST`를 `base_url`로 사용. | `{ROLE}_LLM_BINDING_API_KEY` 지원; 동일 프로바이더 내 미설정 시 기본 키 상속. | Bearer 헤더. |
| `azure_openai` | 지원, `{ROLE}_LLM_BINDING_HOST`를 Azure 엔드포인트로 사용. | `{ROLE}_LLM_BINDING_API_KEY` 지원; 동일 프로바이더 내 미설정 시 기본 키 상속, `AZURE_OPENAI_API_KEY`로 폴백 가능. | `AZURE_OPENAI_API_VERSION`은 글로벌 환경 변수로 역할 수준 재정의를 지원하지 않음. |
| `bedrock` | 지원, `{ROLE}_LLM_BINDING_HOST`를 `endpoint_url`로 사용; `DEFAULT_BEDROCK_ENDPOINT`는 AWS SDK가 선택하도록 함. | 일반 API 키는 지원하지 않음. | 글로벌 또는 역할 수준 SigV4 사용. `AWS_BEARER_TOKEN_BEDROCK`은 프로세스 수준으로 역할별 재정의 불가. |
| `gemini` | 지원, `{ROLE}_LLM_BINDING_HOST`를 통해 Google GenAI 클라이언트에 전달; `DEFAULT_GEMINI_ENDPOINT`는 SDK 기본 엔드포인트 사용. | AI Studio 모드는 `{ROLE}_LLM_BINDING_API_KEY` 지원. | Vertex AI는 `GOOGLE_GENAI_USE_VERTEXAI`, `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_LOCATION`, `GOOGLE_APPLICATION_CREDENTIALS`로 제어; 모두 프로세스 수준 설정. |

## 권장 설정 패턴

### 1. 동일 프로바이더, 모델만 변경

동일한 OpenAI 키와 엔드포인트를 사용하면서 최종 답변에 더 강력한 모델을 사용하는 경우:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key
OPENAI_LLM_REASONING_EFFORT=minimal

QUERY_LLM_MODEL=gpt-5
QUERY_MAX_ASYNC_LLM=2
```

`QUERY`는 기본 호스트, API 키, `OPENAI_LLM_REASONING_EFFORT`를 상속합니다.

### 2. 동일 프로바이더, 모델 변경 및 옵션 조정

기본 모델은 추출에 사용하고 최종 답변은 더 높은 추론 노력도를 사용하는 경우:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key
OPENAI_LLM_REASONING_EFFORT=minimal
OPENAI_LLM_MAX_COMPLETION_TOKENS=4096

QUERY_LLM_MODEL=gpt-5
QUERY_OPENAI_LLM_REASONING_EFFORT=medium
QUERY_OPENAI_LLM_MAX_COMPLETION_TOKENS=9000
QUERY_LLM_TIMEOUT=240
```

### 3. 동일 프로바이더, 서로 다른 엔드포인트와 API 키

모든 역할이 `openai` 바인딩을 사용하지만 일부는 공식 OpenAI API에, 다른 일부는 로컬 vLLM, SGLang, OpenRouter 등 OpenAI 호환 엔드포인트에 접근하는 경우:

```env
###########################################################################
# 기본 LLM 폴백. 미지정 역할도 유효한 OpenAI 설정을 갖도록 EXTRACT와 맞춤.
###########################################################################
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_extract_openai_api_key
LLM_TIMEOUT=180
MAX_ASYNC=4

###########################################################################
# EXTRACT: OpenAI 공식 API, gpt-5-mini
###########################################################################
EXTRACT_LLM_BINDING=openai
EXTRACT_LLM_MODEL=gpt-5-mini
EXTRACT_LLM_BINDING_HOST=https://api.openai.com/v1
EXTRACT_LLM_BINDING_API_KEY=your_extract_openai_api_key
EXTRACT_OPENAI_LLM_REASONING_EFFORT=low
EXTRACT_OPENAI_LLM_MAX_COMPLETION_TOKENS=4096
EXTRACT_MAX_ASYNC_LLM=4
EXTRACT_LLM_TIMEOUT=180

###########################################################################
# QUERY: OpenAI 공식 API, gpt-5.4, 별도 API 키
###########################################################################
QUERY_LLM_BINDING=openai
QUERY_LLM_MODEL=gpt-5.4
QUERY_LLM_BINDING_HOST=https://api.openai.com/v1
QUERY_LLM_BINDING_API_KEY=your_query_openai_api_key
QUERY_OPENAI_LLM_REASONING_EFFORT=medium
QUERY_OPENAI_LLM_MAX_COMPLETION_TOKENS=9000
QUERY_MAX_ASYNC_LLM=2
QUERY_LLM_TIMEOUT=240

###########################################################################
# KEYWORD: 로컬 vLLM OpenAI 호환 엔드포인트, Qwen3.5-35B-A3B
###########################################################################
KEYWORD_LLM_BINDING=openai
KEYWORD_LLM_MODEL=Qwen3.5-35B-A3B
KEYWORD_LLM_BINDING_HOST=http://localhost:8000/v1
KEYWORD_LLM_BINDING_API_KEY=local-vllm-api-key
KEYWORD_OPENAI_LLM_MAX_TOKENS=2048
KEYWORD_MAX_ASYNC_LLM=4
KEYWORD_LLM_TIMEOUT=180
```

### 4. 하나의 역할이 프로바이더를 교차

기본은 공식 OpenAI 모델을 사용하고 키워드 추출만 로컬 Ollama를 사용하는 경우:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_openai_api_key
OPENAI_LLM_REASONING_EFFORT=medium

KEYWORD_LLM_BINDING=ollama
KEYWORD_LLM_MODEL=qwen3.5:9b
KEYWORD_LLM_BINDING_HOST=http://localhost:11434
KEYWORD_LLM_BINDING_API_KEY=ollama-local-key
KEYWORD_OLLAMA_LLM_NUM_CTX=32768
```

### 5. VLM에 전용 멀티모달 모델 지정

텍스트 작업에는 저렴한 모델을 사용하고 멀티모달 분석에는 비전-언어 모델을 사용하는 경우:

```env
VLM_PROCESS_ENABLE=true

LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_api_key

VLM_LLM_BINDING=openai
VLM_LLM_MODEL=gpt-4o
VLM_OPENAI_LLM_MAX_TOKENS=4096
VLM_MAX_ASYNC_LLM=2
VLM_LLM_TIMEOUT=240
```

VLM이 동일한 프로바이더와 키를 사용하는 경우 `VLM_LLM_BINDING_HOST`와 `VLM_LLM_BINDING_API_KEY`를 생략할 수 있습니다.

`VLM_PROCESS_ENABLE`은 멀티모달 분석의 마스터 스위치입니다. `false`이면 파이프라인이 경고를 출력하고 VLM을 호출하지 않고 모든 멀티모달 항목을 건너뜁니다. `true`이면 유효한 VLM 바인딩(`VLM_LLM_BINDING`이 설정된 경우 해당 값, 그렇지 않으면 `LLM_BINDING`)이 이미지 입력을 지원해야 합니다. 비전 가능 프로바이더: `openai`, `azure_openai`, `gemini`, `bedrock`, `ollama`, `anthropic`. `lollms`는 이미지 입력을 처리할 수 없어 시작 시 거부됩니다.

### 6. Bedrock 역할 수준 SigV4 자격 증명

하나의 역할만 Bedrock에 접근하고 독립적인 IAM/STS 자격 증명을 사용하는 경우:

```env
LLM_BINDING=openai
LLM_MODEL=gpt-5-mini
LLM_BINDING_HOST=https://api.openai.com/v1
LLM_BINDING_API_KEY=your_openai_api_key

QUERY_LLM_BINDING=bedrock
QUERY_LLM_MODEL=us.amazon.nova-lite-v1:0
QUERY_LLM_BINDING_HOST=DEFAULT_BEDROCK_ENDPOINT
QUERY_AWS_REGION=us-east-1
QUERY_AWS_ACCESS_KEY_ID=your_query_access_key
QUERY_AWS_SECRET_ACCESS_KEY=your_query_secret_key
QUERY_AWS_SESSION_TOKEN=your_optional_session_token
QUERY_BEDROCK_LLM_MAX_TOKENS=4096
QUERY_BEDROCK_LLM_TEMPERATURE=0.2
```

`QUERY_LLM_BINDING_API_KEY`는 설정하지 마세요. Bedrock은 해당 설정을 거부합니다.

## 주의 사항

- 동일 프로바이더 내에서 `OPENAI_LLM_REASONING_EFFORT`, `OPENAI_LLM_MAX_TOKENS`, `OLLAMA_LLM_NUM_CTX`, `GEMINI_LLM_THINKING_CONFIG` 등의 프로바이더 옵션은 자동으로 상속됩니다.
- 현재 "상속된 프로바이더 옵션 해제"에 대한 깔끔한 역할 수준 시맨틱이 없습니다. 동일 프로바이더 역할의 모델이 기본 옵션을 지원하지 않는 경우, 해당 역할에 대해 지원되는 값으로 옵션을 명시적으로 재정의하거나, 역할을 크로스 프로바이더로 설정하고 지원하는 역할별 프로바이더 옵션만 설정하세요.
- `azure_openai`의 `AZURE_OPENAI_DEPLOYMENT`와 `AZURE_OPENAI_API_VERSION`은 글로벌 환경 변수입니다. `AZURE_OPENAI_DEPLOYMENT`가 설정된 경우 역할 모델명보다 우선할 수 있습니다.
- Gemini Vertex AI 모드는 프로세스 수준 Google 환경 변수로 제어됩니다. 동일한 LightRAG 프로세스에서 일부 역할은 Vertex AI를 사용하고 다른 역할은 AI Studio API 키를 사용할 수 없습니다.
- Docker/Compose에서 `LLM_BINDING_HOST`는 보통 `host.docker.internal` 같은 컨테이너에서 접근 가능한 주소를 사용해야 합니다. 역할 수준 호스트도 동일한 원칙을 따릅니다.
- `.env` 수정 후 LightRAG 서버를 재시작하세요. 일부 IDE 터미널은 `.env`를 미리 로드하므로, 환경 변수가 적용되었는지 확인하려면 새 터미널 세션을 여는 것을 권장합니다.
