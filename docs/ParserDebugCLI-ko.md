# 파서 CLI 디버거 가이드

이 도구는 LightRAG의 세 가지 콘텐츠 파싱 엔진(`native` / `mineru` / `docling`)을 로컬에서 디버깅하는 데 사용됩니다. **단일 파일**에 대해 `LightRAG.parse_<engine>` 프로덕션 코드 경로를 실행하고, 파싱 아티팩트(사이드카 및 원본 캐시)를 **평면 디렉토리 레이아웃**으로 출력합니다. 프로덕션 수집 디렉토리와의 차이점은 다음과 같습니다:

- **`__parsed__/` 중간 레이어 없음**: 아티팩트가 지정된 상위 디렉토리 바로 아래에 저장되어 검사가 용이합니다.
- **소스 파일이 보관되지 않음**: 소스 파일은 원래 위치에 그대로 유지됩니다 (프로덕션 경로는 소스 파일을 `<INPUT_DIR>/__parsed__/`로 이동시킵니다).
- **원본 캐시 유효성 검사는 디렉토리 존재 여부만 확인**: 비어 있지 않은 `mineru` / `docling` 원본 디렉토리는 유효한 것으로 간주되며, `_manifest.json` 유효성 검사는 건너뜁니다.

나머지 흐름(IR 구성, 사이드카 작성, `full_docs` 동기화 로직)은 프로덕션 수집과 동일하므로 파싱 단계 문제 해결에 편리합니다.

## 명령어 형식

```bash
python -m lightrag.parser.cli <input_file> \
    --engine {native|mineru|docling} \
    [-o <sidecar_parent_dir>] \
    [--doc-id <doc-id>] \
    [--force-reparse] \
    [--preview N]
```

| 인수 | 설명 |
|---|---|
| `input_file` | 파싱할 소스 파일 경로 (위치 인수, 필수). 파일이 실제로 존재해야 합니다. |
| `--engine` | 필수: `native` (`.docx` 전용, 로컬 파싱) / `mineru` (PDF/Office 문서, MinerU 서비스 호출) / `docling` (PDF/Office 문서, docling-serve 호출). |
| `-o / --sidecar-parent-dir` | 사이드카 및 원본 디렉토리의 상위 디렉토리. 기본값은 소스 파일이 있는 디렉토리입니다. |
| `--doc-id` | 사용자 정의 문서 ID. 기본값은 `doc-<md5(소스 파일의 절대 경로)>` (동일 파일에 대한 여러 실행에서 안정적인 값). |
| `--force-reparse` | `mineru` / `docling`에만 적용: 원본 디렉토리를 삭제하고 강제로 재다운로드 및 재파싱합니다. 기본적으로 비어 있지 않은 원본 디렉토리는 재사용됩니다. |
| `--preview N` | 파싱 완료 후 처음 N개 블록(제목 + 콘텐츠 스니펫)의 미리보기를 출력합니다. 기본값 5; `0`은 비활성화합니다. |

## 출력 디렉토리 레이아웃

입력 `./inputs/workspace/sample.pdf` + 기본 사이드카 상위 디렉토리(`./inputs/workspace/`)를 예시로:

```
./inputs/workspace/
├── sample.pdf                       # 원본 파일, 변경 없음
├── sample.pdf.parsed/               # ← 사이드카 출력
│   ├── sample.blocks.jsonl          # JSONL: 첫 번째 줄은 메타, 이후 각 줄은 블록
│   ├── sample.blocks.assets/        # native로 추출된 이미지/미디어 에셋 (있는 경우)
│   ├── sample.tables.json           # 테이블 사이드카 (IR에 테이블이 있는 경우)
│   ├── sample.drawings.json         # 드로잉/이미지 사이드카 (IR에 드로잉이 있는 경우)
│   └── sample.equations.json        # 수식 사이드카 (IR에 수식이 있는 경우)
└── sample.pdf.<engine>_raw/         # ← mineru / docling의 원본 캐시 (native는 없음)
    ├── _manifest.json               # 엔진 다운로드 흐름이 작성; CLI 캐시 유효성 검사에서는 읽지 않음
    └── <bundle files>               # 엔진별 원본 아티팩트 (content_list.json / *.json / 에셋 등)
```

`native` 엔진은 원본 디렉토리를 생성하지 않습니다 (파싱이 로컬에서 이루어지며 외부 서비스가 없음).

## 일반적인 사용 사례

### A. `.docx`를 로컬에서 파싱 (네트워크 의존성 없음)

```bash
python -m lightrag.parser.cli ./inputs/workspace/sample.docx --engine native
# 출력: ./inputs/workspace/sample.docx.parsed/ (blocks.jsonl + 에셋 포함)
```

### B. MinerU로 PDF 파싱 (첫 실행 시 원본 다운로드)

```bash
# 첫 실행: 원본 번들 다운로드 + 사이드카 생성
python -m lightrag.parser.cli ./inputs/workspace/sample.pdf --engine mineru
# 두 번째 실행 (변경 없음): 원본 디렉토리 비어 있지 않음 → 직접 재사용 → 사이드카만 재생성, 빠름
python -m lightrag.parser.cli ./inputs/workspace/sample.pdf --engine mineru
# 로그에 표시됨: [parse_mineru] raw cache hit doc_id=... raw_dir=.../sample.pdf.mineru_raw
```

### C. Docling으로 PDF 파싱 + 기존 원본 디렉토리 재사용

