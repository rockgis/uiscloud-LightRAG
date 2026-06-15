# 비대칭 임베딩(Asymmetric Embedding) 설정

LightRAG는 기본적으로 임베딩 동작을 대칭(Symmetric)으로 유지합니다. 쿼리/문서 비대칭 임베딩은 `EMBEDDING_ASYMMETRIC=true`가 명시적으로 설정된 경우에만 활성화됩니다.

이는 환경에 접두사(Prefix) 변수가 존재하더라도 사용자가 의도적으로 비대칭 임베딩을 활성화하지 않은 경우 검색 결과가 의도치 않게 변경되는 것을 방지합니다.

특정 모델에 비대칭 임베딩을 활성화하기 전에, 해당 모델의 최신 모델 카드(Model Card) 또는 프로바이더 문서를 반드시 확인하세요. API 바인딩만으로 올바른 동작을 추론하지 마세요. `openai` 호환 엔드포인트는 동일한 API 형태 뒤에 명령어 없는(Instruction-free) 모델, 접두사 기반 모델, 또는 프로바이더 특정 모델을 제공할 수 있습니다.

## 재인덱싱 필요 조건

비대칭 임베딩 설정을 변경하면 저장된 문서와 이후 쿼리에 대해 생성되는 벡터가 달라집니다. 다음 설정을 활성화, 비활성화, 또는 변경한 후에는 해당 워크스페이스의 기존 LightRAG 데이터를 삭제하고 소스 파일을 다시 인덱싱해야 합니다:

- `EMBEDDING_ASYMMETRIC`
- `EMBEDDING_QUERY_PREFIX`
- `EMBEDDING_DOCUMENT_PREFIX`
- Jina의 `task`, Gemini의 `task_type`, VoyageAI의 `input_type` 등 프로바이더 태스크 동작

비대칭 임베딩 설정 변경 사이에 기존 벡터 스토어를 재사용하지 마세요. 서로 다른 쿼리/문서 동작으로 생성된 벡터를 혼합하면 검색 품질을 예측할 수 없게 됩니다.

## 바인딩 타입

LightRAG는 두 가지 비대칭 임베딩 방식을 구분합니다:

| 방식 | 바인딩 | 비대칭 동작 적용 방법 |
| --- | --- | --- |
| 프로바이더 태스크 파라미터 | `jina`, `gemini`, `voyageai` | LightRAG가 쿼리/문서 컨텍스트를 프로바이더 특정 `task`, `task_type`, 또는 `input_type` 파라미터에 전달합니다. |
| 텍스트 태스크 접두사 | `openai`, `azure_openai`, `ollama` | LightRAG가 임베딩 API 호출 전에 설정된 텍스트 접두사를 앞에 붙입니다. 모델 카드에 별도의 쿼리/문서 접두사가 명시적으로 요구되는 경우에만 사용하세요. |

다른 서버 임베딩 바인딩은 현재 `EMBEDDING_ASYMMETRIC=true`를 지원하지 않습니다.

## 기본 설정: 대칭 임베딩

`EMBEDDING_ASYMMETRIC`이 설정되지 않은 경우, 접두사 변수가 존재하더라도 LightRAG는 비대칭 임베딩 동작을 활성화하지 않습니다:

```env
# EMBEDDING_ASYMMETRIC이 설정되지 않음
# EMBEDDING_QUERY_PREFIX="search_query: "
# EMBEDDING_DOCUMENT_PREFIX="search_document: "
```

접두사는 무시되고 경고가 기록됩니다.

플래그가 명시적으로 false로 설정된 경우에도 동일하게 동작합니다:

```env
EMBEDDING_ASYMMETRIC=false
```

## 명령어 없는 모델: 대칭 유지

일부 임베딩 모델은 명령어가 없는(Instruction-free) 방식으로, 암묵적 의도(Implicit Intent)를 사용한다고 설명되기도 합니다. 이러한 모델은 원본 텍스트 자체에서 쿼리/문서 매칭을 처리하도록 학습되었으며 쿼리/문서 접두사나 프로바이더 태스크 파라미터가 필요하지 않습니다. 이러한 모델에는 `EMBEDDING_ASYMMETRIC=true`를 설정하지 마세요. 모델 카드에 별도 지시가 없는 한 설정하지 않거나 `false`로 설정하고 `EMBEDDING_QUERY_PREFIX`나 `EMBEDDING_DOCUMENT_PREFIX`도 설정하지 마세요.

일반적으로 대칭 모드를 유지해야 하는 모델 예시:

| 모델 계열 | 예시 모델 ID | 비고 |
| --- | --- | --- |
| BGE-M3 | `BAAI/bge-m3` | 일반 텍스트 입력 사용. 특정 서빙 래퍼의 모델 카드에 명시되지 않는 한 `search_query:` / `search_document:`를 추가하지 마세요. |
| OpenAI Text Embedding 3 | `text-embedding-3-small`, `text-embedding-3-large` | OpenAI 임베딩 API는 텍스트 입력과 모델 이름을 사용하며 쿼리/문서 태스크 파라미터를 노출하지 않습니다. |
| Mistral Embed | `mistral-embed` | 프로바이더의 일반 임베딩 입력을 사용하세요. 태스크 접두사를 임의로 만들지 마세요. |
| Alibaba GTE 기본 모델 | `gte-large`, `gte-large-zh` | 기본 GTE 모델은 일반 검색에 일반 텍스트를 사용합니다. `gte-Qwen2-1.5B-instruct`와 같은 최신 `instruct` 변형에는 적용되지 않으므로 해당 모델 카드를 확인하세요. |
| Jina Embeddings v2 | `jina-embeddings-v2-base-en`, `jina-embeddings-v2-base-zh` | Jina v2는 일반 텍스트 입력입니다. Jina v3/v4는 다르며 검색 태스크에 `task` 파라미터를 사용합니다. |

