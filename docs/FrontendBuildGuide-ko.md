# 프론트엔드 빌드 가이드

## 개요

LightRAG 프로젝트에는 React 기반의 WebUI 프론트엔드가 포함되어 있습니다. 이 가이드는 다양한 시나리오에서 프론트엔드 빌드가 어떻게 동작하는지 설명합니다.

## 핵심 원칙

- **Git 저장소**: 프론트엔드 빌드 결과물은 **포함되지 않습니다** (저장소를 깔끔하게 유지)
- **PyPI 패키지**: 프론트엔드 빌드 결과물이 **포함됩니다** (바로 사용 가능)
- **빌드 도구**: **Bun**을 권장하지만, **Node.js/npm**도 완전히 지원됩니다

## 설치 시나리오

### 1. 일반 사용자 (PyPI에서 설치) ✨

**명령어:**
```bash
pip install lightrag-hku[api]
```

**동작:**
- 프론트엔드가 이미 빌드되어 패키지에 포함되어 있습니다
- 추가 작업이 필요 없습니다
- 웹 인터페이스를 즉시 사용할 수 있습니다

---

### 2. 개발 모드 (기여자에게 권장) 🔧

**명령어:**
```bash
# 저장소 복제
git clone https://github.com/HKUDS/LightRAG.git
cd LightRAG

# 편집 가능 모드로 설치 (프론트엔드 빌드 불필요)
pip install -e ".[api]"

# 필요할 때 프론트엔드 빌드 (언제든지 가능)
cd lightrag_webui
bun install --frozen-lockfile
bun run build
cd ..
```

**장점:**
- 먼저 설치하고 나중에 빌드 (유연한 워크플로우)
- 변경 사항이 즉시 반영됩니다 (심볼릭 링크 방식)
- 재설치 없이 언제든지 프론트엔드를 다시 빌드할 수 있습니다

**동작 방식:**
- 소스 디렉토리에 심볼릭 링크를 생성합니다
- 프론트엔드 빌드 결과물은 `lightrag/api/webui/`에 저장됩니다
- 변경 사항이 설치된 패키지에 즉시 반영됩니다

---

### 3. 일반 설치 (패키지 빌드 테스트용) 📦

**명령어:**
```bash
# 저장소 복제
git clone https://github.com/HKUDS/LightRAG.git
cd LightRAG

# ⚠️ 먼저 프론트엔드를 빌드해야 합니다
cd lightrag_webui
bun install --frozen-lockfile
bun run build
cd ..

# 이제 설치
pip install ".[api]"
```

**동작:**
- 프론트엔드 파일이 site-packages에 **복사**됩니다
- 빌드 후 수정 사항은 설치된 패키지에 반영되지 않습니다
- 업데이트하려면 재빌드 후 재설치가 필요합니다

**언제 사용하나요:**
- 전체 설치 프로세스를 테스트할 때
- 패키지 설정을 확인할 때
- PyPI 사용자 경험을 시뮬레이션할 때

---

### 4. 배포 패키지 생성 🚀

**명령어:**
```bash
# 먼저 프론트엔드 빌드
cd lightrag_webui
bun install --frozen-lockfile --production
bun run build
cd ..

# 배포 패키지 생성
python -m build

# 결과물: dist/lightrag_hku-*.whl 및 dist/lightrag_hku-*.tar.gz
```

**동작:**
- `setup.py`가 프론트엔드 빌드 여부를 확인합니다
- 빌드되지 않은 경우 도움말 메시지와 함께 설치가 실패합니다
- 생성된 패키지에 모든 프론트엔드 파일이 포함됩니다

---

## GitHub Actions (자동화된 릴리스)

GitHub에서 릴리스를 생성하면:

1. Bun을 사용하여 **프론트엔드를 자동으로 빌드**합니다
2. 빌드가 성공적으로 완료되었는지 **검증**합니다
3. 프론트엔드가 포함된 **Python 패키지를 생성**합니다
4. 기존 신뢰할 수 있는 게시자 설정을 사용하여 **PyPI에 배포**합니다

