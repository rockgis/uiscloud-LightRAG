# 단일 서버 다중 사이트 배포

이 문서는 리버스 프록시(nginx, Traefik, Kubernetes Ingress 등)를 사용하여 하나의 호스트에서 여러 개의 격리된 LightRAG 인스턴스를 실행하고, **하나의 WebUI 빌드**를 모든 인스턴스에서 재사용하는 방법을 설명합니다.

> 기본 단일 인스턴스 Docker 설정을 찾고 있다면 [DockerDeployment.md](./DockerDeployment-ko.md)를 참고하세요. 일반적인 프런트엔드 빌드 메커니즘은 [FrontendBuildGuide.md](./FrontendBuildGuide-ko.md)를 참고하세요.

---

## 요약

- `LIGHTRAG_API_PREFIX`는 **백엔드에서만** 인스턴스별로 설정하세요. WebUI는 항상 `/webui`에 마운트됩니다(변경 불가).
- WebUI를 **한 번만** 빌드하세요. 동일한 아티팩트가 어떤 리버스 프록시 프리픽스에서도 작동합니다.
- 리버스 프록시가 각 백엔드를 가리키도록 설정하고, 사이트 프리픽스를 제거한 후 포워딩하세요.

```bash
# 이미지 하나, 컨테이너 둘, 프리픽스 둘 — 리빌드 불필요.
docker run -e LIGHTRAG_API_PREFIX=/site01 -p 9621:9621 lightrag:latest
docker run -e LIGHTRAG_API_PREFIX=/site02 -p 9622:9621 lightrag:latest
```

---

## "한 번 빌드, 여러 곳 배포"가 필요한 이유

이전 버전의 LightRAG는 빌드 시점에 사이트 프리픽스를 JavaScript 번들에 포함시켰습니다(`VITE_API_PREFIX` / `VITE_WEBUI_PREFIX`를 통해). 다른 프리픽스를 사용하는 각 사이트는 별도의 WebUI 빌드가 필요했으며, 단일 Docker 이미지를 여러 사이트에 재사용하려면 배포 시 리빌드 단계가 필요했습니다. 런타임 설정 주입(runtime-config-injection) 리팩터링 이후:

- `index.html`의 **에셋 URL**은 상대 경로(`./assets/index-abc.js`)로 생성됩니다. 브라우저가 현재 문서 URL에 상대적으로 해석하므로 어떤 마운트 포인트에서도 작동합니다.
- **API 기본 URL**과 **앱 내 링크**는 `window.__LIGHTRAG_CONFIG__`에서 프리픽스를 읽습니다. FastAPI 서버는 자체 `LIGHTRAG_API_PREFIX`를 기반으로 각 응답에서 `index.html`에 이를 주입합니다.

결과적으로, 단일 `lightrag/api/webui/` 디렉토리(또는 Docker 이미지)를 사이트별 빌드 아티팩트 없이 원하는 수만큼의 사이트에서 재사용할 수 있습니다.

---

## 런타임 프리픽스 주입 방식

`index.html`에 대한 각 요청은 `lightrag/api/lightrag_server.py`의 `SmartStaticFiles`를 거칩니다:

1. `bun run build`로 생성된 정적 `index.html`을 읽습니다.
2. `<!-- __LIGHTRAG_RUNTIME_CONFIG__ -->` 플레이스홀더 주석을 찾습니다.
3. 설정된 `LIGHTRAG_API_PREFIX`에서 계산된
   `<script>window.__LIGHTRAG_CONFIG__ = {"apiPrefix":"…","webuiPrefix":"…"}</script>`로 교체합니다
   (앱 내 `/webui` 마운트는 서버 측에서 하드코딩됩니다).

사이트 프리픽스가 있는 인스턴스에 대한 브라우저 요청 순서:

```
Browser            nginx                  uvicorn         SmartStaticFiles
  │                  │                       │                    │
  │ GET /site01/webui/                       │                    │
  │─────────────────►│                       │                    │
  │                  │ GET /webui/  (/site01 제거)                 │
  │                  │──────────────────────►│                    │
  │                  │                       │ get_response("")   │
  │                  │                       │───────────────────►│
  │                  │                       │                    │ 주입
  │                  │                       │                    │ window.__LIGHTRAG_CONFIG__
  │                  │                       │                    │ = { apiPrefix: "/site01",
  │                  │                       │                    │ webuiPrefix: "/site01/webui/" }
  │                  │                       │◄───────────────────│
  │                  │◄──────────────────────│                    │
  │◄─────────────────│                       │                    │
  │ 런타임 설정이 주입된 index.html
```

SPA는 `src/lib/runtimeConfig.ts`를 통해 주입된 설정을 읽고,
`axios.baseURL`, `fetch()` 템플릿 문자열, API 문서 iframe,
앱 내 링크에 사용합니다.

---

## 백엔드 변수 하나로 충분

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `LIGHTRAG_API_PREFIX` | `""` | 리버스 프록시 마운트 프리픽스. 백엔드는 스트립 및 그대로 포워딩 모두 허용합니다. FastAPI에 `root_path`로 전달됩니다. |

WebUI는 항상 서버 측에서 `/webui`에 마운트됩니다. `window.__LIGHTRAG_CONFIG__.webuiPrefix`는 `LIGHTRAG_API_PREFIX + "/webui/"`로 계산되어 SPA에 주입됩니다 — 직접 설정할 필요가 없습니다.

더 이상 프런트엔드 `VITE_API_PREFIX` / `VITE_WEBUI_PREFIX` 변수는 없습니다. 설정해도 효과가 없습니다(빌드에서 무시됨).

### 포워딩 모드: 스트립 및 그대로 전달 모두 작동

`LIGHTRAG_API_PREFIX=/site01`을 설정한 후, 백엔드는 두 가지 포워딩 방식 모두에서 모든 라우트를 올바르게 해석합니다:

- **스트립** — 프록시가 프리픽스를 제거하고, 백엔드가 `/webui/`와 `/documents/foo`를 수신합니다. 아래 nginx 예시가 이 방식을 사용합니다.
- **그대로 전달** — 프록시가 요청을 변경 없이 포워딩하고, 백엔드가 `/site01/webui/`와 `/site01/documents/foo`를 수신합니다. Vite 개발 흐름([시나리오 2](#시나리오-2--사이트-프리픽스-시뮬레이션))과 재작성하지 않는 프록시가 이 방식을 사용합니다.

`create_app`의 작은 ASGI 미들웨어가 경로에 이미 포함되지 않은 경우 `scope["path"]`에 `root_path`를 앞에 붙이므로, 일반 라우트와 마운트 서브앱(WebUI의 `StaticFiles`) 모두 두 가지 모드에서 동일하게 동작합니다. 하나를 표준화할 필요가 없습니다 — 두 방식이 설정 토글 없이 동일한 백엔드에서 공존합니다.

---

## 엔드투엔드 예시: 하나의 nginx 뒤에 두 개의 사이트

### 인스턴스 설정

`site01.env`:
```bash
HOST=0.0.0.0
PORT=9621
LIGHTRAG_API_PREFIX=/site01
WORKING_DIR=/data/site01/storage
INPUT_DIR=/data/site01/inputs
LIGHTRAG_API_KEY=site01-secret
# … LLM / 임베딩 설정 …
```

`site02.env`:
```bash
HOST=0.0.0.0
PORT=9621
LIGHTRAG_API_PREFIX=/site02
WORKING_DIR=/data/site02/storage
INPUT_DIR=/data/site02/inputs
LIGHTRAG_API_KEY=site02-secret
# … LLM / 임베딩 설정 …
```

### docker-compose.yml (이미지 하나, 서비스 둘)

```yaml
services:
  site01:
    image: ghcr.io/hkuds/lightrag:latest
    env_file: site01.env
    volumes:
      - ./data/site01:/data/site01
    ports:
      - "127.0.0.1:9621:9621"

  site02:
    image: ghcr.io/hkuds/lightrag:latest
    env_file: site02.env
    volumes:
      - ./data/site02:/data/site02
    ports:
      - "127.0.0.1:9622:9621"
```

### nginx 설정

```nginx
server {
    listen 443 ssl http2;
    server_name host.example.com;

    # site01: /site01/을 제거하고 포워딩
    location /site01/ {
        proxy_pass http://127.0.0.1:9621/;
        proxy_set_header X-Forwarded-Prefix /site01;
        proxy_set_header Host $host;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }

    # site02: /site02/를 제거하고 포워딩
    location /site02/ {
        proxy_pass http://127.0.0.1:9622/;
        proxy_set_header X-Forwarded-Prefix /site02;
        proxy_set_header Host $host;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
}
```

`https://host.example.com/site01/webui/`를 탐색하면 site01의 WebUI가 표시되고, `https://host.example.com/site02/webui/`를 탐색하면 site02가 표시됩니다. 동일한 Docker 이미지가 두 사이트를 모두 제공합니다 — 사이트별 빌드 아티팩트 없음, 프리픽스 변경 시 리빌드 없음.

### 각 계층에서 보이는 것

| 계층 | site01 GET /webui/ |
| --- | --- |
| 브라우저 주소창 | `https://host.example.com/site01/webui/` |
| nginx 수신 | `/site01/webui/` |
| nginx 포워딩 | `/webui/` |
| FastAPI `root_path` | `/site01` |
| `app.mount` 해석 | `/webui/` |
| 주입된 `apiPrefix` | `/site01` |
| 주입된 `webuiPrefix` | `/site01/webui/` |
| HTML의 에셋 URL | `./assets/index-abc.js` (`https://host.example.com/site01/webui/assets/index-abc.js`로 해석됨) |

---

## 단일 이미지 Docker 레시피

`Dockerfile`은 WebUI를 프리픽스 없이 한 번 빌드합니다:

```dockerfile
FROM oven/bun:1 AS webui-build
WORKDIR /src/lightrag_webui
COPY lightrag_webui/package.json lightrag_webui/bun.lock ./
RUN bun install --frozen-lockfile
COPY lightrag_webui/ ./
COPY lightrag/api/webui/.gitkeep /src/lightrag/api/webui/.gitkeep
RUN bun run build

FROM python:3.11-slim
COPY --from=webui-build /src/lightrag/api/webui /app/lightrag/api/webui
# … 나머지 이미지 …
```

동일한 이미지에서 원하는 수의 컨테이너를 실행하고, 각각 고유한 프리픽스를 지정하세요:

```bash
# 일반 단일 인스턴스, 프리픽스 없음.
docker run --rm -p 9621:9621 lightrag:latest

# 동일한 이미지, 다른 프리픽스 — 런타임에서 결정.
docker run --rm -e LIGHTRAG_API_PREFIX=/site01 -p 9621:9621 lightrag:latest
docker run --rm -e LIGHTRAG_API_PREFIX=/site02 -p 9622:9621 lightrag:latest
```

### Kubernetes Ingress 동등 설정

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: lightrag-multisite
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  rules:
  - host: host.example.com
    http:
      paths:
      - path: /site01(/|$)(.*)
        pathType: ImplementationSpecific
        backend:
          service:
            name: lightrag-site01
            port: { number: 9621 }
      - path: /site02(/|$)(.*)
        pathType: ImplementationSpecific
        backend:
          service:
            name: lightrag-site02
            port: { number: 9621 }
```

백엔드는 여전히 `LIGHTRAG_API_PREFIX=/site01` / `=/site02`를 설정합니다.

---

## `bun run dev`를 사용한 로컬 개발

> **항상 `http://localhost:5173/`을 열세요 — 루트 경로, `/webui` 없음, `/site01` 없음 — 아래 시나리오에 상관없이.**
>
> Vite의 개발 서버는 어떤 프리픽스를 설정하더라도 SPA를 자체 루트(`/`)에서 제공합니다. `VITE_DEV_API_PREFIX`는 페이지가 로드된 *후* SPA가 API URL을 구성하는 방식과 개발 프록시가 가로채는 경로에만 영향을 미칩니다. `localhost:5173/site01/webui/`에 접근하는 것은 작동하지만(Vite의 SPA 폴백이 동일한 `index.html`을 반환), 이는 표준 진입점이 아닙니다.
>
> 이는 [`vite.config.ts`](../lightrag_webui/vite.config.ts)의 `base: './'` 설정의 의도적인 결과입니다 — 프로덕션 빌드를 어떤 수의 리버스 프록시 마운트 포인트에서도 재사용 가능하게 하는 동일한 설정입니다.

개발 서버는 프로덕션 주입을 미러링합니다: FastAPI 서버가 요청 시점에 사용하는 것과 동일한 `transformIndexHtml` 메커니즘을 통해 `index.html`을 제공하므로, SPA는 개발 환경에서도 프로덕션과 동일한 방식으로 `window.__LIGHTRAG_CONFIG__`를 읽습니다. 중요한 환경 변수는 **두 가지**뿐입니다:

| 변수 | 목적 | 위치 |
| --- | --- | --- |
| `VITE_BACKEND_URL` | 개발 서버가 프록시된 API 호출을 포워딩하는 곳 | `lightrag_webui/.env*` |
| `VITE_DEV_API_PREFIX` | **시뮬레이션할** 프리픽스 (백엔드의 `LIGHTRAG_API_PREFIX`와 일치). 비어 있으면 프리픽스 없음. | `lightrag_webui/.env*` |

`VITE_DEV_API_PREFIX`는 브라우저에서 `window.__LIGHTRAG_CONFIG__`에 `apiPrefix`를 주입하여 백엔드 동작을 미러링합니다. 또한 `VITE_API_ENDPOINTS`의 프리픽스 역할을 하여 백엔드 API에 올바르게 접근할 수 있게 합니다. 매칭되는 `webuiPrefix`는 `${VITE_DEV_API_PREFIX}/webui/`로 자동으로 파생됩니다 — 별도의 변수가 필요 없습니다.

세 가지 시나리오가 만날 수 있는 모든 경우를 다룹니다:

### 시나리오 1 — 단일 인스턴스 개발 (프리픽스 없음, 프록시 없음)

기본값입니다. 기존 `.env.development` 외에 아무것도 설정하지 마세요.

```
Browser ──► localhost:5173 (Vite) ──► localhost:9621 (백엔드, 프리픽스 없음)
```

```bash
# lightrag_webui/.env.development (저장소에 샘플로 이미 포함)
VITE_BACKEND_URL=http://localhost:9621
VITE_API_PROXY=true
VITE_API_ENDPOINTS=/api,/documents,/graphs,/graph,/health,/query,/docs,/redoc,/openapi.json,/login,/auth-status,/static
# VITE_DEV_API_PREFIX=          ← 비워 두세요
```

실행:
```bash
lightrag-server                  # 터미널 1, LIGHTRAG_API_PREFIX 없음
cd lightrag_webui && bun run dev # 터미널 2; http://localhost:5173/ 열기
```

### 시나리오 2 — 사이트 프리픽스 시뮬레이션

SPA가 `/site01` (또는 원하는 프로덕션 프리픽스)에서 실행되도록 하려면 `VITE_DEV_API_PREFIX=/site01`을 설정하세요. Vite는 매칭되는 `window.__LIGHTRAG_CONFIG__`를 주입하고 프리픽스된 프록시 키를 등록합니다. `fetch("/site01/documents/foo")` 같은 SPA 요청은 `VITE_BACKEND_URL`이 가리키는 곳으로 그대로 포워딩됩니다. 업스트림 — 로컬 백엔드 또는 프로덕션 nginx — 이 프리픽스를 이해할 책임이 있습니다.

```
Browser ──► localhost:5173 (Vite + HMR)
                │
                │  Vite 프록시가 /site01/*을 재작성 없이 그대로 포워딩
                ▼
            VITE_BACKEND_URL  ──►  /site01을 알고 있는 업스트림
```

`.env.local` (gitignored — 개인 개발 설정):
```bash
VITE_BACKEND_URL=…                             # "VITE_BACKEND_URL을 가리킬 곳" 참조
VITE_API_PROXY=true
VITE_API_ENDPOINTS=/api,/documents,/graphs,/graph,/health,/query,/docs,/redoc,/openapi.json,/login,/auth-status,/static
VITE_DEV_API_PREFIX=/site01
```

`bun run dev`를 실행하고 **`http://localhost:5173/`**을 여세요. HMR은 순전히 로컬입니다 — 브라우저는 SPA 에셋을 위해 `localhost:5173`과만 통신하며, 업스트림에서 WebSocket 업그레이드 설정이 필요 없습니다.

#### `VITE_BACKEND_URL`을 가리킬 곳

프리픽스 인식 업스트림이 어디에 있는지에 따라 두 가지 옵션 중 선택합니다. Vite 측 설정은 동일합니다. 이 하나의 변수만 다릅니다.

**A. `LIGHTRAG_API_PREFIX=/site01`이 있는 로컬 백엔드** (어디에도 nginx 없음) — 가장 간단한 설정, 노트북에서 두 개의 프로세스. Vite의 프록시 자체가 리버스 프록시 역할을 합니다.

```bash
VITE_BACKEND_URL=http://localhost:9621
```
```bash
# 터미널 1
LIGHTRAG_API_PREFIX=/site01 lightrag-server
# 터미널 2
cd lightrag_webui && bun run dev
```

**B. 프로덕션 nginx를 통해 도달하는 실제(원격) 백엔드** — 로컬에서 재현하기 어려운 데이터/설정이 있는 실제 백엔드가 있을 때 유용합니다. nginx가 이미 `/site01/`을 제거한 후 백엔드에 포워딩합니다. 개발 프런트엔드는 프로덕션에서 아무것도 변경하지 않고 혜택을 받습니다.

```bash
VITE_BACKEND_URL=https://prod.example.com      # 또는 http://10.0.0.5 — nginx URL
```

#### `VITE_BACKEND_URL`에 `/site01`을 포함하지 않는 이유

Vite는 요청 경로를 **그대로** 포워딩합니다(재작성 없음). 브라우저가 이미 `/site01/documents/foo`를 보내므로 Vite가 업스트림에 보내는 URL은 `${VITE_BACKEND_URL}/site01/documents/foo`가 됩니다. `VITE_BACKEND_URL=https://prod.example.com/site01`로 설정하면 `https://prod.example.com/site01/site01/documents/foo`가 되어 — 중복된 프리픽스로 nginx와 백엔드 모두 거부합니다. 항상 `VITE_BACKEND_URL`을 업스트림 **루트**로 지정하세요.

#### 일반적인 함정 (주로 옵션 B에 관련)

- **HTTPS 업스트림 + 자체 서명 인증서**: Vite의 프록시가 기본적으로 거부합니다. 비공개 인증서를 가진 스테이징 프록시를 대상으로 할 때 인증서 검증을 건너뛰려면 `vite.config.ts`에 `proxy: { ..., secure: false }`를 설정하세요.
- **인증 필요**: 업스트림이 `LIGHTRAG_API_KEY`를 요구하는 경우, 프로덕션에서 하는 것과 동일하게 개발 SPA를 통해 로그인하세요 — 인증 토큰이 프록시를 통해 그대로 흐릅니다.
- **CORS 오류**: 브라우저가 `localhost:5173`에 대한 동일 출처 요청을 보므로 발생하지 않아야 합니다. 나타난다면 `changeOrigin: true`가 적용되어 있는지 확인하세요 (`vite.config.ts`에 기본적으로 있음).

### 빠른 결정 매트릭스

| 시나리오 | `VITE_BACKEND_URL` | `VITE_DEV_API_PREFIX` | 개발 프록시가 통신하는 업스트림 | 브라우저에서 열기 |
| --- | --- | --- | --- | --- |
| 1. 기본 단일 인스턴스 개발 | `http://localhost:9621` | 미설정 | 로컬 백엔드, 프리픽스 없음 | `http://localhost:5173/` |
| 2A. 로컬에서 프리픽스 시뮬레이션 (nginx 없음) | `http://localhost:9621` | `/site01` | `LIGHTRAG_API_PREFIX=/site01`인 로컬 백엔드 | `http://localhost:5173/` |
| 2B. 프로덕션 nginx를 통해 실제 백엔드 접근 | `https://prod.example.com` | `/site01` | 이미 `/site01/`을 스트립하는 원격 nginx | `http://localhost:5173/` |

2A와 2B는 **`VITE_BACKEND_URL` 외에는 모든 것이 동일합니다** — 선택은 순전히 "프리픽스 인식 업스트림이 내 노트북에 있는가, 아니면 프로덕션에 있는가?"입니다.

**"브라우저에서 열기" 열은 항상 `http://localhost:5173/`입니다 — 모든 개발 시나리오에서 진입점입니다.** 행 사이에서 변하는 것은 API 트래픽이 최종적으로 어디에 도달하는가입니다. SPA 자체는 항상 개발 서버의 루트에서 제공됩니다.

---

## 문제 해결

### WebUI 접근 시 에셋 URL 404

기본 URL은 `/`로 끝나야 합니다. `/site01/webui`(후행 슬래시 없음)에 접근하면 브라우저가 `./assets/foo.js`를 `/site01/`에 상대적으로 해석하여 404가 됩니다. 서버가 이미 슬래시 없는 형식을 슬래시 있는 형식으로 리다이렉트합니다. 리다이렉트가 nginx에 도달하고 있는지 확인하세요(`X-Forwarded-Prefix`와 nginx가 후행 슬래시와 함께 `proxy_pass http://…/`를 사용하는지 확인).

### 배포 후 `window.__LIGHTRAG_CONFIG__`의 `apiPrefix`가 비어 있음

페이지 소스를 확인하세요. 주입된 `<script>` 태그 대신 리터럴 플레이스홀더 `<!-- __LIGHTRAG_RUNTIME_CONFIG__ -->`가 보인다면, 요청이 `SmartStaticFiles`를 거치지 않은 것입니다 — 실행 중인 컨테이너에 `lightrag/api/webui/index.html`이 존재하고 WebUI 마운트가 성공했는지 확인하세요(서버는 시작 시 `WebUI assets mounted at <path>`를 로그합니다).

### `VITE_DEV_API_PREFIX`가 설정된 상태에서 `bun run dev` 프록시가 404를 반환

백엔드도 매칭되는 `LIGHTRAG_API_PREFIX`로 실행 중인지 확인하세요. 개발 프록시는 프리픽스된 경로를 그대로 포워딩합니다. 백엔드에 프리픽스가 설정되지 않은 경우, 해당 경로에 라우트를 등록하지 않습니다.

### WebUI를 완전히 비활성화하고 싶음

프런트엔드를 빌드하지 마세요 — `lightrag/api/webui/index.html`이 존재하지 않으면 서버가 WebUI 마운트를 건너뛰고, `/`와 WebUI 경로를 `/docs`로 리다이렉트합니다. 런타임 설정 주입은 빌드 아티팩트의 존재를 통해 순전히 opt-in 방식입니다.