모델이 명령어 없는(Instruction-free) 방식인 경우, LightRAG의 비대칭 모드를 활성화하면 모델이 학습되거나 문서화된 기대값과 다른 입력이 전달될 수 있습니다. 서버가 성공적으로 시작되더라도 검색 품질이 저하될 수 있습니다.

## 프로바이더 태스크 파라미터 바인딩

별도의 쿼리/문서 임베딩 태스크를 노출하는 프로바이더에 이 모드를 사용하세요. 이 바인딩에는 접두사 변수를 설정하지 마세요.

Jina 예시:

```env
EMBEDDING_BINDING=jina
EMBEDDING_ASYMMETRIC=true
EMBEDDING_MODEL=jina-embeddings-v4
```

Gemini 예시:

```env
EMBEDDING_BINDING=gemini
EMBEDDING_ASYMMETRIC=true
EMBEDDING_MODEL=gemini-embedding-001
```

VoyageAI 예시:

```env
EMBEDDING_BINDING=voyageai
EMBEDDING_ASYMMETRIC=true
EMBEDDING_MODEL=voyage-3
```

이 바인딩에 `EMBEDDING_QUERY_PREFIX` 또는 `EMBEDDING_DOCUMENT_PREFIX`가 함께 설정된 경우, LightRAG는 경고를 기록하고 접두사를 무시합니다.

## 텍스트 태스크 접두사 바인딩

`search_query:`, `search_document:`, `query:`, `passage:` 등의 접두사를 문서에 명시한 모델처럼, 입력 텍스트에 태스크 지시어가 필요한 임베딩 모델에 이 모드를 사용하세요. 단순히 모델이 `openai`, `azure_openai`, `ollama`를 통해 제공된다는 이유로 이 모드를 활성화하지 마세요.

두 접두사 변수 모두 명시적으로 설정해야 합니다:

```env
EMBEDDING_ASYMMETRIC=true
EMBEDDING_QUERY_PREFIX="search_query: "
EMBEDDING_DOCUMENT_PREFIX="search_document: "
```

한쪽이 의도적으로 접두사 없이 남겨져야 하는 경우, 센티넬(Sentinel) 값 `NO_PREFIX`를 사용하세요:

```env
EMBEDDING_ASYMMETRIC=true
EMBEDDING_QUERY_PREFIX="search_query: "
EMBEDDING_DOCUMENT_PREFIX=NO_PREFIX
```

`NO_PREFIX`는 내부적으로 빈 문자열로 변환됩니다. 이는 설정되지 않은 변수와 다릅니다. 해당 쪽이 검토되어 의도적으로 접두사 없이 남겨졌음을 의미합니다.

최소한 한쪽에는 비어 있지 않은 접두사가 있어야 합니다. 다음은 유효하지 않습니다:

```env
EMBEDDING_ASYMMETRIC=true
EMBEDDING_QUERY_PREFIX=NO_PREFIX
EMBEDDING_DOCUMENT_PREFIX=NO_PREFIX
```

## 빈 접두사 금지

의도적인 빈 접두사를 위해 빈 환경 변수 값을 사용하지 마세요:

```env
EMBEDDING_DOCUMENT_PREFIX=
```

대신 `NO_PREFIX`를 사용하세요. 빈 값은 거부됩니다. 셸, `.env`, Docker Compose 처리에서 빈 문자열과 의도치 않은 누락된 설정을 구별할 수 없기 때문입니다.

## 유효성 검사 요약

| 설정 | 결과 |
| --- | --- |
| `EMBEDDING_ASYMMETRIC` 미설정 | 대칭 모드; 접두사는 경고와 함께 무시됩니다. |
| `EMBEDDING_ASYMMETRIC=false` | 대칭 모드; 접두사는 경고와 함께 무시됩니다. |
| `BAAI/bge-m3`, `text-embedding-3-small`, `mistral-embed`, 기본 GTE, Jina v2 등 명령어 없는 모델 | 대칭 모드 유지; 모델 카드에 명시되지 않는 한 접두사나 프로바이더 태스크를 설정하지 마세요. |
| `jina`/`gemini`/`voyageai`와 함께 `EMBEDDING_ASYMMETRIC=true` | 프로바이더 태스크 모드; 접두사는 경고와 함께 무시됩니다. |
| `openai`/`azure_openai`/`ollama`와 함께 `EMBEDDING_ASYMMETRIC=true` 및 두 접두사 변수 설정 | 접두사 모드. |
| 접두사 모드에서 접두사 변수 누락 | 시작 오류; 실제 접두사 또는 `NO_PREFIX`를 사용하세요. |
| 접두사 모드에서 양쪽 모두 `NO_PREFIX` | 시작 오류; 비대칭 동작이 발생하지 않습니다. |
| 접두사 변수가 빈 값으로 설정 | 시작 오류; `NO_PREFIX`를 사용하세요. |

유효한 비대칭 임베딩 설정 변경은 여전히 워크스페이스 데이터를 삭제하고 소스 파일을 다시 인덱싱해야 합니다.