**수동 작업이 필요 없습니다!**

---

## 빠른 참조

| 시나리오 | 명령어 | 프론트엔드 필요 여부 | 나중에 빌드 가능 |
|----------|---------|-------------------|-----------------|
| PyPI에서 설치 | `pip install lightrag-hku[api]` | 포함됨 | 불필요 (이미 설치됨) |
| 개발 | `pip install -e ".[api]"` | 불필요 | ✅ 가능 (언제든지) |
| 일반 설치 | `pip install ".[api]"` | ✅ 필요 (설치 전) | 불가 (재설치 필요) |
| 패키지 생성 | `python -m build` | ✅ 필요 (빌드 전) | 해당 없음 |

---

## Bun 설치

Bun이 설치되어 있지 않은 경우:

```bash
# macOS/Linux
curl -fsSL https://bun.sh/install | bash

# Windows
powershell -c "irm bun.sh/install.ps1 | iex"
```

공식 문서: https://bun.sh

---

## 파일 구조

```
LightRAG/
├── lightrag_webui/          # 프론트엔드 소스 코드
│   ├── src/                 # React 컴포넌트
│   ├── package.json         # 의존성
│   └── vite.config.ts       # 빌드 설정
│       └── outDir: ../lightrag/api/webui  # 빌드 결과물 경로
│
├── lightrag/
│   └── api/
│       └── webui/           # 프론트엔드 빌드 결과물 (gitignore 처리)
│           ├── index.html   # 빌드된 파일 (bun run build 실행 후)
│           └── assets/      # 빌드된 에셋
│
├── setup.py                 # 빌드 검사
├── pyproject.toml           # 패키지 설정
└── .gitignore               # lightrag/api/webui/* 제외 (.gitkeep 제외)
```

---

## 문제 해결

### Q: 개발 모드로 설치했는데 웹 인터페이스가 동작하지 않아요

**A:** 프론트엔드를 빌드하세요:
```bash
cd lightrag_webui && bun run build
```

### Q: 프론트엔드를 빌드했는데 설치된 패키지에 반영되지 않아요

**A:** `pip install .`로 설치했을 가능성이 높습니다. 다음 중 하나를 선택하세요:
- 개발용: `pip install -e ".[api]"` 사용
- 또는 재설치: `pip uninstall lightrag-hku && pip install ".[api]"`

### Q: 빌드된 프론트엔드 파일은 어디에 있나요?

**A:** `bun run build` 실행 후 `lightrag/api/webui/`에 있습니다

### Q: Bun 대신 npm이나 yarn을 사용할 수 있나요?

**A:** 가능합니다. 빌드 스크립트(`dev`, `build`, `preview`, `lint`)는 런타임에 독립적이며 Bun과 Node.js/npm 모두에서 동작합니다:
```bash
npm install
npm run build
```
속도 면에서 Bun을 권장하지만 npm도 완전히 지원됩니다. 테스트(`bun test`)는 여전히 Bun이 필요합니다.

### Q: `Cannot find package '@/lib'` 오류가 발생해요

**A:** 이 오류는 `vite.config.ts`가 Bun만 설정 로드 시점에 해석할 수 있는 TypeScript 경로 별칭(`@/`)을 사용했기 때문입니다. 이 문제가 수정된 최신 버전으로 업데이트하세요 (상대 경로 임포트로 수정됨).

---

## 요약

✅ **PyPI 사용자**: 추가 작업 불필요, 프론트엔드 포함
✅ **개발자**: `pip install -e ".[api]"` 사용, 필요할 때 프론트엔드 빌드
✅ **CI/CD**: GitHub Actions에서 자동 빌드
✅ **Git**: 프론트엔드 빌드 결과물은 절대 커밋되지 않음

문의 사항이나 문제가 있으면 GitHub 이슈를 열어주세요.
