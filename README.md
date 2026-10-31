# 툴픽 (ToolPick) v2

GitHub에서 스타가 빠르게 늘어나는 개발 도구를 매일 찾아, AI로 거르고, 분야별 순위로 보여 주는 서비스입니다.

- 서비스: https://toolpickv2.vercel.app
- 백엔드 API: https://toolpick-v2.onrender.com

## 주요 기능

- **분야별 순위**: 8개 분야마다 최근 7일 스타 증가량 Top 10
- **레포 상세**: 스타 추이 차트, 설치 명령 복사
- **GitHub 로그인과 북마크**
- **관리자 검수**: AI가 판별을 보류한 레포를 사람이 승인하거나 거절
- **MCP 서버**: AI 에이전트가 순위, 레포 정보, 카테고리 목록을 도구로 조회

## 구조

```text
브라우저 ──> Vercel (Next.js, frontend/)
               │  rewrite: /api, /oauth2, /login
               ▼
             웹 프로세스 (backend jar, 기본 프로필) ──┐
               · 읽기 API · GitHub 로그인 · 북마크     ├──> PostgreSQL (Neon)
               · 관리자 검수 · MCP(/mcp)               │
                                                      │
GitHub Actions (daily-batch.yml)                      │
  └> 배치 프로세스 (같은 jar, batch 프로필) ───────────┘
       · GitHub API · Groq/Gemini API
```

- 웹과 배치는 **같은 jar를 다른 프로필로 띄운 별도 프로세스**입니다. 웹은 DB만 읽고, 외부 API는 배치에서만 부릅니다.
- 매일 배치는 외부 cron(cron-job.org)이 03:17·05:17 KST에 `daily-batch.yml`을 실행합니다.
- 배치 단계: 후보 검색(DISCOVER) → 스타 수 수집(COLLECT) → 순위 계산(RANK) → AI 판별(ANALYZE) → 순위 재계산(RANK) → 완료(DONE)


## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4, Spring Data JPA, Spring Security (OAuth2), Flyway, Caffeine, Spring AI MCP Server |
| 프론트엔드 | Next.js 16, React 19, TypeScript, Tailwind CSS 4, shadcn/ui, Recharts |
| DB | PostgreSQL 17 (운영: Neon) |
| AI | Groq, Gemini (레포 판별) |
| 테스트 | JUnit, Testcontainers, WireMock, MockMvc |
| 배포 | Render(웹), Vercel(프론트엔드), GitHub Actions(배치·CI) |

## 폴더 구성

| 경로 | 내용 |
| --- | --- |
| `backend/` | Spring Boot 앱. 기능별 패키지(`collector`, `analysis`, `ranking`, `batch`, `repo`, `category`, `user`, `admin`, `mcp`, `common`) |
| `frontend/` | Next.js 앱 |
| `.github/workflows/` | `ci.yml`(테스트·빌드), `daily-batch.yml`(매일 배치) |
| `docs/` | 구조 문서와 작업 기록 |
| `docker-compose.yml` | 로컬 개발용 PostgreSQL |


