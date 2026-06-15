# LightRAG 사이드카 파일 형식 명세 (LightRAG Sidecar File Format Specification)

이 문서는 콘텐츠 파싱 엔진이 출력하는 **LightRAG 사이드카(Sidecar)** 파일 형식을 설명합니다. LightRAG가 native/mineru/docling 같은 멀티모달 지원 콘텐츠 파싱 엔진을 사용하여 파일 콘텐츠를 추출할 때, "본문 텍스트 + 멀티모달 객체 + 파싱 메타데이터"를 `*.parsed/` 디렉터리로 분리합니다. 해당 디렉터리의 각 JSON / JSONL 파일을 총칭하여 **사이드카** 파일이라고 합니다. 사이드카는 후속 파이프라인(멀티모달 분석 → 멀티모달 청크 구성 → 엔티티 추출 → 문서 삭제 시 캐시 정리)의 유일하게 신뢰할 수 있는 정보 소스입니다. 사이드카 형식은 LightRAG의 내장 범용 파일 교환 형식이며, 새로운 멀티모달 콘텐츠 추출 엔진은 이 형식을 따라야 합니다. **LightRAG 사이드카** 형식을 공개적으로 문서화하는 목적은 커뮤니티 개발자들이 자체 콘텐츠 파싱 엔진을 작성하기 편하도록 하기 위함입니다.

## 1. 개요

| 관심사 | 파일 | 내용 | 비고 |
|---|---|---|---|
| 메인 파일 | `<doc>.blocks.jsonl` | 블록 본문 저장 | 모든 블록의 `content` 필드를 연결하면 완전한 원본 텍스트가 복원됨 |
| 드로잉 객체 | `<doc>.drawings.json` | 파일에서 추출한 드로잉 객체 | VLM(비전 언어 모델)으로 전송하여 분석; 분석 결과를 다시 기록 |
| 표 객체 | `<doc>.tables.json` | 파일에서 추출한 표 객체 | LLM으로 전송하여 분석; 분석 결과를 다시 기록 |
| 수식 객체 | `<doc>.equations.json` | 파일에서 추출한 수식 객체 | LLM으로 전송하여 분석; 분석 결과를 다시 기록 |
| 원본 이미지 에셋 | `<doc>.blocks.assets/` | 문서에서 추출한 원본 이미지 파일 | VLM 이미지 분석으로 전송 |

사이드카의 설계 의도:

- 파싱 단계에서 콘텐츠 추출 엔진(native/mineru/docling)은 `blockid / heading / content / surrounding` 같은 "객관적" 필드 생성만 **담당**합니다;
- 멀티모달 분석 단계(`analyze_multimodal`)에서 분석 결과 딕셔너리 `llm_analyze_result`는 LightRAG가 작성하며, 추가하거나 덮어쓸 수 있습니다; 파서는 미리 채워서는 안 됩니다.

## 2. 디렉터리 레이아웃

```
inputs/space1/__parsed__/<표준 파일명>.parsed/
├── <표준 파일명>.blocks.jsonl        본문 블록 시퀀스 + 문서 수준 메타 (첫 번째 행)
├── <표준 파일명>.drawings.json       드로잉 사이드카 (딕셔너리 컨테이너, 키 = 드로잉 id)
├── <표준 파일명>.tables.json         표 사이드카
├── <표준 파일명>.equations.json      수식 사이드카
└── <표준 파일명>.blocks.assets/      원본 에셋 디렉터리 (drawings.json이 참조하는 이미지 파일)
    ├── image1.wmf
    ├── image2.wmf
    ├── image3.wmf
    ├── image4.png
    ├── image5.png
    ├── image6.png
    └── image7.emf
```

## 3. blocks.jsonl

`blocks.jsonl`은 행 단위로 JSON 직렬화됩니다. **첫 번째 행은 `type="meta"`**이며; 이후의 모든 행은 `type="content"`인 내용 블록입니다.

### 3.1 meta 행 예시

