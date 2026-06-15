# 파일 처리 파이프라인 명세

v1.5.0부터 LightRAG의 파일 처리 파이프라인이 대폭 업그레이드되었습니다:

* 다중 파일 콘텐츠 추출 엔진 지원: legacy, native, mineru, docling
* 다중 텍스트 청킹 방법 지원: Fix, Recursive, Vector, Paragraph
* 개별 파일별 개체-관계 추출 비활성화 지원

LightRAG Server는 중간 파일 처리 포맷인 `LightRAG Document`를 도입합니다. 이 포맷은 표와 이미지 같은 멀티모달 데이터를 지원하며, 나중에 내용 추적이 용이하도록 문서의 섹션/단락 메타데이터도 포함합니다.

이 문서는 **LightRAG Server** 배포 및 사용 관점에서 정리되었습니다. 바로 적용 가능한 빠른 시작 설정을 먼저 제시하고, 이어서 콘텐츠 추출 및 청킹 설정 구문, 스토리지/디렉터리 레이아웃, 중복 제거, 동시성, 재개 규칙을 다룹니다. Python으로 `LightRAG` 클래스를 직접 호출하는 개발자는 [제8장: Python SDK 호출](#8-python-sdk-호출)로 바로 이동하세요.

## 1. 빠른 시작

### 레거시 파일 처리 동작 유지

모든 파일을 레거시 문서 파싱 및 청킹 전략으로 처리합니다. `LIGHTRAG_PARSER`를 설정하지 않거나 다음 값으로 설정하세요:

```bash
LIGHTRAG_PARSER=*:legacy-F
```

### 권장 파일 처리 동작

외부 문서 파싱 서비스나 `VLM` 비전 모델에 의존하지 않습니다. 새로 내장된 `Native` 엔진으로 `docx` 문서를 파싱하되 표(t) 및 수식(e) 모달리티 분석을 활성화하고 `P` 청킹 전략을 사용합니다. 다른 문서는 레거시 콘텐츠 추출기와 보다 효과적인 `R` 청킹 전략을 조합합니다.

```bash
LIGHTRAG_PARSER=*:native-teP,*:legacy-R
```

### 멀티모달 처리 기능 활성화

멀티모달 처리를 활성화하려면 `MinerU` 파일 파싱 서비스와 `VLM` 비전 인식 모델이 필요합니다. `Native`로 `docx` 파일을 파싱하고, `MinerU`로 `pdf`, `office`, 각종 이미지 파일을 파싱합니다. 위의 모든 파일에 이미지(i), 표(t), 수식(e) 모달리티 분석을 활성화하고 `P` 청킹 전략과 결합합니다. 다른 문서는 레거시 콘텐츠 추출기와 `R` 청킹 전략으로 폴백합니다.

```bash
LIGHTRAG_PARSER=*:native-iteP,*:mineru-iteP,*:legacy-R
VLM_PROCESS_ENABLE=true
VLM_LLM_MODEL=kimi-k2.6
MINERU_API_MODE=local
MINERU_LOCAL_ENDPOINT=http://localhost:8000
```

> `P`는 LightRAG의 고유 청킹 전략입니다. 자세한 내용은 [단락 시맨틱 청킹](ParagraphSemanticChunking-ko.md)을 참고하세요. VLM 설정은 [역할 기반 LLM/VLM 설정 가이드](RoleSpecificLLMConfiguration.md)를 참고하세요.

## 2. 콘텐츠 추출 및 처리 옵션 설정

LightRAG의 파일 처리 설정은 두 부분으로 구성됩니다. 콘텐츠 추출 엔진은 원본 파일을 어떻게 파싱할지 결정하고, 처리 옵션은 파싱 후 멀티모달 분석 수행 여부, 사용할 청킹 방법, 지식 그래프 구축 여부를 결정합니다. 일반적으로 환경 변수 `LIGHTRAG_PARSER`로 파일 확장자별 기본 규칙을 먼저 설정한 뒤, 파일명의 `[hint]`로 개별 파일을 재정의합니다. 엔진과 옵션은 동일한 설정 조각에 함께 작성할 수 있습니다. 예: `docx:native-iet`, `report.[native-R!].docx`.

이전 버전과의 호환성을 위해 설정을 수정하지 않으면 업그레이드 후에도 파일 콘텐츠 추출 동작이 원래의 `legacy` 방식으로 유지됩니다. 새 콘텐츠 처리 엔진을 활성화하려면 이 섹션에 설명된 대로 설정하세요.

### 2.1 설정 구문 개요

전체 설정 모델은 다음과 같습니다:

```text
LIGHTRAG_PARSER=ext:engine-options,ext:engine,*:legacy-R
filename.[ENGINE].ext
filename.[ENGINE-OPTIONS].ext
filename.[-OPTIONS].ext
```

- `LIGHTRAG_PARSER`는 파일 확장자별로 매칭되는 기본 규칙 테이블입니다. 예: `pdf:mineru`, `docx:native-iet`.
- 파일명의 `[hint]`는 단일 파일 재정의 규칙입니다. 예: `paper.[mineru].pdf`, `memo.[native-R!].docx`.
- `ENGINE`은 콘텐츠 추출 엔진입니다: `legacy`, `native`, `mineru`, `docling` 중 하나.
- `OPTIONS`는 처리 옵션의 문자열 조합입니다. 예: `iet`, `R!`, `P`. 옵션은 최종적으로 `process_options`에 기록되어 이후 파이프라인 단계에서 읽힙니다.
- `ENGINE-OPTIONS`의 하이픈은 엔진과 옵션을 구분하는 용도로만 사용되며 옵션 자체에는 포함되지 않습니다.
- 처리 옵션만 지정할 경우 반드시 `[-OPTIONS]` 형식으로 작성해야 합니다. 예: `[-!]`. 하이픈이 없는 `[abc]`는 엄격히 엔진 이름으로 해석되며 오류가 발생합니다. 옵션으로 폴백되지 않습니다.

일반적인 조합 예시:

```bash
LIGHTRAG_PARSER=pdf:mineru-R,docx:native-ietP,*:legacy-R
MINERU_API_MODE=local
MINERU_LOCAL_ENDPOINT=http://localhost:8000
DOCLING_ENDPOINT=http://localhost:5001
```

```text
my-proposal.[native-iet].docx   # native 엔진, 드로잉/표/수식 분석 활성화
my-memo.[native-R!].docx        # native 엔진, 재귀 시맨틱 청킹, 지식 그래프 구축 비활성화
my-proposal.[-!].docx           # 기본 엔진, 지식 그래프 구축만 비활성화
my-proposal.[mineru].docx       # MinerU 엔진, 모든 처리 옵션 기본값
```

### 2.2 기본 규칙: `LIGHTRAG_PARSER`

`LIGHTRAG_PARSER`는 파일 확장자별 기본 콘텐츠 추출 엔진을 설정하는 데 사용됩니다. 엔진 뒤에 기본 처리 옵션을 추가할 수도 있습니다:

```text
ext:engine,ext:engine,*:legacy
ext:engine;ext:engine;*:legacy
ext:engine-options
```

- 좌변은 파일 확장자(전체 파일명이 아님)에 매칭됩니다. `*.pdf:mineru`가 아닌 `pdf:mineru`로 작성하세요.
- 규칙은 쉼표 `,` 또는 세미콜론 `;`으로 구분할 수 있습니다.
- 규칙은 왼쪽에서 오른쪽으로 검사됩니다. 우선순위가 높은 규칙을 앞에 배치하고, 와일드카드 규칙은 보통 마지막에 둡니다.
- 엔진 뒤의 `-options` 접미사는 이 규칙에 매칭된 파일의 기본 `process_options`로 사용됩니다. 예: `LIGHTRAG_PARSER=docx:native-iet`는 모든 `.docx` 파일이 기본적으로 이미지, 표, 수식 분석이 활성화된 `native` 엔진을 사용함을 의미합니다.

### 2.3 단일 파일 재정의: 파일명 힌트

파일명의 대괄호를 사용하여 단일 파일의 처리 방법을 일시적으로 지정할 수 있습니다:

```text
paper.[mineru-R].pdf
slides.[docling].pptx
memo.[native-P].docx
notes.[-R].md
```

대괄호 안의 내용은 세 가지 형식을 지원합니다:

```text
[ENGINE]              # 엔진만 지정; 처리 옵션은 기본값 또는 LIGHTRAG_PARSER 제공값 사용
[ENGINE-OPTIONS]      # 엔진과 처리 옵션 모두 지정
[-OPTIONS]            # 처리 옵션만 지정; 엔진은 여전히 LIGHTRAG_PARSER / 기본 규칙에 따름
```

힌트 파싱 시 하이픈 없는 내용은 엔진 이름(`mineru`/`native`/`docling`/`legacy`)과 정확히 일치해야 합니다. 하이픈 앞의 내용이 있으면 하이픈 앞은 엔진, 뒤는 옵션입니다. 하이픈으로 시작하면 옵션만 지정합니다. 레거시 `[OPTIONS]` 구문은 더 이상 유효하지 않습니다. 예를 들어 `[iet]`는 이제 `[-iet]`로 작성해야 합니다.

### 2.4 콘텐츠 추출 엔진

| 엔진 | 설명 | 지원 파일 형식(확장자) |
| --- | --- | --- |
| `legacy` | 레거시 추출; 파이프라인에 합류하기 전에 콘텐츠를 일괄 추출 | `txt` `md` `mdx` `pdf` `docx` `pptx` `xlsx` `rtf` `odt` `tex` `epub` `html` `htm` `csv` `json` `xml` `yaml` `yml` `log` `conf` `ini` `properties` `sql` `bat` `sh` `c` `h` `cpp` `hpp` `py` `java` `js` `ts` `swift` `go` `rb` `php` `css` `scss` `less` |
| `native` | 내장 지능형 구조화 콘텐츠 추출기 | `docx` |
| `mineru` | 외부 MinerU 콘텐츠 추출 엔진 | `pdf` `doc` `docx` `ppt` `pptx` `xls` `xlsx` `png` `jpg` `jpeg` `jp2` `webp` `gif` `bmp` |
| `docling` | 외부 Docling 콘텐츠 추출 엔진 | `pdf` `docx` `pptx` `xlsx` `md` `html` `xhtml` `png` `jpg` `jpeg` `tiff` `webp` `bmp` |

`mineru`와 `docling`은 외부 콘텐츠 추출 엔진입니다. 관련 규칙을 활성화하기 전에 서비스가 먼저 실행 중이어야 하며, LightRAG에 해당 엔드포인트/토큰을 설정해야 합니다.

LightRAG는 `mineru` 및 `docling` 엔진의 파싱 결과를 로컬에 캐시합니다. 동일한 파일을 다시 업로드해도 보통 엔진이 문서를 재파싱하지 않습니다. 파싱 캐시를 삭제하려면 문서 관리 인터페이스의 파일 삭제 대화상자에서 "파일도 삭제" 옵션을 클릭해야 합니다. `mineru`/`docling` 엔진의 엔드포인트 주소나 유효한 추출 파라미터를 수정하면 캐시가 무효화되어 동일한 파일을 다시 업로드할 때 엔진이 파일 내용을 재파싱하게 됩니다.

#### MinerU 설정 및 로컬 배포

MinerU 클라이언트는 두 가지 모드를 지원합니다. 하나를 선택하세요:

- `local`: 자체 호스팅 MinerU 서비스 (공식 Docker Compose 배포 권장). LightRAG가 HTTP를 통해 로컬 컨테이너를 호출합니다.
- `official`: MinerU 공식 정밀 API v4에 직접 연결합니다. [mineru.net](https://mineru.net)에서 토큰을 신청해야 합니다.

**로컬 배포 (Docker Compose)**

공식 [opendatalab/MinerU](https://github.com/opendatalab/MinerU) 저장소를 로컬에 클론하고, 저장소 내부의 Docker 배포 디렉터리로 이동한 후 먼저 이미지를 빌드합니다:

```bash
docker compose -f compose.yaml build
```

그런 다음 API 서비스를 시작합니다 (`--profile api`는 HTTP API 컨테이너를 활성화하는 데 필요하며, 기본 수신 포트는 8000입니다):

```bash
docker compose -f compose.yaml --profile api up -d
```

이미지 빌드 세부 정보, GPU 드라이버 설정, 모델 가중치 위치 등은 공식 README를 참고하세요: <https://github.com/opendatalab/MinerU>

**LightRAG 측 환경 설정**

로컬 모드 (자체 호스팅 mineru-api):

```bash
MINERU_API_MODE=local
MINERU_LOCAL_ENDPOINT=http://localhost:8000
```

공식 모드 (MinerU 클라우드 API):

```bash
MINERU_API_MODE=official
MINERU_API_TOKEN=<your_token>
# MINERU_OFFICIAL_ENDPOINT=https://mineru.net   # 기본값, 보통 변경 불필요
```

나머지 고급 스위치 (`MINERU_MODEL_VERSION`, `MINERU_LANGUAGE`, `MINERU_ENABLE_TABLE`/`MINERU_ENABLE_FORMULA`, `MINERU_PAGE_RANGES`, `MINERU_LOCAL_BACKEND`/`MINERU_LOCAL_PARSE_METHOD`, `MINERU_POLL_INTERVAL_SECONDS`/`MINERU_MAX_POLLS`, `MINERU_ENGINE_VERSION`, `LIGHTRAG_FORCE_REPARSE_MINERU` 등)는 저장소 루트의 `env.example` 템플릿에 있는 MinerU 섹션을 참고하세요. `MINERU_PAGE_RANGES`는 두 모드에서 의미가 다릅니다: `official`은 전체 목록(예: `1-3,5,7-9`)을 지원하고, `local`은 단일 페이지(`3`) 또는 단순 범위(`1-10`)만 지원하며 쉼표로 구분된 목록은 허용하지 않습니다.

#### Docling 설정

`docling` 콘텐츠 추출 엔진은 외부 [docling-serve](https://github.com/DS4SD/docling-serve) 서비스 (v1 비동기 API)가 필요합니다. 최소 설정:

```bash
DOCLING_ENDPOINT=http://localhost:5001
```

`DOCLING_ENDPOINT`는 기본 URL입니다 (`/v1/convert/file/async` **제외**). 현재 LightRAG는 Docling의 표준 파이프라인을 사용하여 파일을 처리합니다. 다음 환경 변수를 통해 Docling 파이프라인의 동작을 제어할 수 있습니다:

| 환경 변수 | 기본값 | 의미 |
| --- | --- | --- |
| `DOCLING_DO_OCR` | `true` | OCR 마스터 스위치 |
| `DOCLING_FORCE_OCR` | `true` | 페이지별 OCR 강제 (스캔 문서에 필수; 비스캔 문서에 활성화해도 레이아웃 인식 품질 향상에 도움이 되는 경우가 많음) |
| `DOCLING_OCR_ENGINE` | `auto` | OCR 엔진 선택 (변경 비권장) |
| `DOCLING_OCR_PRESET` | `auto` | OCR 엔진 프리셋 (변경 비권장) |
| `DOCLING_OCR_LANG` | (비어 있음) | OCR 엔진 요구사항에 따라 설정 (변경 비권장) |
| `DOCLING_DO_FORMULA_ENRICHMENT` | `false` | 문서에서 수식을 인식하여 LaTeX 형식으로 출력할지 여부; 활성화 전에 Docling 백엔드에 수식 인식 모델이 다운로드되어 있는지 확인 필요 |

`DOCLING_OCR_ENGINE`/`DOCLING_OCR_PRESET`이 설정되지 않으면 `auto`와 동일하게 처리됩니다. `DOCLING_OCR_LANG`이 설정되지 않으면 docling-serve에 언어 목록이 전달되지 않고 OCR 엔진의 자체 기본값을 사용합니다. 파싱 캐시 서명은 이러한 유효 파라미터로 계산되므로 "설정하지 않음"과 "기본값으로 명시적으로 설정"은 캐시를 무효화하지 않습니다.

폴링 예산 환경 변수 두 가지 (docling-serve는 서버 측 롱폴 사용; 클라이언트는 추가 슬립 불필요):

| 환경 변수 | 기본값 | 의미 |
| --- | --- | --- |
| `DOCLING_POLL_INTERVAL_SECONDS` | `5` | 파싱 결과 대기 폴링 간격 |
| `DOCLING_MAX_POLLS` | `240` | 최대 폴링 반복 횟수; 초과 시 `TimeoutError` 발생;<br />기본 대기 시간 ≈ 5 × 240 (약 20분) |

번들 캐시 환경 변수 세 가지:

| 환경 변수 | 기본값 | 의미 |
| --- | --- | --- |
| `DOCLING_ENGINE_VERSION` | (비어 있음) | Docling 엔진 버전; 버전 변경 시 파싱 캐시 무효화 |
| `LIGHTRAG_FORCE_REPARSE_DOCLING` | `false` | `true`/`1`로 설정 시 파싱 캐시 미사용 |
| `DOCLING_BBOX_ATTRIBUTES` | `{"origin":"LEFTBOTTOM"}` | Docling 레이아웃의 기본 좌표계 |

**`DOCLING_DO_FORMULA_ENRICHMENT` 전제 조건**: docling-serve 측에 code-formula 모델 가중치가 준비되어 있어야 합니다. 어댑터는 이중 트랙과 호환됩니다. 활성화된 경우 `text` 필드가 LaTeX 형식이 되고, 비활성화된 경우 또는 가중치 누락으로 `text == orig`이면 일반 텍스트로 폴백하고 `equations.json`을 작성하지 않습니다. 따라서 기본값 `false`는 보수적입니다. 배포 측에 모델이 준비된 것을 확인한 후에만 활성화하세요.

#### Docling 로컬 배포 (LaTeX 수식 인식 활성화)

다음은 Docker 기반 docling-serve 배포를 예시로, 이미지 다운로드부터 모델 마운트까지 전체 단계를 제공합니다. 배포가 완료되면 LightRAG의 `.env`에 `DOCLING_DO_FORMULA_ENRICHMENT=true`를 작성하여 LaTeX 수식 인식을 활성화합니다.

> **중요**: 아래 단계는 GPU가 CUDA 13을 지원하는 환경을 기준으로 합니다. GPU가 구형이어서 CUDA 13을 지원하지 않는 경우, 명령어와 compose 파일의 이미지 이름 `docling-serve-cu130:main`을 해당 CUDA 버전의 태그로 교체하세요. 사용 가능한 이미지 목록은 [docling-serve Packages](https://github.com/orgs/docling-project/packages?repo_name=docling-serve)를 참고하세요.

**1. 이미지 pull**

```bash
docker pull ghcr.io/docling-project/docling-serve-cu130:main
```

**2. 모델 다운로드**

```bash
# docling 작업 디렉터리 생성
mkdir docling
cd docling

# 모델 마운트 디렉터리 생성
mkdir models

# 컨테이너 내부의 기존 모델을 models 디렉터리로 복사
docker run --rm -it \
  -v "$(pwd)/models:/opt/app-root/src/models" \
  ghcr.io/docling-project/docling-serve-cu130:main \
  cp -r /opt/app-root/src/.cache/docling/models /opt/app-root/src/

# 수식 인식 모델 다운로드
docker run --rm \
  -v "$(pwd)/models:/opt/app-root/src/models" \
  -e DOCLING_SERVE_ARTIFACTS_PATH="/opt/app-root/src/models" \
  ghcr.io/docling-project/docling-serve-cu130:main \
  docling-tools models download-hf-repo docling-project/CodeFormulaV2 -o models
```

**3. `docker-compose.yaml` 생성**

이전 단계의 `docling` 디렉터리에 다음 내용으로 `docker-compose.yaml`을 생성합니다:

```yaml
services:
  docling-serve:
    image: ghcr.io/docling-project/docling-serve-cu130:main
    container_name: docling-serve
    ports:
      - "5001:5001"
    environment:
      DOCLING_SERVE_ENABLE_UI: "true"
      NVIDIA_VISIBLE_DEVICES: "all"
      DOCLING_SERVE_ARTIFACTS_PATH: "/opt/app-root/src/models"
    runtime: nvidia
    restart: always
    volumes:
      - ./models:/opt/app-root/src/models
```

해당 디렉터리에서 `docker compose up -d`를 실행하여 서비스를 시작합니다. 컨테이너가 준비되면 LightRAG의 `.env`에 다음을 설정합니다:

```bash
DOCLING_ENDPOINT=http://localhost:5001
DOCLING_DO_FORMULA_ENRICHMENT=true
```

이를 통해 LightRAG가 로컬 docling-serve를 통해 문서의 수식을 인식하고 LaTeX 형식으로 출력할 수 있습니다.

### 2.5 파일 처리 옵션

처리 옵션은 멀티모달 분석, 지식 그래프 구축, 텍스트 청킹에 관한 단일 파일의 동작을 제어합니다. 모든 옵션은 선택 사항이며, 기본값은 아래 표에 표시되어 있습니다. 파일당 최대 하나의 청킹 방법(F/R/V/P)을 지정할 수 있으며, 다른 옵션은 임의로 조합할 수 있습니다.

| 옵션 | 유형 | 기본값 | 의미 |
| --- | --- | --- | --- |
| `i` | 멀티모달 | 꺼짐 | 이미지 분석(VLM) 활성화 |
| `t` | 멀티모달 | 꺼짐 | 표 분석(VLM) 활성화 |
| `e` | 멀티모달 | 꺼짐 | 수식 분석(VLM) 활성화 |
| `!` | 파이프라인 | 꺼짐 | 개체/관계 추출 비활성화; 지식 그래프 미구축 (청크 벡터 인덱스는 유지; naive/mix 검색은 계속 작동) |
| `F` | 청킹 | 기본값 | 고정 길이(Fix) 청킹: 레거시 방법, 고정 토큰 길이 또는 구분자로 기계적으로 분할 (구분자로 분할 시 청크 오버랩 없음) |
| `R` | 청킹 | - | 재귀 문자(Recursive) 청킹 (LangChain의 RecursiveCharacterTextSplitter): 구분자 목록 사용 (기본값: `["\n\n","\n","。","！","？","；","，"," ",""]`, 의미 경계 강도 순). 단락(이중 개행)으로 먼저 분할하고, 청크가 여전히 토큰 한도를 초과하면 단일 개행 → 중국어 문말 부호 → 중국어 문중 부호 → 공백 → 문자 단위로 단계적으로 폴백. **기본 계단식에는 중국어 부호가 포함**되어 있어 중국어/혼합 문서가 의미 경계에서 분할됩니다. 영어 `.?!`는 의도적으로 제외합니다 (`0.95`/`e.g.` 오분할 방지). |
| `V` | 청킹 | - | 벡터 시맨틱(Vector) 청킹 (LangChain의 SemanticChunker): 먼저 텍스트를 문장으로 분할하고, 인접 문장의 임베딩을 계산한 뒤, 지정된 임계값 전략(예: 백분위, 표준편차, 사분위범위)에 따라 의미 분기점을 찾아 분할. `SemanticChunker` 자체에는 청크 크기 상한이 없습니다. `chunk_token_size`를 초과하는 의미 청크는 R을 통해 자동으로 재분할됩니다 (V의 비오버랩 의미론 유지). 이 청킹 전략은 절대 오버랩 청크를 생성하지 않습니다. |
| `P` | 청킹 | - | 단락 시맨틱(Paragraph) 청킹 (고유); 먼저 제목으로 분할하며 이전 제목의 끝과 다음 제목의 시작 내용이 섞이지 않도록 엄격히 방지합니다. 명확한 제목 구조로 제목을 정확히 식별할 수 있는 문서 청킹에 적합합니다. 동일 제목 아래 본문이 너무 길어 R로 폴백할 때 `CHUNK_P_OVERLAP_SIZE`에 따라 오버랩을 유지할 수 있습니다. 인접한 대형 표 사이의 연결 텍스트도 그 예산 내에서 주변 표 청크에 반복될 수 있습니다. 이 청킹 방법은 사이드카 디렉터리에 저장된 `lightrag` 콘텐츠에만 적용됩니다. `lightrag` 콘텐츠가 없으면 `R` 청킹으로 저하됩니다. 이 청킹 방법은 `R`이나 `F` 전략보다 오버랩 청크를 훨씬 적게 생성합니다. |

> 전역 멀티모달 스위치 `addon_params["enable_multimodal_pipeline"]`는 더 이상 사용되지 않습니다. 관련 동작은 이제 파일 수준의 `i/t/e` 옵션으로 일괄 제어됩니다. [부록 A](#부록-a-레거시-업그레이드-참고사항)를 참고하세요.

#### 옵션 적용 단계

처리 옵션의 각 문자는 파이프라인의 서로 다른 단계에서 적용됩니다:

| 옵션 | 단계 | 설명 |
| :-: | --- | --- |
| i/t/e | 분석 (멀티모달 분석) | 사이드카의 이미지/표/수식에 VLM 요약 분석을 호출할지 결정. **추출 단계에는 영향 없음**: 콘텐츠 추출 엔진은 문서에 실제로 포함된 내용에 따라 `drawings.json`/`tables.json`/`equations.json` 사이드카 파일을 출력. 따라서 `i`/`t`/`e` 옵션만 조정하면 원본 파일을 다시 파싱하지 않고도 나중에 VLM 분석을 실행할 수 있습니다. |
| ! | 추출 (개체-관계 추출) | 개체/관계 추출 및 그래프 쓰기 건너뜀; 청크는 여전히 벡터 스토어에 기록되어 naive/mix 검색 기능 유지. |
| F/R/V/P | 청킹 (텍스트 청킹) | 사용할 청킹 전략 결정; 파싱 단계 출력에는 영향 없음. |

> 모달리티 가용성은 "사이드카 파일 존재 여부"만으로 신호가 전달됩니다. 콘텐츠 추출 엔진은 meta에 기능을 선언할 필요가 없습니다. 특정 문서에 이미지/표/수식이 없으면 해당 사이드카는 작성되지 않습니다. 사용자가 `i/t/e`를 활성화해도 해당 모달리티는 자동으로 건너뛰지만 `analyze_multimodal`이 해당 문서에 대해 INFO 수준 로그를 기록합니다 (`[analyze_multimodal] sidecar e:equations empty: doc—id ...`). "VLM이 왜 실행되지 않았는가"를 진단하기 쉽습니다. 이는 오류가 아닙니다.

### 2.6 유효성 검사, 우선순위, 폴백

- `LIGHTRAG_PARSER`는 시작 시 엄격하게 유효성이 검사됩니다. 알 수 없는 콘텐츠 추출 엔진, 잘못된 확장자 구문, 지원되지 않는 확장자 명시, 엔드포인트가 없는 외부 엔진, 처리 옵션의 불법 문자는 모두 시작 실패를 유발합니다.
- **와일드카드 규칙이 특정 확장자에 매칭될 때**, 엔진은 두 가지 사용 가능성 검사(`parser_routing._engine_is_usable`)를 통과해야 합니다: (a) 엔진의 기능 테이블이 해당 확장자를 지원하는지, (b) 외부 엔진(`mineru`/`docling`)인 경우 해당 엔드포인트/토큰 환경 변수가 설정되어 있는지. 두 검사 중 하나라도 실패하면 규칙을 건너뛰고 다음 규칙으로 이동합니다.
- 파일명 힌트는 `LIGHTRAG_PARSER`보다 높은 우선순위를 가집니다. 힌트에 지정된 엔진이 해당 확장자를 지원하지 않으면 기본 규칙으로 폴백하여 사용 가능한 엔진을 계속 선택합니다.
- 파일명 힌트가 비어 있지 않은 옵션 문자열을 제공하면 힌트가 우선됩니다. 그렇지 않으면 `LIGHTRAG_PARSER`에서 매칭된 항목의 기본 옵션을 사용합니다. 둘 다 없으면 모든 기본값을 사용합니다.
- 사용 가능한 규칙이 없으면 파일 콘텐츠 추출이 `legacy`로 폴백됩니다. `legacy`도 파일 확장자를 지원하지 않으면 시스템에 오류 항목이 추가되고 업로드된 파일은 `INPUT` 디렉터리에 남습니다.
- F/R/V/P 중 최대 하나만 나타날 수 있습니다. 동일한 옵션을 반복해도 한 번만 적용되지만 오류는 발생하지 않습니다.
- 대소문자 구분: 청킹 옵션 F/R/V/P는 대문자여야 합니다. 다른 옵션 i/t/e는 소문자여야 합니다.
- 대괄호 안에 불법 문자가 나타나면 전체 힌트가 무효화됩니다. 엔진은 기본 규칙에 따르고 옵션은 `LIGHTRAG_PARSER` 기본값 또는 모든 기본값으로 폴백되며 경고도 기록됩니다.
- `P`는 `native`로 추출된 구조화 `LightRAG Document` 결과에만 유효합니다. `legacy` 경로나 비구조화 출력의 경우 자동으로 `R`로 저하되고 경고가 기록됩니다.

## 3. 청커 파라미터 설정 (chunk_options)

### 3.1 process_options와 chunk_options의 역할

`process_options`는 **어떤** 청킹 전략(F/R/V/P)을 선택할지 결정하고, `chunk_options`는 해당 청커가 **어떤 파라미터**를 사용할지 결정합니다. 두 역할은 직교(orthogonal)합니다: 전자는 단일 문자 선택자이고, 후자는 구조화된 딕셔너리입니다.

```
환경 변수                                                  (시작 시 한 번 읽음)
   │
   ▼
addon_params["chunker"]                                   (LightRAG 인스턴스 필드, env에서 레거시 폴백으로 채움)
   │
   ▼  resolve_chunk_options(addon_params, split_by_character=…, split_by_character_only=…)
   │
full_docs[doc_id]["chunk_options"]                       (enqueue 시 동결; 파일별 독립 스냅샷)
   │
   ▼
chunker(tokenizer, content, chunk_token_size, **strategy_kwargs)   (청킹 중 선택자에 의해 디스패치)
```

- **환경 변수**는 `LightRAG.__init__` 단계에서 `addon_params["chunker"]`로 로드됩니다 (전략별 환경 변수는 `default_chunker_config()`가 읽고, `_apply_chunk_size_overlay`가 레거시 환경 변수를 폴백으로 채움).
- **`addon_params["chunker"]`**는 `ObservableAddonParams` 필드입니다. 서버 배포의 경우 새 값이 적용되려면 환경 변수 변경 후 재시작만 하면 됩니다. Python 프로세스 내에서 (재시작 없이) 런타임에 변경하거나 파일별 재정의를 수행하려면 [제8장: Python SDK 호출](#8-python-sdk-호출)을 참고하세요.
- **`full_docs.chunk_options`**는 `apipeline_enqueue_documents` enqueue 시에 동결됩니다: 기본적으로 현장에서 `resolve_chunk_options(self.addon_params, ...)`에 의해 조립됩니다. 호출자가 `chunk_options` 인수를 전달하면 그대로 지속됩니다 (SDK 사용, §8.4 참고).
- **청커 호출**은 `full_docs.chunk_options`에서 해당 하위 딕셔너리를 가져와 `process_options.chunking` 선택자에 따라 F/R/V/P로 디스패치합니다.

### 3.2 환경 변수

아래 표의 모든 변수는 `LightRAG`가 인스턴스화될 때 `addon_params["chunker"]`로 한 번 읽힙니다. 전략별 환경 변수는 `default_chunker_config()`가 읽고, 레거시 환경 변수(`CHUNK_SIZE`/`CHUNK_OVERLAP_SIZE`)는 `_apply_chunk_size_overlay`가 전략 환경 변수나 레거시 생성자 필드가 채우지 않은 슬롯에 채웁니다. 환경 변수 수정 후에는 서비스를 재시작(또는 새 `LightRAG` 인스턴스 생성)해야 적용됩니다. 이미 enqueue된 문서는 동결된 스냅샷을 유지하며 영향받지 않습니다.

| 변수 | 기본값 | 유형 | 범위 |
|---|---|---|---|
| `CHUNK_SIZE` | `1200` | int | 레거시 최상위 `chunk_token_size` 폴백; 전략별 환경 변수 및 SDK 경로의 `addon_params["chunker"]["chunk_token_size"]` 설정보다 낮은 우선순위 |
| `CHUNK_OVERLAP_SIZE` | `100` | int | 레거시 오버랩 폴백; 전략별 환경 변수(`CHUNK_F_OVERLAP_SIZE`/`CHUNK_R_OVERLAP_SIZE`/`CHUNK_P_OVERLAP_SIZE`)도 SDK 경로의 `LightRAG(chunk_overlap_token_size=…)`도 없을 때 채워짐 |
| `CHUNK_F_OVERLAP_SIZE` | 미설정 | int | F 전략별 오버랩; 레거시 생성자 필드 및 `CHUNK_OVERLAP_SIZE`보다 높은 우선순위 |
| `CHUNK_F_SPLIT_BY_CHARACTER` | (미설정 = `null`) | str? | F 사전 분할 구분자; `null`/빈 문자열 = 토큰 윈도우만으로 분할 |
| `CHUNK_F_SPLIT_BY_CHARACTER_ONLY` | `false` | bool | F 엄격 모드: 이차 토큰 분할 없음; 크기 초과 시 오류 발생 |
| `CHUNK_R_SIZE` | 미설정 | int | R 전략별 `chunk_token_size`; 최상위 레거시 폴백(`CHUNK_SIZE` 및 SDK 경로의 `LightRAG(chunk_token_size=…)`)보다 높은 우선순위. 미설정 시 R이 최상위 확인된 값을 상속. |
| `CHUNK_R_OVERLAP_SIZE` | 미설정 | int | R 전략별 오버랩; 레거시 생성자 필드 및 `CHUNK_OVERLAP_SIZE`보다 높은 우선순위 |
| `CHUNK_R_SEPARATORS` | `["\n\n","\n","。","！","？","；","，"," ",""]` | JSON 배열 문자열 | R 구분자 계단식, 의미 경계 강도 순. 기본값에는 중국어 문말 부호(`。！？`) 및 문중 부호(`；，`)가 포함되어 있어 중국어/혼합 문서가 의미 경계에서 분할됩니다. 영어 `.?!`는 숫자와 약어 오분할 방지를 위해 의도적으로 제외합니다. |
| `CHUNK_V_SIZE` | 미설정 | int | V 전략별 `chunk_token_size` (하드 상한; 초과 시 R을 통해 자동 재분할); 최상위 레거시 폴백보다 높은 우선순위. 미설정 시 V가 최상위 확인된 값을 상속. |
| `CHUNK_V_BREAKPOINT_THRESHOLD_TYPE` | `percentile` | str | V 임계값 유형; `percentile`/`standard_deviation`/`interquartile`/`gradient` 가능 |
| `CHUNK_V_BREAKPOINT_THRESHOLD_AMOUNT` | (미설정 = `null`) | float? | V 임계값 크기; `null`이면 LangChain이 유형별 기본값 선택 (예: percentile=95) |
| `CHUNK_V_BUFFER_SIZE` | `1` | int | V 문장 버퍼 윈도우; 거리 계산 시 병합할 인접 문장 수 |
| `CHUNK_V_SENTENCE_SPLIT_REGEX` | `(?<=[.?!])\s+\|(?<=[。？！])` | str | V의 문장 분할 정규식, LangChain의 `SemanticChunker`에 전달. 기본값은 영어 `.?!`(오분할 방지를 위해 뒤에 공백 필요)와 중국어 `。？！`(공백 불필요) 모두 인식. 환경 변수 값은 원시 정규식 문자열입니다; JSON 인용 불필요. |
| `CHUNK_P_SIZE` | `2000` (`DEFAULT_CHUNK_P_SIZE`) | int | P 전략별 `chunk_token_size`. R/V와 달리, P는 미설정 시 최상위 `CHUNK_SIZE`/`LightRAG(chunk_token_size=…)`를 상속하지 않습니다. 단락 시맨틱 병합은 관련 단락을 함께 유지하기 위해 전역 기본값보다 더 많은 여유 공간이 필요하므로 슬롯은 항상 `DEFAULT_CHUNK_P_SIZE`(2000)를 유지합니다. |
| `CHUNK_P_OVERLAP_SIZE` | 미설정 | int | P 전략별 오버랩; 레거시 생성자 필드 및 `CHUNK_OVERLAP_SIZE`보다 높은 우선순위. 동일 JSONL 콘텐츠 라인 내 긴 본문이 R로 폴백할 때 텍스트 오버랩에 사용되며, 인접 대형 표 청크에 복사되는 연결 텍스트의 각 측 예산으로도 사용됩니다. |

### 3.3 우선순위 체인

각 청킹 슬롯의 최종 값은 특이성 순서 체인 (높음 → 낮음)으로 결정됩니다:

1. **`addon_params["chunker"]` 명시적 값** — 생성 시 명시적으로 작성되거나 SDK 경로를 통해 런타임에 설정된 필드 값 (§8.3 참고). 서버 전용 배포는 보통 이 단계에 해당하지 않습니다. 가장 직접적이며 모든 것보다 우선합니다.
2. **전략별 환경 변수** — 예: `CHUNK_F_OVERLAP_SIZE`/`CHUNK_R_OVERLAP_SIZE`/`CHUNK_P_OVERLAP_SIZE`/`CHUNK_R_SIZE`/`CHUNK_V_SIZE`/`CHUNK_P_SIZE` (현재 전략별 `CHUNK_F_SIZE` 없음; F는 최상위 `chunk_token_size` 재사용). ①로 이미 채워지지 않은 슬롯에만 채워집니다.
3. **레거시 생성자 필드** — `LightRAG(chunk_token_size=…, chunk_overlap_token_size=…)`; SDK 경로에서만 유효, §8.2 참고. 전략 무관, "대략적인 기본값", 여전히 비어 있는 슬롯만 채웁니다.
4. **레거시 환경 변수** — `CHUNK_SIZE`/`CHUNK_OVERLAP_SIZE`. 최후의 폴백.

예시: `CHUNK_R_OVERLAP_SIZE=42` + `LightRAG(chunk_overlap_token_size=2)` → R 하위 딕셔너리 `chunk_overlap_token_size=42` (전략 환경 변수 우선), F/P 하위 딕셔너리 `chunk_overlap_token_size=2` (F/P별 환경 변수 없음; 레거시 생성자 필드 채워짐).

**P의 `chunk_token_size` 특별 케이스**: P `chunk_token_size` 슬롯은 전체 4단계 체인을 따르지 않습니다. ①이 명시적으로 제공되지 않으면 `CHUNK_P_SIZE` 환경 변수 → `DEFAULT_CHUNK_P_SIZE`(2000)로 직접 결정되며, ③ 레거시 생성자 필드 `LightRAG(chunk_token_size=…)`와 ④ 레거시 환경 변수 `CHUNK_SIZE`를 **건너뜁니다**.

3가지 의미 보장:

1. **재현성**: 환경 변수를 변경하고 재시작해도 이전 문서는 enqueue 시점의 스냅샷으로 청킹됩니다. 결과가 변하지 않습니다.
2. **재개 일관성**: 브랜치 B 재개(콘텐츠 이미 추출, 현재 `process_options`로 청킹 재실행)도 `full_docs.chunk_options`를 읽으므로 환경 변수 드리프트가 일관성을 깨지 않습니다.
3. **파일별 맞춤화**: 호출자가 각 파일에 다른 `chunk_options`를 전달할 수 있습니다 (일반적인 사용: 관리 UI가 특정 파일에 대해 구분자나 V 임계값을 개별적으로 설정). SDK 경로의 입력 의미론입니다; §8.4 참고.

### 3.4 필드 구조

`addon_params["chunker"]` (인스턴스 필드)는 모든 4가지 전략의 하위 딕셔너리를 런타임 기준선으로 유지합니다. `full_docs[doc_id]["chunk_options"]`는 **슬림 스냅샷**입니다. enqueue 시에 `process_options`로 선택된 전략 하위 딕셔너리만 유지되고 (기본값 F), 다른 전략의 파라미터는 처리 단계에서 읽지 않으므로 버려집니다. 재파싱 시 `process_options`와 `chunk_options`가 함께 재작성되어 이전 전략 파라미터의 잔재를 방지합니다.

**`addon_params["chunker"]` 전체 기준선** (SDK를 통해 런타임에 수정 가능, 이후 enqueue에 영향):

```jsonc
{
  "chunk_token_size": 1200,                                   // 공통 토큰 상한
  "fixed_token": {                                            // F 전용
    "chunk_overlap_token_size": 100,
    "split_by_character": null,
    "split_by_character_only": false
  },
  "recursive_character": {                                    // R 전용
    "chunk_token_size": 1200,                                 // 선택 사항; 생략 시 최상위 chunk_token_size 상속
    "chunk_overlap_token_size": 100,
    "separators": ["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""]   // 기본 계단식에 중국어 부호 포함
  },
  "semantic_vector": {                                        // V 전용
    "chunk_token_size": 1200,                                 // 선택적 하드 상한; 초과 시 R을 통해 재분할
    "breakpoint_threshold_type": "percentile",                // percentile | standard_deviation | interquartile | gradient
    "breakpoint_threshold_amount": null,                      // null = LangChain 기본값
    "buffer_size": 1,
    "sentence_split_regex": "(?<=[.?!])\\s+|(?<=[。？！])"      // 기본 정규식: 영어/중국어 문장 부호 모두 처리
  },
  "paragraph_semantic": {                                     // P 전용
    "chunk_token_size": 2000,                                 // 생략 시 CHUNK_P_SIZE 또는 DEFAULT_CHUNK_P_SIZE(2000)에서 결정;
                                                              // 공통 chunk_token_size를 상속하지 않음
    "chunk_overlap_token_size": 100                           // 생략 시 레거시 오버랩 결정 체인 상속
  }
}
```

**`full_docs[doc_id]["chunk_options"]` 슬림 스냅샷** (선택자에 의해 투영; 아래 예시는 `process_options="R"`):

```jsonc
{
  "chunk_token_size": 1200,                                   // 공통 토큰 상한 (최상위 폴백으로 유지)
  "recursive_character": {                                    // 유일하게 유지되는 전략 하위 딕셔너리
    "chunk_overlap_token_size": 100,
    "separators": ["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""]
  }
}
```

선택자 → 하위 딕셔너리 매핑: F → `fixed_token`, R → `recursive_character`, V → `semantic_vector`, P → `paragraph_semantic`; 선택자 없이는 F가 기본값. 각 하위 딕셔너리는 해당 청커 함수의 키워드 전용 파라미터와 일대일 대응합니다. 새 파라미터 추가 시 디스패처 변경 없이 청커 함수에 kwarg만 추가하면 됩니다.

### 3.5 누락 필드의 하위 호환성

enqueue 시점의 이전 문서에는 아직 `chunk_options` 필드가 없습니다. 청킹 중에 디스패처가 현재 `process_options`에 따라 `resolve_chunk_options(self.addon_params, process_options=…)`를 호출하여 슬림 스냅샷으로 폴백합니다. 업그레이드 후에는 이전 문서에 슬림 `chunk_options` 스냅샷을 부여하기 위해 재처리를 한 번 실행하는 것이 권장됩니다 (현재 `process_options`와 정렬).

## 4. 스토리지 및 디렉터리 레이아웃

### 4.1 `full_docs` 필드

파일 enqueue 및 추출 결과는 `full_docs`에 기록됩니다:

| 필드 | 설명 |
| --- | --- |
| `file_path` | 파일명의 베이스네임 (디렉터리 제외). **사용자가 제공한 원본 이름(대괄호 힌트 포함)을 그대로 보존**합니다. 예: `abc.[native-iet].docx`는 그대로 저장됩니다. 유효한 소스가 없으면 `unknown_source`로 저장됩니다. 관리 UI가 사용자의 원본 명명 의도를 직접 표시할 수 있도록 파일명 힌트가 제거되지 않습니다. |
| `canonical_basename` | 처리 힌트가 제거된 정규화 베이스네임 (예: `abc.docx`). 파일명 중복 제거는 이 필드를 인덱스 키로 사용하여 `abc.docx`와 `abc.[native-iet].docx`가 동일한 논리 문서로 처리됩니다. |
| `source_path` | enqueue 시 제공된 원본 경로 (디렉터리 구분자를 포함하거나 절대 경로인 경우에만 기록됨). `native`/`mineru`/`docling` 파서가 실제 파일을 찾는 데 사용합니다. |
| `parse_format` | 콘텐츠 형식: `pending_parse`, `raw`, `lightrag`. |
| `content` | `raw`이면 추출된 텍스트를 보유하고, `pending_parse`이면 빈 문자열이며, `lightrag`이면 `{{LRdoc}}`으로 시작하는 **완전히 병합된 텍스트**를 보유합니다 (`.blocks.jsonl`의 모든 `type=="content"` 라인의 본문 세그먼트 연결). 청킹 중에 `parse_native`는 접두사를 제거하고 청킹 함수에 전달하며, `raw`와 정확히 동일한 코드 경로를 따릅니다. |
| `content_hash` | 콘텐츠의 MD5, 파일명 간 중복 제거에 사용됩니다. `parse_format=raw`이면 `sanitize_text_for_encoding` 후 텍스트의 해시이고, `parse_format=lightrag`이면 `*.blocks.jsonl` 파일의 해시이며, `parse_format=pending_parse`이면 작성되지 않고 추출 완료 후 채워집니다. |
| `lightrag_document_path` | `parse_format=lightrag`이면 구조화된 LightRAG Document 경로를 저장합니다. 새 레코드는 `INPUT_DIR`에 대한 상대 경로를 선호합니다. 예: `__parsed__/report.docx.parsed/report.blocks.jsonl`. 경로의 하위 디렉터리와 블록 파일명은 모두 정규화 베이스네임(힌트 제외)을 사용합니다. |
| `parse_engine` | 추출을 실제로 완료한 엔진: `legacy`, `native`, `mineru`, `docling`. 추출 대기 파일의 경우 대상 엔진을 일시적으로 저장할 수도 있습니다. |
| `process_options` | enqueue 시 기록된 원본 처리 옵션 문자열 (엔진 이름과 구분자 `-` 제외). 예: `"iet"`, `"R!"`, `""`. 하위 단계는 이 필드를 이미지/표/수식 분석(`i/t/e`) 활성화 여부, 지식 그래프 구축 비활성화(`!`) 여부, 청킹 방법(`F/R/V/P`)을 결정하는 권위 있는 소스로 사용합니다. 빈 문자열은 모든 기본값과 동일합니다. |
| `chunk_options` | enqueue 시 청커 파라미터의 **동결** 스냅샷 (슬림 딕셔너리: `process_options`로 선택된 전략 하위 딕셔너리만 유지, 나머지 버림). SDK 경로 호출자가 전달하거나, 인스턴스 필드(환경 변수 기본값 포함)에서 `resolve_chunk_options(self.addon_params, process_options=…)`가 조립합니다. `process_options`는 어떤 청킹 전략(F/R/V/P)을 선택할지 결정하고, `chunk_options`는 해당 청커가 어떤 파라미터를 사용할지 결정합니다. 재파싱 시 `process_options`와 함께 재작성됩니다. |

`pending_parse`는 파일이 enqueue되었지만 추출이 아직 완료되지 않았음을 나타냅니다. 성공적으로 추출되면 `raw` 또는 `lightrag`으로 재작성되고 `content_hash`가 채워집니다. 추출 실패 시 `pending_parse`와 빈 `content`가 유지되어 이후 문제 해결 및 재시도가 용이합니다.

### 4.2 `__parsed__` 디렉터리 구조

`__parsed__`는 입력 디렉터리 옆에 위치한 보관 및 분석 결과 디렉터리입니다. 이미 처리된 원본 문서와 구조화된 파싱으로 생성된 `LightRAG Document` 파일 및 이미지 에셋을 저장합니다.

- 원본 파일 보관: `legacy` 로컬 추출 성공 및 enqueue 완료 후 원본 파일이 인접 `__parsed__` 디렉터리로 이동됩니다. `native`/`mineru`/`docling`은 파이프라인 파싱을 위해 원본 파일을 먼저 유지하고, 성공적으로 파싱하여 `full_docs`에 기록한 후에만 `__parsed__`로 이동합니다. **보관 시 원본 파일명(힌트 포함)이 보존됩니다.** 예: `report.[native-iet].docx`는 `__parsed__/report.[native-iet].docx`로 보관됩니다.
- 분석 결과 디렉터리: 구조화된 파싱 결과는 **정규화된 파일명**(힌트 제거) + `.parsed` 접미사로 명명된 하위 디렉터리에 기록됩니다. 예: `report.docx`, `report.[native].docx`, `report.[native-iet].docx`의 분석 결과는 모두 `__parsed__/report.docx.parsed/`에 기록됩니다.
- 분석 결과 파일: LightRAG Document 블록 파일과 사이드카는 정규화된 파일명 스템으로 명명됩니다. 예: `__parsed__/report.docx.parsed/report.blocks.jsonl`. 동일 디렉터리에 `report.tables.json`, `report.drawings.json`, `report.equations.json`, `report.blocks.assets/` 이미지 에셋 디렉터리도 포함될 수 있습니다. **사이드카 생성 여부는 문서 콘텐츠에 의해 결정됩니다**: 파서는 문서에 실제로 표/이미지/수식이 포함된 경우에만 해당 파일을 작성합니다.
- 파싱 실패 시 원본 파일은 이동되지 않아 설정 수정 및 재처리가 용이합니다.
- `/documents/scan`이 이미 `PROCESSED`인 동일 이름 파일을 발견하면 입력 파일을 이미 처리된 것으로 취급하고 `__parsed__`로 이동하며 새 문서로 enqueue하지 않습니다.
- `/documents/scan`이 동일 스캔에서 같은 정규화 이름을 공유하는 여러 파일을 발견하면 지원되는 엔진 힌트가 있는 파일을 우선합니다. 힌트가 있는 변형이 없으면 정렬 순서로 첫 번째 파일을 처리합니다.
- 파싱 중에 중복 콘텐츠 해시가 발견되면 입력 파일도 `__parsed__`로 이동됩니다. 이 `doc_status` 항목은 추적을 위해 `FAILED duplicate`로 유지됩니다.
- 파일 이동은 현재 입력 파일에만 작용하며 기존 문서 소스 파일을 덮어쓰거나 이동하지 않습니다. 대상에 동일 이름 파일이 이미 존재하면 시스템이 자동으로 `_001`, `_002` 등을 추가합니다.

### 4.3 MinerU 원시 아티팩트 디렉터리 `<base>.mineru_raw/`

`mineru` 엔진은 파싱 중에 MinerU 서비스가 반환한 전체 아티팩트(`content_list.json` + 선택적 `full.md`/`middle.json`/`layout.pdf`/`images/` 등)를 `__parsed__/<canonical filename>.mineru_raw/` 디렉터리에 기록하고, `_manifest.json`을 무결성 검증 파일로 작성합니다.

설계 목표:

- **중복 업로드 방지**: 동일 파일을 다시 파싱할 때 소스 파일의 콘텐츠 해시+크기를 `_manifest.json`에 대해 먼저 검증합니다. 적중 시 MinerU 서비스 호출을 건너뛰고 로컬 `content_list.json`을 어댑터 → SidecarWriter에 직접 공급합니다.
- **진단 정보 보존**: MinerU가 잘못 파싱하거나 하위 사이드카 필드가 비정상인 경우 `*.mineru_raw/`로 가서 원본 content_list와 이미지 에셋을 비교할 수 있습니다.
- **객체 추적 지원**: MinerU가 생성한 `drawings.json`/`tables.json`/`equations.json`은 `self_ref`에 `content_list.json#/N`을 저장하며, 이를 통해 해당 MinerU 원본 객체와 `page_idx`/`bbox` 등을 조회할 수 있습니다.

`_manifest.json` 무효화 조건 (하나라도 해당되면 캐시 미스):

- 소스 파일 크기 또는 sha256이 매니페스트와 불일치;
- `MINERU_ENGINE_VERSION` 환경 변수와 매니페스트에 기록된 `engine_version`이 모두 비어 있지 않고 불일치;
- 현재 `MINERU_API_MODE`와 매니페스트에 기록된 `api_mode`가 모두 비어 있지 않고 불일치;
- `content_list.json` 크기 또는 sha256이 매니페스트와 불일치.

### 4.4 Docling 원시 아티팩트 디렉터리 `<base>.docling_raw/`

`docling` 엔진은 파싱 중에 docling-serve가 반환한 zip 아티팩트(DoclingDocument JSON, Markdown, 참조 이미지)를 `__parsed__/<canonical filename>.docling_raw/` 디렉터리에 추출하고, `_manifest.json`을 무결성 검증 파일로 작성합니다. 후속 파싱에서 IR 빌더는 해당 디렉터리의 `.json` 파일을 읽고 `DoclingIRBuilder`에 공급하며 docling-serve를 더 이상 호출하지 않습니다.

디렉터리 레이아웃:

```text
__parsed__/<base>.docling_raw/
├── _manifest.json
├── <base>.json        # DoclingDocument JSON (pages[].image base64 포함)
├── <base>.md          # Markdown 형식, 인간 검사용
└── artifacts/
    └── image_*.png    # pictures[*].image.uri가 참조하는 이미지 에셋
```

## 5. 문서 중복 탐지 규칙

파일 업로드, 파일 파싱 enqueue, 텍스트 API는 "파일명 + 콘텐츠 해시" 두 가지 게이트에 대해 중복을 확인합니다. 어느 하나에 해당하면 중복으로 간주되어 기존 `full_docs`를 덮어쓰지 않고 `FAILED` 레코드가 기록됩니다.

### 5.1 파일명(basename) 중복 제거

- 확인 세분성은 디렉터리 경로와 워크스페이스 경로를 제외한 베이스네임입니다. 예: `/data/a.pdf`, `inputs/a.pdf`, `a.pdf`는 모두 동일한 파일명 `a.pdf`로 간주됩니다.
- 파일명 중복 제거는 `canonical_basename`을 인덱스로 사용합니다. 지원되는 엔진 처리 힌트가 비교 전에 제거되므로 `abc.docx`, `abc.[native].docx`, `abc.[native-iet].docx`는 동일한 이름으로 간주됩니다.
- 일반 업로드, 텍스트 API, 코어 enqueue API의 경우, `doc_status`에 동일 이름 파일이 이미 존재하면(`PENDING`, `PARSING`, `ANALYZING`, `PROCESSING`, `FAILED`, `PROCESSED` 상태 불문) 동일 이름 파일은 중복으로 간주됩니다.
- `/documents/scan` 디렉터리 스캔의 경우:
  - 동일 스캔에서 여러 파일이 동일한 정규화 이름을 공유하면 지원되는 엔진 힌트가 있는 파일이 우선 처리됩니다.
  - 동일 이름 레코드가 이미 `PROCESSED`이면 방금 스캔된 파일을 이미 처리된 것으로 취급합니다. 시스템이 경고를 발행하고 입력 파일을 인접 `__parsed__` 디렉터리로 이동하며 enqueue를 건너뜁니다.
  - 동일 이름 레코드가 `PROCESSED`가 아니면 스캔된 파일이 단순히 동일 이름 때문에 건너뛰어지지 않지만, 기존 레코드를 재추출하거나 덮어쓰지도 않습니다.

### 5.2 콘텐츠 해시 중복 제거

- 파일명은 다르지만 추출된 콘텐츠가 동일한 문서도 중복으로 간주됩니다. 여기서의 해시는 원본 파일 바이트의 해시가 아니라 설정된 추출 엔진으로 얻은 최종 텍스트 또는 LightRAG Document의 콘텐츠 해시입니다.
- `legacy` 경로는 텍스트를 로컬에서 추출하고 enqueue 중에 콘텐츠 해시를 중복 제거합니다. 적중 시 이 레코드는 `FAILED duplicate`로 기록되며 새 `full_docs`, 청크, 그래프 데이터가 생성되지 않습니다.
- `native`/`mineru`/`docling` 경로는 먼저 `pending_parse`로 enqueue하고, 파싱이 완료되어 `content_hash`가 채워진 후 다른 문서가 동일한 해시를 이미 가지고 있으면 이 레코드는 분석, 청킹, 개체 추출, 그래프 쓰기에 진입하기 전에 중단됩니다.
- 중복 레코드는 진단을 위해 `metadata.duplicate_kind`에 `filename` 또는 `content_hash`로 표시됩니다.

## 6. 파이프라인 동시성 및 재진입 제약

`scan`/`upload`/`insert`가 진행 중인 파이프라인의 `doc_status`/`full_docs` 레코드를 덮어쓰는 것을 방지하기 위해 모든 쓰기 진입점은 `pipeline_status` 공유 딕셔너리를 통해 조율합니다.

### 6.1 `pipeline_status` 필드

| 필드 | 의미 |
| --- | --- |
| `busy` | 일반 파이프라인 바쁨 플래그. 처리 루프와 파괴적 작업(지우기/삭제) 모두 설정합니다. **`busy=True`(처리 루프) 단독으로는 enqueue를 차단하지 않습니다** — 루프는 배치당 `doc_status` 스냅샷을 가져오고 배치 사이에 `request_pending`을 확인하여 새로 도착한 작업을 확인합니다. |
| `destructive_busy` | `busy`의 파괴적 하위집합: `/documents/clear` 또는 `/documents/{doc_id}`(삭제)가 스토리지를 삭제/소스 파일을 제거하고 있습니다. 예약과 enqueue 최후 방어선 모두 거부합니다. |
| `scanning` | `/documents/scan` 백그라운드 작업이 실행 중입니다 (전체 생명주기: 분류 단계 + 처리 단계). `/scan` 엔드포인트만 중복 스캔을 거부하는 데 사용합니다. upload/insert는 차단하지 않습니다. |
| `scanning_exclusive` | `scanning`의 배타적 하위집합: 스캔의 **분류 단계** 중에만 True입니다. 예약과 enqueue 최후 방어선 모두 거부합니다. 분류 후 플래그가 즉시 지워지고 스캔이 처리 단계에 진입하면 동시 업로드가 허용됩니다. |
| `pending_enqueues` | `_reserve_enqueue_slot`을 통과했지만 백그라운드 작업이 아직 완료되지 않은 업로드/삽입 호출 수. 스캔 엔드포인트만 배타적 잠금을 획득할지 결정하는 데 사용합니다. |
| `request_pending` | 실행 중인 처리 루프가 다른 라운드를 스캔하도록 촉구하는 신호. enqueue가 `busy=True` 상태에서 `doc_status`에 기록한 후 설정합니다. |

### 6.2 진입점 동작

| 진입점 | 조건 | 동작 |
| --- | --- | --- |
| `/documents/upload`/`/documents/text`/`/documents/texts` | `scanning_exclusive=True` 또는 `destructive_busy=True` | HTTP 409 발생; 파일 저장 안 함, enqueue 호출 안 함 |
| 동일 | 그 외 (순수 `busy=True`, 스캔 처리 단계 `scanning=True`이지만 `scanning_exclusive=False` 포함) | 잠금 내: `pending_enqueues++` 슬롯 예약 → 엄격한 이름 사전 확인 → 파일 저장 → 백그라운드 작업 스케줄링; 백그라운드 작업이 `finally`에서 슬롯 해제 |
| `/documents/scan` | `busy=True` 또는 `scanning=True` 또는 `pending_enqueues>0` | 경고 발행 후 즉시 `scanning_skipped_pipeline_busy` 반환; 백그라운드 작업 스케줄링 안 함 |
| 동일 | 모두 유휴 | 잠금 내 `scanning=True` 설정 후 스케줄링; 작업이 완료 시 `finally`에서 플래그 지움 |
| `/documents/clear`/`/documents/delete_document` | `busy=True` 또는 `scanning=True` 또는 `pending_enqueues>0` | 엔드포인트가 동기적으로 `status="busy"` 반환, 백그라운드 작업 스케줄링 안 함 |
| 동일 | 모두 유휴 | 엔드포인트가 잠금 내에서 **동기적으로** `busy=True` + `destructive_busy=True` 설정, 백그라운드 작업의 finally가 두 플래그를 지움 |

### 6.3 `busy`가 더 이상 enqueue를 차단하지 않는 이유

이전 버전에서는 `busy=True`가 항상 새 enqueue를 거부했습니다. 이제 다음 메커니즘으로 동시 enqueue가 가능합니다:

1. **쓰기 순서 일관성 보장**: `apipeline_enqueue_documents`는 항상 먼저 `full_docs`를 업서트하고 그다음 `doc_status`를 업서트합니다.
2. **배치 수준 스냅샷**: 각 처리 루프 배치는 `get_docs_by_statuses` 스냅샷을 한 번 가져옵니다. 새로 기록된 `PENDING` 행은 현재 배치를 방해하지 않으며, 다음 라운드에서 `request_pending`을 통해 새 작업을 확인합니다.
3. **`request_pending`이 이를 위해 설계됨**: 이 메커니즘은 "실행 중에 새 작업이 도착"하도록 설계되었습니다.

이 메커니즘으로 **사용자는 긴 배치 처리 중에도 계속 새 문서를 업로드**할 수 있습니다.

### 6.4 파이프라인 동시성 파라미터

```
          ┌─ q_native  ──► [native 파서  × N1] ─┐
PENDING ─►├─ q_mineru  ──► [mineru 파서  × N2] ─┼─► q_analyze ─►[분석기 × N4] ─► q_process ─►[처리기 × N5]
          └─ q_docling ──► [docling 파서 × N3] ─┘
```

| 환경 변수 | 기본값 | 효과 | 조정 권고 |
| --- | --- | --- | --- |
| `MAX_PARALLEL_PARSE_NATIVE` | `5` | N1: native 파싱 동시 워커 수 | 순수 CPU, 메모리 사용 낮음; CPU 코어 수까지 올릴 수 있음 |
| `MAX_PARALLEL_PARSE_MINERU` | `1` | N2: MinerU 파싱 동시 워커 수 | MinerU는 GPU/CPU 사용량 상당; **직렬 기본값이 가장 안정적**. 로컬 배포 및 VRAM 여유 시 2~3 설정 가능; 공식 클라우드 서비스 이용 시 적절히 올릴 수 있음 (클라우드 할당량에 따름). |
| `MAX_PARALLEL_PARSE_DOCLING` | `1` | N3: Docling 파싱 동시 워커 수 | Docling도 리소스 민감도가 높습니다; **직렬 기본값이 가장 안정적**. 로컬 배포 및 CPU/GPU 여유 시 2~3 설정 가능. |
| `MAX_PARALLEL_ANALYZE` | `5` | N4: 멀티모달 분석 동시 워커 수 | VLM 할당량을 직접 소비합니다. VLM 서비스 동시성 상한 이하로 권장. |
| `MAX_PARALLEL_INSERT` | `2` | N5: 개체/관계 추출 + 수집 단계 동시 문서 수 | `MAX_ASYNC / 3` 권장, 2~10 범위. 이 단계는 문서당 여러 LLM 호출을 트리거합니다; 너무 높게 설정하면 LLM 속도 제한에 부딪힙니다. |
| `QUEUE_SIZE_DEFAULT` | `100` | 파싱/분석 단계 간 바인딩 큐 용량 | 일반적으로 조정 불필요. 매우 큰 배치(수천 개 이상)에는 올릴 수 있고, 메모리가 부족할 때는 낮추세요. |
| `QUEUE_SIZE_INSERT` | `4` | 분석 → 처리 단계 간 큐 용량 | 처리 단계는 파이프라인에서 가장 느리고 메모리 집약적입니다. 큐는 의도적으로 작아서 업스트림에 백프레셔를 제공하고 메모리 팽창을 방지합니다. |

## 7. 시작 시 파이프라인 재개 규칙

`apipeline_process_enqueue_documents`가 시작될 때마다 `PARSING`/`ANALYZING`/`PROCESSING`/`PENDING`/`FAILED` 상태의 모든 문서를 가져와 처리를 계속합니다. 재개 경로는 **"콘텐츠가 추출되었는지 여부"에 따라 분기**되어 어떤 문서든 현재 `process_options`로 재개될 때 멱등적인 결과를 보장합니다.

### 7.1 "콘텐츠가 추출되었는지" 판단

`full_docs[doc_id]` 읽기:

| `parse_format` | 판정 |
| --- | --- |
| `lightrag`이고 `lightrag_document_path` 파일이 존재 | ✅ 추출됨 |
| `raw`이고 `content`가 비어 있지 않음 | ✅ 추출됨 |
| 기타 (`pending_parse` 포함, 레코드 없음) | ❌ 미추출 |

### 7.2 브랜치 A: 미추출

전체 파이프라인 진행 (`parse_native`/`parse_mineru`/`parse_docling` → `analyze_multimodal` → 청킹 → 개체 추출). 각 단계의 동작은 `full_docs.process_options`에 의해 결정됩니다. 이것이 "최초 enqueue"의 정상 흐름입니다.

### 7.3 브랜치 B: 이미 추출됨

**항상 파싱 건너뜀** (`parse_*` 재호출 안 함), ANALYZING 단계부터 재시작, 이전 청크/개체를 지우고 현재 `process_options`에 따라 재실행합니다:

| 하위 단계 | 동작 |
| --- | --- |
| 엔진 비교 | `process_options`가 암시하는 엔진 ≠ `full_docs.parse_engine`이면 **경고만** 발행하고 재파싱하지 않습니다. 추출된 콘텐츠는 불변의 사실입니다. 엔진을 전환하려면 전체 문서를 삭제하고 다시 업로드하세요. |
| 이전 청크/개체/관계 정리 | `status_doc.chunks_list`를 읽어 이전 청크 ID 집합을 수집하고 `_purge_doc_chunks_and_kg(doc_id, chunk_ids)` 호출 |
| `analyze_multimodal` | 활성화된 모달리티에 대해 매번 실행 시 사이드카 항목 분석을 재계산하고 기존 `llm_analyze_result`를 덮어씁니다. LLM 분석 캐시는 여전히 적용됩니다. |
| 재청킹 | 새 `process_options.chunking`으로 전략 선택, 파라미터는 `full_docs.chunk_options`에서 읽음 (enqueue 스냅샷; 재개 시 덮어쓰지 않음; 환경 변수 변경이 이전 문서에 영향 없음) |
| 개체 추출 / KG 건너뜀 | 새 `process_options.skip_kg`에 의해 결정 |

## 8. Python SDK 호출

이 챕터는 통합을 위해 **`LightRAG` 클래스를 직접 임포트**하는 개발자를 대상으로 합니다. 런타임 API, 생성자 파라미터, 서버 배포에서 사용하지 않는 제거된 레거시 인터페이스를 다룹니다.

### 8.1 대상 독자

```python
from lightrag import LightRAG
rag = LightRAG(working_dir="./rag_storage", ...)
await rag.initialize_storages()
await rag.ainsert("text", file_paths="doc.pdf")
```

이 호출 방식은 서버 경로와 다음 면에서 다릅니다: 프로세스를 재시작하지 않고 `addon_params["chunker"]`를 변경할 수 있고, `apipeline_enqueue_documents`에 파일별 `chunk_options`를 전달할 수 있으며, `ainsert` 호출에서 F 전략의 사전 분할 파라미터를 동적으로 재정의할 수 있습니다.

### 8.2 LightRAG 생성자 파라미터

`LightRAG(chunk_token_size=…, chunk_overlap_token_size=…)`는 §3.3 우선순위 체인의 **3단계**입니다: "레거시 생성자 필드". 전략 무관, 대략적인 기본값, 여전히 비어 있는 슬롯만 채웁니다.

- `addon_params["chunker"]` 명시적 값(§8.3)과 전략별 환경 변수(§3.2)보다 낮은 우선순위.
- 레거시 환경 변수 `CHUNK_SIZE`/`CHUNK_OVERLAP_SIZE`보다 높은 우선순위.

### 8.3 런타임에 `addon_params["chunker"]` 수정

`addon_params["chunker"]`는 `ObservableAddonParams` 필드입니다. **런타임에 수정 가능**합니다:

```python
rag.addon_params["chunker"]["recursive_character"]["separators"] = ["##", "\n", " "]
```

수정 후 **이후 enqueue**는 새 기본값을 얻습니다. 이미 enqueue된 문서는 enqueue 시점의 스냅샷을 유지합니다 (§3.3의 3가지 의미 보장 참고). 이것이 §3.3 우선순위 체인의 1단계입니다: "`addon_params["chunker"]` 명시적 값", 모든 것보다 우선합니다.

서버 배포에는 이 기능이 없습니다. 환경 변수 변경 후 서비스를 재시작해야 적용됩니다.

### 8.4 `apipeline_enqueue_documents(chunk_options=…)`

`apipeline_enqueue_documents`는 선택적 `chunk_options` 인수를 받습니다. 호출자가 `dict`/`list[dict]`를 전달하면 현재 문서의 `process_options`로 투영하여 슬림 스냅샷으로 만든 후 `full_docs[doc_id]["chunk_options"]`에 지속됩니다. 전달하지 않으면 `resolve_chunk_options(self.addon_params, process_options=…)`가 현장에서 조립합니다.

일반적인 사용:

```python
await rag.apipeline_enqueue_documents(
    input=["text A", "text B"],
    file_paths=["a.[native-R].txt", "b.txt"],
    process_options=["R", ""],
    chunk_options=[
        {"chunk_token_size": 800, "recursive_character": {"separators": ["\n\n", "\n"]}},
        {"chunk_token_size": 1500},
    ],
)
```

### 8.5 `ainsert(split_by_character=…, split_by_character_only=…)`

`LightRAG.ainsert(split_by_character=…, split_by_character_only=…)` 런타임 파라미터는 enqueue 시에 `resolve_chunk_options`에 의해 `chunk_options.fixed_token`으로 재정의됩니다:

- 비-`None` `split_by_character`는 환경 변수 기본값을 재정의합니다.
- `split_by_character_only=True`는 재정의합니다 (`False`는 서명 기본값으로, "지정하지 않음"과 구별 불가이므로 환경 변수 기본값이 우선).

F 전략에만 유효합니다. 다른 전략의 하위 딕셔너리는 영향받지 않습니다.

### 8.6 제거된 SDK 파라미터: `reprocess_existing_non_processed`

레거시 `apipeline_enqueue_documents`의 `reprocess_existing_non_processed=True` 동작은 §5/§6의 규칙과 충돌하므로 완전히 제거되었습니다. 대체 경로:

- 자동 재개: 스캔이 §6.4의 분류 규칙에 따라 동일 이름 파일을 처리합니다.
- 강제 갱신: 먼저 `/documents/{doc_id}`를 호출하여 이전 문서를 삭제한 다음 동일 이름 새 파일을 업로드합니다.