```bash
# 기존 ./inputs/workspace/sample.pdf.docling_raw/ (docling의 JSON 출력 등 포함)
python -m lightrag.parser.cli ./inputs/workspace/sample.pdf --engine docling
# CLI는 매니페스트를 확인하지 않습니다; 원본 디렉토리가 비어 있지 않으면 docling-serve 호출을 건너뜁니다
```

> 참고: 이는 레거시 `python -m lightrag.parser.external.docling` 디버그 진입점에 있던 "기존 원본 디렉토리에서 사이드카 재구성" 시나리오의 동등한 대체입니다. 합의된 위치(`<sidecar_parent>/<source>.docling_raw/`)에 원본 디렉토리를 배치하면 캐시 히트 분기가 활성화됩니다.

### D. 사용자 정의 디렉토리로 출력

```bash
python -m lightrag.parser.cli ./inputs/workspace/sample.docx \
    --engine native -o /tmp/debug_sidecar
# 출력: /tmp/debug_sidecar/sample.docx.parsed/
# 소스 파일 ./inputs/workspace/sample.docx는 이동되지 않음
```

### E. 강제 재파싱 (원본 삭제 및 재다운로드)

```bash
python -m lightrag.parser.cli ./inputs/workspace/sample.pdf \
    --engine docling --force-reparse
# 원본 디렉토리 삭제 → docling-serve가 다시 호출되어 다운로드 → 사이드카 재생성
```

## 환경 변수

`mineru` / `docling` 엔진은 **캐시 미스** 시 (첫 파싱 또는 `--force-reparse`) 외부 서비스를 호출합니다. 필요한 환경 변수는 프로덕션 수집과 동일합니다:

- **MinerU**: `MINERU_API_MODE` (`local` / `official`), `MINERU_API_TOKEN`, `MINERU_LOCAL_ENDPOINT` 또는 `MINERU_OFFICIAL_ENDPOINT`, 선택적으로 `MINERU_ENGINE_VERSION` / `MINERU_MODEL_VERSION` / `MINERU_POLL_INTERVAL_SECONDS` / `MINERU_MAX_POLLS`.
- **Docling**: `DOCLING_ENDPOINT`, 선택적으로 `DOCLING_ENGINE_VERSION` / `DOCLING_DO_OCR` / `DOCLING_FORCE_OCR` / `DOCLING_OCR_ENGINE` / `DOCLING_OCR_PRESET` / `DOCLING_OCR_LANG` / `DOCLING_DO_FORMULA_ENRICHMENT` / `DOCLING_POLL_INTERVAL_SECONDS` / `DOCLING_MAX_POLLS`.

자세한 내용은 [FileProcessingConfiguration.md](./FileProcessingConfiguration.md)를 참고하세요.

**캐시 히트** 시 (원본 디렉토리가 이미 존재하고 비어 있지 않으며, `--force-reparse`가 전달되지 않은 경우), 외부 서비스 환경 변수가 필요하지 않습니다. 이를 이용해 오프라인으로 파싱 출력을 재현할 수 있습니다.

## 일반적인 문제 해결

| 증상 | 조치 |
|---|---|
| `error: input file does not exist: ...` | `input_file` 경로를 확인하세요; 기존 파일이어야 합니다 (원본 디렉토리가 아님). |
| 원본 디렉토리가 존재하지만 사이드카 내용이 여전히 오래됨 | 기본 동작은 원본을 **재사용**하고 사이드카를 재생성합니다. 원본 자체가 오래되었거나 교체된 경우 `--force-reparse`를 추가하여 삭제하고 재다운로드하세요. |
| MinerU가 `MINERU_API_TOKEN` 누락 보고 / Docling이 `DOCLING_ENDPOINT`에 연결 실패 | 캐시 미스가 외부 서비스 호출을 트리거했습니다. 해당 환경 변수를 확인하거나, 원본 디렉토리가 비어 있지 않은지 확인하세요 (캐시 히트 시 서비스 불필요). |
| 소스 파일이 예기치 않게 이동됨 | 발생해서는 안 됩니다: CLI는 보관 함수를 모킹했습니다. 재현 가능한 경우 이슈를 제출하세요 (파이프라인에 새 보관 호출 사이트가 추가되었을 수 있음). |
| `parse_docling`이 `produced zero blocks` 보고 | docling 원본의 메인 JSON 콘텐츠를 파싱할 수 없거나 비어 있습니다. 원본 디렉토리의 `*.json` 파일이 유효한지 확인하세요. |

## `LightRAG.parse_*` 프로덕션 경로와의 동등성

이 CLI는 `lightrag/parser/debug.py`의 경량 RAG 대리(Stand-in)를 통해 프로덕션 코드 경로 `LightRAG.parse_native` / `parse_mineru` / `parse_docling`을 직접 호출합니다. 따라서:

- 사이드카 필드, 명명 규칙, 콘텐츠 형식이 프로덕션 수집과 동일합니다.
- IR 빌더, `write_sidecar` 호출, `_persist_parsed_full_docs` 동작이 동일합니다.
- 세 가지 차이점은 모두 CLI 내부의 `monkey-patch`로 구현되며 **프로덕션 코드는 수정되지 않습니다**:
  1. `parsed_artifact_dir_for_source` → 평면 경로 반환 (`__parsed__/` 없음);
  2. `is_bundle_valid` → "비어 있지 않으면 원본 유효";
  3. `archive_docx_source_after_full_docs_sync` → no-op, 소스 파일 보존.

결과는 `tests/parser/docx/golden/native_docx/`의 골든 픽스처와 교차 검증할 수 있습니다 (CLI는 타임스탬프를 고정하지 않으므로 비교 시 `created_at` 같은 시간 필드는 제외하세요).
