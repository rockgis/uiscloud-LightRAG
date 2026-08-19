# LightRAG WebUI

LightRAG WebUI는 LightRAG 시스템과 상호작용하기 위한 React 기반 웹 인터페이스입니다. LightRAG의 기능을 조회, 관리, 탐색할 수 있는 사용자 친화적인 인터페이스를 제공합니다.

## 설치

### Bun 사용 (권장)

1. **Bun 설치:**

    아직 Bun을 설치하지 않았다면 공식 문서를 따라 설치하세요: [https://bun.sh/docs/installation](https://bun.sh/docs/installation)

2. **의존성 설치:**

    `lightrag_webui` 디렉터리에서 다음 명령을 실행하여 프로젝트 의존성을 설치합니다:

    ```bash
    bun install --frozen-lockfile
    ```

3. **프로젝트 빌드:**

    다음 명령을 실행하여 프로젝트를 빌드합니다:

    ```bash
    bun run build
    ```

    이 명령은 프로젝트를 번들링하고 빌드된 파일을 `lightrag/api/webui` 디렉터리에 출력합니다.

### Node.js / npm 사용 (대안)

사용 환경에서 Bun을 사용할 수 없거나 Bun 빌드가 실패하는 경우(예: 오래된 Linux 배포판, 제한된 환경, 또는 Bun 버전 비호환성), Node.js를 대신 사용할 수 있습니다:

```bash
npm install
npm run build
```

> **참고:** 테스트(`bun test`)는 여전히 Bun이 필요합니다. 그 외의 모든 스크립트(`dev`, `build`, `preview`, `lint`)는 Bun과 Node.js/npm 모두에서 동작합니다.

## 개발

- **개발 서버 시작:**

  ```bash
  # Bun 사용
  bun run dev

  # Node.js/npm 사용
  npm run dev
  ```

## 스크립트 명령어

`package.json`에 정의된 자주 사용하는 스크립트 명령어는 다음과 같습니다:

| 명령어 | 설명 |
|---------|-------------|
| `bun run dev` / `npm run dev` | 개발 서버를 시작합니다 |
| `bun run build` / `npm run build` | 프로덕션용으로 프로젝트를 빌드합니다 |
| `bun run lint` / `npm run lint` | 린터를 실행합니다 |
| `bun run preview` / `npm run preview` | 프로덕션 빌드를 미리 봅니다 |
| `bun run build:bun` | Bun 런타임을 명시적으로 사용하여 빌드합니다 |
| `bun test` | 테스트를 실행합니다 (Bun 전용) |

## 문제 해결

### `bun run build`가 조용히 실패하거나 종료 코드 1로 실패하는 경우

이는 Bun 버전 비호환성이나 제한된 환경에서 발생할 수 있습니다. 다음을 시도해 보세요:

```bash
npm install
npm run build
```

### `Cannot find package '@/lib'`

이 오류는 Vite 설정에서 Bun만 설정 로드 시점에 해석할 수 있는 TypeScript 경로 별칭(`@/`)을 사용했던 이전 버전에서 발생했습니다. `vite.config.ts`에서 상대 경로 임포트를 사용하도록 수정하여 해결되었습니다.