```json
{
  "type": "meta",
  "format": "lightrag",
  "version": "1.0",
  "document_name": "m012-manual.docx",
  "document_format": "docx",
  "document_hash": "sha256:4840...3f9543d9db0822d2d59",
  "table_file": true,
  "equation_file": true,
  "drawing_file": true,
  "asset_dir": true,
  "split_option": { "fixlevel": 0 },
  "blocks": 39,
  "doc_id": "doc-f1bee60173d067d88595c00e7d9b0ce5",
  "parse_engine": "native",
  "parse_time": "2026-05-13T18:42:25.943490+00:00",
  "doc_title": "m012-manual"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | `"meta"` | 행 유형, 고정값, 완전성 확인용 |
| `format` | `"lightrag"` | 사이드카 주요 버전 패밀리 식별자 |
| `version` | `str` | 사이드카 스키마 버전 |
| `document_name` | `str` | 표준 파일명 (확장자 포함, 처리 힌트 제외) |
| `document_format` | `str` | 파일 형식 (현재 파일 확장자로 표현) |
| `document_hash` | `"sha256:<hex>"` | 사이드카 본문 지문. `SHA-256(merged_text)`로 정의되며, `merged_text`는 비어 있지 않은 내용 행의 `content` 필드를 `"\n\n"`으로 연결한 것. 외부 소비자가 두 `.parsed/` 디렉터리가 같은 소스를 공유하는지 빠르게 확인하고(행별 본문 비교 없이), 사이드카 파일의 자기 설명 콘텐츠 체크섬으로 사용됨. 참고: LightRAG 수집 파이프라인 자체는 이 필드를 읽지 않음; 교차 문서 중복 제거는 `doc_status.content_hash`가 별도로 처리. |
| `table_file` / `equation_file` / `drawing_file` | `bool` | 해당 사이드카 파일의 존재 여부 (true이면 해당 파일이 반드시 존재해야 함) |
| `asset_dir` | `bool` | `blocks.assets` 에셋 디렉터리 존재 여부 |
| `split_option` | `object` | 파일 추출 중 사용된 청킹 파라미터. 이 필드는 추출 엔진 자체가 기록하고 사용하기 위해 예약됨 |
| `blocks` | `int` | 내용 행 수 (meta 제외) |
| `doc_id` | `"doc-<md5>"` | 전역 문서 ID. 사이드카 항목 ID(`im-/tb-/eq-`)는 `doc-` 접두사를 제거한 `doc_id`의 해시 부분을 사용하여, 본문 텍스트에 삽입된 플레이스홀더 태그를 단축함. |
| `parse_engine` | `str` | 파싱 엔진 `native/mineru/docling/legacy` |
| `parse_time` | `str` | 파싱 완료 시간; 형식: ISO-8601 UTC |
| `doc_title` | `str` | 문서 제목 (보통 첫 번째 H1); 선택적 |
| `doc_summary` | `str` | 문서 요약; 선택적 |
| `doc_attributes` | `object` | 문서 확장 속성 객체; 선택적 |
| `bbox_attributes` | `object` | 전역 bbox 위치 속성; [§8](#8-위치positions) 참조 |

> LightRAG는 동일 워크스페이스(지식 베이스) 내에서 파일명(`document_name`)이 고유해야 합니다.

### 3.2 content 행

각 content 행은 원본 문서 "블록"의 최소 주소 지정 단위이며, 최소한 다음을 포함합니다:

```json
{
  "type": "content",
  "blockid": "462c6364584a7ba4bdae6853f85ac429",
  "format": "plain_text",
  "content": "1 제품 목적 및 기능\nMI012 모듈은 산소 공급 및 반중력 조절기의 산소 공급 및 반중력 제어 기능을 지원하는 데 사용됩니다...",
  "heading": "1 제품 목적 및 기능",
  "parent_headings": [],
  "level": 1,
  "session_type": "body",
  "table_slice": "none",
  "positions": [
    {
      "type": "paraid",
      "range": ["5EA4577A", "6555DDCB"]
    }
  ]
}
```

| 필드 | 의미 |
|---|---|
| `type` | `"content"` |
| `blockid` | 전역 고유 블록 ID |
| `format` | 콘텐츠 형식, 현재 `"plain_text"`로 고정 |
| `content` | 텍스트 내용; **수식과 이미지는 플레이스홀더 태그로, 표는 table 태그로 래핑된 JSON 또는 HTML로 나타남** (§3.3 참조) |
| `heading` | 이 콘텐츠를 포함하는 섹션의 최상위 제목. `heading`이 실제 제목이면 `content`의 시작 부분에도 나타나야 함; 제목 바로 다음에 다음 수준 제목이 오면, 다음 수준 제목을 본문 텍스트로 처리해야 함. 목표는 모든 블록의 `content` 필드를 연결하면 완전한 원본 텍스트가 복원되도록 하는 것임. |
| `parent_headings` | 문자열 배열: 현재 `heading`을 제외한 조상 제목의 위에서 아래 방향 목록 |
| `level` | 정수: `heading`의 문서 개요 수준 (`1` = H1 / 1수준 제목; `0`은 제목 없음) |
| `session_type` | 블록이 속하는 영역: `body` `preface` `TOC` `references` `appendix` |
| `table_slice` | 선택적 예약 필드; 블록이 표의 슬라이스만 포함하는지 여부를 나타냄. 현재 분석 엔진은 긴 표를 분할하지 않으므로 이 필드는 `"none"`으로 고정 (표가 슬라이스되지 않음을 의미) |
| `table_header` | 선택적 예약 필드; 현재 블록이 표 슬라이스일 때 인식된 표 헤더를 보유. 현재 미사용. |
| `positions` | `position` 객체 배열: 텍스트 블록의 레이아웃 위치를 식별함; 텍스트 블록이 레이아웃의 여러 위치에서 왔을 때 여러 `position` 객체가 나타남. [§8](#8-위치positions) 참조 |

> - blockid 계산: `md5(doc_id + ":" + block_index + ":" + heading + ":" + content)`. 청킹 전략이 생성한 청크는 사이드카의 위치로 청크를 역추적하기 위해 blockid를 기록함.
> - 문서 섹션 구조를 무시하는 청킹 전략 `F` / `R` / `V`는 연결된 `content` 필드에서 작동합니다. 따라서 모든 블록의 `content` 필드를 연결하면 빠짐없이, 겹침 없이 완전한 문서 내용이 되어야 합니다.

### 3.3 content 내부의 인라인 플레이스홀더 태그

P 청킹 전략이 멀티모달 객체를 깨지 않고 본문 텍스트를 분할할 수 있도록, `content` 내부에 세 가지 XML 스타일 플레이스홀더 태그를 사용합니다:

| 태그 | 의미 | 태그 속성 |
|---|---|---|
| `<table id="tb-…" format="json">…</table>` | 표 플레이스홀더; 본문은 원시 표 JSON / HTML | `id`는 `tables.json`의 해당 항목을 가리킴; `format` ∈ `json` / `html` |
| `<drawing id="im-…" format="png" path="…" src="…" caption="…" />` | 자기 폐쇄형 드로잉 플레이스홀더 | `id`는 `drawings.json`을 가리킴; `path`는 `*.parsed/` 디렉터리에 대한 상대 경로; `src`는 원본 문서에서의 참조 이름 |
| `<equation id="eq-…" format="latex" caption="…">…</equation>` | 수식 플레이스홀더 | 인라인 수식도 `<equation format="latex">`을 사용하지만 `id`가 **없으며**, 사이드카에 기록되지 않음; 블록 수식(한 줄 이상을 차지하는)만 `id`를 가짐 |

텍스트가 엔티티/관계 추출 중 LLM에 전달될 때, `id / path / src` 같은 내부 속성은 제거되지만 핵심 속성(`format / caption`)은 보존됩니다. 목표는 문서에 보이지 않는 엔티티를 추출하여 추출 결과에 과도한 노이즈를 주입하는 것을 피하기 위함입니다.

### 3.4 blockid와 청크 sidecar.refs 간의 대응 관계

사이드카 파일이 있을 때, 청킹 전략은 각 출력 청크에 `sidecar = {"type": "block", "id": <주요 소스 blockid>, "refs": [{"type": "block", "id": <blockid>}, …]}`를 첨부합니다. 여기서:

- 병합되지 않은 청크 → `sidecar.refs`는 청크가 나온 blocks.jsonl 행의 `blockid`와 동일한 하나의 요소만 있음;
- Stage D에서 병합된 청크 → `refs`는 모든 소스 `blockid`의 순서를 보존함 (중복 제거됨);
- 하드 폴백 분할 후 하위 청크 → 부모 청크의 `sidecar`를 공유.

이 연결은 문서 수준 추적 가능성(청크 ↔ 블록 ↔ 원본 단락 paraId)의 기반입니다.

## 4. drawings.json

최상위는 `{"version": "1.0", "drawings": { <id>: <item>, … }}` 형태의 딕셔너리 컨테이너로, id로 조회하기 위해 **`id` 필드로 키가 지정**됩니다. 각 항목은 다음과 같습니다:

```json
{
  "id": "im-f1bee60173d067d88595c00e7d9b0ce5-0004",
  "blockid": "2f52b70839d13a936d97955916820147",
  "heading": "2.3 구조적 치수 및 무게",
  "format": "png",
  "path": "m012-manual.blocks.assets/image4.png",
  "src": "",
  "caption": "",
  "footnotes": [],
  "extras": {
    "ocr_texts": "이미지 내 첫 번째 OCR 단락\n\n이미지 내 두 번째 OCR 단락",
    "ocr_texts_count": 2
  },
  "surrounding": {
    "leading": "2.3 구조적 치수 및 무게\n치수 및 무게 요건은 다음과 같습니다:\na) 외부 치수 길이: <drawing …",
    "trailing": "\n그림 1  외부 치수 개략도\nb) 무게는 0.85 kg을 초과하지 않습니다.\nc) 테스트 결과: 측정된 회로 노이즈 Vpp=1.526 mV…"
  },
  "llm_analyze_result": {
    "name": "제품 외부 치수 공학 도면",
    "type": "Illustration",
    "description": "이 도면은 제품의 외부 치수에 대한 개략도로, 전자 장치 또는 전력 모듈 설계의 세 가지 뷰를 보여줍니다…",
    "analyze_time": 1778697752,
    "status": "success",
    "message": ""
  },
  "llm_cache_list": [
    "default:analysis:fcf4c4f88227ee1c1bf0ed4394039e37"
  ]
}
```

| 필드 | 설명 |
|---|---|
| `id` | `im-<doc_hash>-<NNNN>` 형태 (`doc_hash`는 `doc-` 접두사가 제거된 `doc_id`의 32자 md5 부분) |
| `blockid` | 이 드로잉을 생성한 내용 행을 가리킴 |
| `heading` | 드로잉이 속하는 섹션 제목 |
| `format` | 원본 확장자 (점 없음): `png` / `jpeg` / `gif` / `webp` / `wmf` / `emf` / … |
| `path` | `*.parsed/` 디렉터리에 대한 상대 리소스 경로; **항상** `*.blocks.assets/` 내의 파일을 가리킴 |
| `src` | 원본 문서에서 드로잉의 참조 별칭 (대부분의 경우 비어 있음) |
| `caption` | 보이는 캡션 (파서가 비워 둘 수 있음) |
| `footnotes` | 각주 문자열 목록 |
| `surrounding` | 컨텍스트 객체: [§7](#7-surrounding컨텍스트) 참조 |
| `self_ref` | 문자열, 선택적; 원본 파싱 엔진 출력에서의 객체 참조 (예: Docling JSON 포인터 `#/pictures/3` 또는 MinerU `content_list.json#/23`), 역추적 시 파싱 아티팩트에서 원본 객체를 조회하는 데 사용. `native` 및 이 필드를 제공하지 않는 다른 엔진에서는 출력되지 않음. |
| `extras` | 객체, 선택적; 엔진 특정 우회 필드 (이미지 내부의 OCR 텍스트 등). 스펙 검증의 일부가 아님; 다운스트림 소비자는 특정 키에 의존해서는 안 됨. |
| `llm_analyze_result` | 모달 분석 결과 객체: [§9](#9-llm_analyze_result) 참조 (나중에 멀티모달 텍스트 블록에 주입됨) |
| `llm_cache_list` | 모달 분석용 LLM 캐시 목록 (나중에 멀티모달 텍스트 블록에 주입됨) |

`extras` 내의 일반 드로잉 특정 키:

| 키 | 설명 |
|---|---|
| `ocr_texts` | 문자열, 선택적; 드로잉 객체 내부의 OCR 텍스트, 여러 단락은 빈 줄(`\n\n`)로 연결. 파싱 엔진이 이 드로잉의 자식 아래에 OCR 텍스트를 명시적으로 첨부할 때만 기록됨; 캡션/각주는 이 필드에 들어가지 않음. |
| `ocr_texts_count` | 정수, 선택적; `ocr_texts`에 기록된 비어 있지 않은 OCR 단락 수. |

**드로잉에서 지원하는 래스터 형식(png / jpeg / gif / webp)만 VLM 분석에 들어갑니다**; 다른 형식(wmf / emf / svg 등)은 `llm_analyze_result.status="skipped"`가 되고, 다운스트림에서 멀티모달 청크가 생성되지 않으며, 문서 처리는 계속됩니다. 환경 변수 `VLM_MAX_IMAGE_BYTES`로 지정된 크기보다 큰 이미지도 VLM 분석에 들어가지 않습니다.

> 이미지 크기와 DPI 같은 정보는 `extras` 객체에 통일하여 배치합니다; 항목 최상위 수준에 선언되지 않은 필드(예: `image` / `img_path` 등)를 도입하지 마세요. tables / equations도 동일한 `extras` 규약을 따릅니다. `self_ref`는 스펙이 선언한 최상위 선택 필드로 `extras`에 속하지 않습니다.

## 5. tables.json

최상위는 `{"version": "1.0", "tables": { <id>: <item>, ... }}` 형태의 딕셔너리 컨테이너로, id로 조회하기 위해 **`id` 필드로 키가 지정**됩니다. 각 항목은 다음과 같습니다:

```json
{
  "id": "tb-f1bee60173d067d88595c00e7d9b0ce5-0007",
  "blockid": "3f33897b5e105d254addc655f1efbf8c",
  "heading": "2.4.4 온도-습도-고도 (시스템과 함께 실행)",
  "dimension": [16, 8],
  "format": "json",
  "content": "[[\"단계\", \"온도 (°C)\", \"고도 (m)\", \"상대 습도\", \"시간 (분)\", \"보조 냉각\", \"시스템 전원\", \"기능/성능 확인\"],…",
  "caption": "",
  "footnotes": [],
  "table_header": "[[\"단계\", \"온도 (°C)\", \"고도 (m)\", \"상대 습도\", \"시간 (분)\", \"보조 냉각\", \"시스템 전원\", \"기능/성능 확인\"]]",
  "surrounding": {
    "leading": "2.4.4 온도-습도-고도 (시스템과 함께 실행)\n제품은 임무 수행 중 온도, 습도, 고도의 복합 환경을 견뎌야 합니다…",
    "trailing": "\n참고: 위의 단계는 10 사이클 반복됩니다. a) 완제품 및 부속품이 열적 안정성에 도달하거나 240분, 둘 중 더 긴 시간; b) 완제품 및 부속품이 열적 안정성에 도달하거나 120분, 둘 중 더 긴 시간.…"
  },
  "llm_analyze_result": {
    "name": "문서 관리 메타데이터 표",
    "description": "이것은 기술 문서의 기본 메타데이터와 버전 제어 정보를 기록하는 데 사용되는 문서 관리 정보 표입니다…",
    "analyze_time": 1778697759,
    "status": "success",
    "message": ""
  },
  "llm_cache_list": [
    "default:analysis:b316aacd40fdca0cb56430870bb89a62"
  ]
}
```

tables.json의 `blockid` / `heading` / `surrounding` / `llm_analyze_result` 필드는 drawings.json과 동일한 의미를 가집니다. 다르거나 새로 추가된 필드는 아래에 설명합니다:

| 필드 | 설명 |
|---|---|
| `id` | `tb-<doc_hash>-<NNNN>` 형태 (`doc_hash`는 `doc-` 접두사가 제거된 `doc_id`의 32자 md5 부분) |
| `dimension` | 정수 배열: `[행 수, 열 수]`, 헤더 행 포함 |
| `format` | `"json"` (2D 배열) 또는 `"html"` (시작 및 닫기 태그를 포함하는 `<table>…</table>` 조각) |
| `content` | 문자열: `format`에 따라 구조화된 표 본문; 다운스트림 멀티모달 청크가 실제로 사용하는 문자열. |
| `table_header` | 문자열, 선택적; 표 헤더로 처리되는 인식된 행 |
| `self_ref` | 선택적; 원본 파싱 엔진 출력에서의 객체 참조 (예: Docling JSON 포인터 `#/tables/2` 또는 MinerU `content_list.json#/31`), 역추적 시 원본 아티팩트를 조회하는 데 사용 |

모달 분석 단계에서 `content` 필드의 길이가 LLM의 컨텍스트 윈도우를 초과하면 모델에 전달하기 전에 표 내용이 기계적으로 절단됩니다.

## 6. equations.json

최상위는 `{"version": "1.0", "equations": { <id>: <item>, ... }}` 형태의 딕셔너리 컨테이너로, id로 조회하기 위해 **`id` 필드로 키가 지정**됩니다. 각 항목은 다음과 같습니다:

```json
{
  "id": "eq-f1bee60173d067d88595c00e7d9b0ce5-0001",
  "blockid": "2f52b70839d13a936d97955916820147",
  "heading": "2.3 구조적 치수 및 무게",
  "format": "latex",
  "content": "C=2∗\\frac{P∗T}{\\left( {V}_{H}^{2}−{V}_{L}^{2} \\right)∗η}",
  "caption": "",
  "footnotes": [],
  "surrounding": {
    "leading": "2.3 구조적 치수 및 무게\n치수 및 무게 요건은 다음과 같습니다:\n …",
    "trailing": "\n여기서 P는 전원 이상 시 유지되는 전력 28 W, T는 원하는 에너지 저장 시간, V<sub>H</sub>는 커패시터 방전 전…"
  },
  "llm_analyze_result": {
    "name": "커패시터 에너지 저장 시간 계산 공식",
    "description": "이 공식은 전원 이상 중 정상 시스템 작동을 유지하는 데 필요한 커패시터 에너지 저장값을 계산합니다…",
    "analyze_time": 1778697783,
    "status": "success",
    "message": "",
    "equation": "C=2\\cdot\\frac{P\\cdot T}{(V_{H}^{2}-V_{L}^{2})\\cdot\\eta}"
  },
  "llm_cache_list": [
    "default:analysis:fcf4c4f88227ee1c1bf0ed4394039e37"
  ]
}
```

equations.json의 `blockid` / `heading` / `surrounding` / `llm_analyze_result` 필드는 drawings.json과 동일한 의미를 가집니다. 다르거나 새로 추가된 필드는 아래에 설명합니다:

| 필드 | 설명 |
|---|---|
| `id` | `eq-<doc_hash>-<NNNN>` 형태 (`doc_hash`는 `doc-` 접두사가 제거된 `doc_id`의 32자 md5 부분) |
| `format` | `"latex"`로 고정 |
| `content` | 문자열: **원시** LaTeX (유니코드 연산자, 외부 `\[ \]` 포함 가능); 선행/후행 `$` 구분자를 포함하지 않음; 모달 분석 단계에서 직접 읽음 |
| `self_ref` | 선택적; 원본 파싱 엔진 출력에서의 객체 참조 (예: Docling JSON 포인터 `#/texts/15` 또는 MinerU `content_list.json#/45`), 역추적 시 원본 아티팩트를 조회하는 데 사용 |
| `llm_analyze_result.equation` | 문자열: LLM이 출력한 **표준화된** LaTeX 수식 (외부 `$ / \[ \] / equation` 환경, 유니코드를 LaTeX로 변환, 선행/후행 `$` 구분자 없음); 다운스트림 멀티모달 청크가 실제로 사용하는 문자열. |

모달 분석 단계에서 `content` 필드의 길이가 LLM의 컨텍스트 윈도우를 초과하면 모델에 전달하기 전에 내용이 기계적으로 절단됩니다. 인라인 수식(`<equation format="latex">…</equation>`으로 본문과 연속된 것)은 equations.json에 저장되지 **않으며**; `id` 없이 블록 텍스트에만 남습니다. 목표는 추출 결과에 과도한 노이즈를 주입하지 않기 위함입니다.

## 7. surrounding(컨텍스트)

`surrounding.leading`과 `surrounding.trailing`은 사이드카 항목의 분석 가능한 컨텍스트 윈도우입니다; 그 목적은 이미지, 표 또는 수식을 포함하는 단락에 대한 컨텍스트 정보를 제공하여 멀티모달 분석의 품질을 향상시키는 것입니다. **surrounding 내용은 분석 단계에서 LightRAG가 자동으로 주입합니다; 문서 파싱 엔진이 사이드카에 능동적으로 기록할 필요가 없습니다.** surrounding 내용의 생성 로직은 다음과 같습니다:

- 동일 `blockid`를 가진 내용 행의 텍스트에서 가져와, 멀티모달 플레이스홀더 태그의 위치에서 분할함;
- 각 측면의 토큰 한도는 환경 변수 `SURROUNDING_LEADING_MAX_TOKENS` / `SURROUNDING_TRAILING_MAX_TOKENS`로 제어 (기본값 `2000`, 독립적으로 조정 가능); 토크나이저로 절단하되, 목표에 가까운 문장을 우선 유지;
- 텍스트는 **같은 행에 있는 다른 멀티모달 객체의 플레이스홀더 태그**를 보존하여, "그림 1 다음에 수식 1도 있음"과 같은 컨텍스트를 모델이 인식할 수 있게 함; 단 내부 파서 식별자(`id` / `path` / `src` / `refid`)는 `strip_internal_multimodal_markup_for_extraction`에 의해 제거됨 — 엔티티 추출 전 청크 내용 정리와 일관성을 유지하여 노이즈가 VLM/LLM 프롬프트에 들어가지 않도록 함. 구체적인 정리 규칙:
  - `<drawing id="im-…" path="…" src="…" caption="그림 1" />` → `<drawing caption="그림 1" />`; **캡션 없는 드로잉은 완전히 제거됨** (태그에 모델이 볼 수 있는 정보가 더 이상 없음);
  - `<table id="tb-…" format="json" caption="…">rows</table>` → `<table format="json" caption="…">rows</table>`;
  - `<equation id="eq-…" format="latex">body</equation>` → `<equation format="latex">body</equation>`;
  - `<cite type="table" refid="tb-…">표 1</cite>` → `<cite type="table">표 1</cite>`; `<cite type="equation" refid="eq-…">수식 2</cite>` → `<cite type="equation">수식 2</cite>`. `refid` 속성만 제거되고; `<cite type="…">…</cite>` 래퍼는 보존됨 — VLM/LLM이 "이것은 다른 표/수식에 대한 참조"임을 인식하게 하면서, LLM이 볼 수 없는 파서 내부 id를 숨김.
    - 예외: `tables.json` 유형의 surrounding은 제거 전에 먼저 `remove_table_tags`를 거쳐 모든 `<cite type="table">` 블록을 완전히 제거함 (대상 표를 분석할 때 다른 표에 대한 허상 참조에 의해 산만해지지 않도록);
- 정리는 토큰 예산 절단 **전에** 발생함: 토큰 수는 "LLM이 실제로 보는 것"에 대해 계산되며, 절단은 정리되지 않은 `id="…"` 속성 내부에 떨어지지 않아 깨진 태그 구조를 피함;
- 목표 객체 자체가 블록의 시작/끝에 위치할 때, 해당 측면은 `"n/a"` 대신 `""`이 됨 (프롬프트 조립 시 빈 문자열은 나중에 `n/a`로 표시됨);
- `enrich_sidecars_with_surrounding`은 멱등적: 각 `analyze_multimodal` 진입점은 `surrounding`을 재계산하고 덮어쓰므로, `SURROUNDING_LEADING_MAX_TOKENS` / `SURROUNDING_TRAILING_MAX_TOKENS`를 변경한 후 사이드카를 수동으로 정리할 필요가 없음 — 멀티모달 분석을 다시 실행하기만 하면 `surrounding`이 새 예산으로 재기록됨.

## 8. 위치(positions)

`positions`는 `blockid` 내용이 파일의 어떤 텍스트에서 왔는지를 식별하는 객체 배열로, 콘텐츠 추적 시 소스 파일에서 원본 내용을 찾고 표시할 수 있게 합니다. `blockid`의 내용이 레이아웃의 여러 열에서 구성된 경우 여러 `position` 객체가 나타나며, 각 `position` 객체는 하나의 레이아웃 박스 또는 열에 해당합니다. 서로 다른 문서 형식의 콘텐츠 위치 지정 방식을 수용하기 위해, 다음과 같은 유형의 `position` 객체를 지원합니다.

`position` 객체는 여러 유형이 있으며, `type` 필드가 유형을 결정합니다:

* paraid

docx 형식 파일에 적용; `단락 id`(paraid)로 내용을 찾습니다. `range` 필드는 시작 및 끝 `단락 id`를 지정하며; `charspan`은 내용이 단락의 문자 m에서 시작하여 문자 n에서 끝난다는 것을 지정하는 선택적 필드입니다. `charspan`이 제공되지 않으면, `blockid`는 시작 및 끝 단락의 전체 내용을 포함합니다. 예시:

```
"positions": [
{
    "type": "paraid",
    "range": ["5EA4577A", "6555DDCB"]
    "charspan": [10,999]
}]
```

* bbox

PDF 유사 파일에 적용; 페이지의 직사각형을 통해 내용의 원래 위치를 식별합니다. bbox는 다음 필드를 지원합니다:

```
origin: 직사각형 좌표가 페이지의 어떤 위치에 대한 상대값인지 (선택적, 기본값 LEFTTOP; 다른 옵션은 LEFTBOTTOM)
max: 페이지 레이아웃의 최대 길이와 너비; 정확한 위치 표시를 위해 좌표가 이 값으로 정규화됨 (선택적; 비어 있으면 이미지의 픽셀 격자로 좌표 계산)
anchor: 페이지 번호, 문자열로, 로마 숫자와 같은 비아라비아 숫자 지원
range: 직사각형 좌표 배열 [h1, w1, h2, w2], 예: [174, 155, 818, 333]
charspan: 내용이 앵커된 단락의 문자 m에서 시작하여 문자 n에서 끝남 (선택적)
```

`blocks.jsonl`의 `meta` 행의 `bbox_attributes` 필드는 전역 bbox 설정을 보유하여, 모든 `content` 행의 `positions` 객체에 동일한 내용을 반복하는 것을 방지합니다. 일반적인 `positions` 객체 예시:

```
"positions": [
{
    "type": "bbox",
    "anchor": "ii"
    "range": [174, 155, 818, 333]
    "charspan": [10, 999]
}]
```

* heading

마크다운 유사 파일에 적용; 제목으로 내용을 찾습니다. `anchor`는 시작 제목입니다 (중복 제목 처리를 위해 마크다운 앵커 스펙 참조); `charspan`은 내용이 단락의 문자 m에서 시작하여 문자 n에서 끝난다는 것을 지정하는 선택적 필드입니다. `charspan`이 제공되지 않으면, `blockid`는 시작 및 끝 단락의 전체 내용을 포함합니다.

```
"positions": [
{
    "type": "heading",
    "anchor": "ii"
    "range": [174, 155, 818, 333]
    "charspan": [10, 999]
}]
```

* absolute

텍스트 유사 파일에 적용; 절대 문자 위치로 내용을 찾습니다. `charspan`은 내용이 문자 m에서 시작하여 문자 n에서 끝난다는 것을 지정합니다.

```
"positions": [
{
    "charspan": [10, 999]
}]
```

## 9. `llm_analyze_result`

| `status` | 트리거 시나리오 | 필드 설명 |
|---|---|---|
| `success` | 모델이 유효한 JSON을 반환하고 모든 필수 필드가 있음 | 드로잉: `name / type / description`; 표: `name / description`; 수식: `name / description / equation` |
| `skipped` | 멀티모달 분석이 의도적으로 건너뜀: 이미지 형식 미지원, 픽셀 < `VLM_MIN_IMAGE_PIXEL` (기본값 32 px), `VLM_MAX_IMAGE_BYTES` (기본값 5 MB)보다 큼, 또는 VLM 미활성화 | `message`에 건너뜀 이유 기록 |
| `failure` | 필수 필드 없음, JSON이 수리 후에도 유효하지 않음, 해당 모달리티가 활성화된 상태에서 VLM/EXTRACT 역할이 설정되지 않음, 또는 모델 호출이 예외를 던짐 | `message`에 진단 정보 기록 |

추가 참고:

- `analyze_time`은 에포크 초 단위이며 모든 상태에 대해 존재;
- `message`는 `status="success"`일 때 **항상 빈 문자열**로, 필터링을 편리하게 함;
- 활성화된 모달리티의 항목은 각 `analyze_multimodal` 실행 시 재계산되며, 현재 실행이 이전의 `llm_analyze_result`(`success`, `skipped`, 또는 `failure`)를 덮어씁니다. 이를 통해 운영자가 VLM/EXTRACT 설정을 수정하고 오래된 사이드카 결과를 수동으로 지우지 않고 재시도할 수 있습니다. LLM 호출은 여전히 분석 캐시를 사용합니다: 캐시 키가 일치하면 제공자가 호출되지 않고 의미론적 필드는 보통 동일하게 유지되지만, `analyze_time` 같은 런타임 필드는 재기록됩니다. 유효한 역할 모델/바인딩/호스트, 프롬프트 입력 또는 이미지 메타데이터를 변경한 후 캐시 미스는 다른 저장된 내용을 생성할 수 있습니다.

드로잉 `type`은 12개 값의 열거형으로 제한됩니다 ([`IMAGE_TYPE_ENUM`](../lightrag/prompt_multimodal.py) 참조: `Photo / Illustration / Screenshot / Icon / Chart / Table / Infographic / Flowchart / Chat Log / Wireframe / Texture / Other`); 열거형 외의 모델 반환 값은 실패하지 않고 `Other`로 정규화됩니다.
