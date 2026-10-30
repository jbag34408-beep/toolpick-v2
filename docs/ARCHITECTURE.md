# 툴픽 v2 구조

설계 근거는 `docs/design/v2-00-backend.md`에 있다. v1(`Toolpick1`)과 비교하면 가장 크게 바뀐 점은 **웹과 매일 배치가 다른 프로세스**라는 것이다.

## 구성

```text
브라우저 ──> Vercel (Next.js, frontend/)
               │  rewrite: /api, /oauth2, /login
               ▼
             웹 프로세스 (backend jar, 기본 프로필) ──┐
               · 읽기 API · GitHub 로그인 · 북마크     │
               · 관리자 검수 · MCP(/mcp)               ├──> PostgreSQL (Neon)
                                                      │
실행 환경(Actions 러너 / Cloud Run 잡 등, 미정)         │
  └> 배치 프로세스 (같은 jar, batch 프로필) ───────────┘
       · GitHub API · Groq/Gemini API
       · 끝나면 종료 코드 0/1/2를 남기고 끝남
```

- 웹은 외부 API를 부르지 않고 DB만 읽는다. 순위 캐시(Caffeine)는 TTL 10분이다.
- 배치는 같은 이미지를 `SPRING_PROFILES_ACTIVE=batch`로 띄운다. 웹 서버, 보안 필터, MCP, Flyway가 꺼진 상태로 뜬다.
- 어디서 돌릴지(웹 호스팅, 배치 실행 위치)는 아직 정하지 않았다. 지금은 `.github/workflows/daily-batch.yml`로 수동 실행만 할 수 있다.

## 매일 배치 (`batch.DailyJob`)

| 순서 | 단계(`batch_run.step`) | 하는 일 | 클래스 |
| --- | --- | --- | --- |
| 0 | — | 시작 판단: 새로 만들기 / 다시 시작 / 건너뛰기 (조건부 SQL) | `BatchRunStore.claim` |
| 1 | DISCOVER | 새 후보 검색(생성 90일, 스타 100 이상, 최대 300개) | `CollectorService.discoverCandidates` |
| 2 | COLLECT | 추적 대상의 오늘 스타 수 저장 | `CollectorService.collectSnapshots` |
| 3 | RANK | 분야별 7일 증가량 Top 10 계산 → **오늘 순위 공개** | `RankingCalculator.calculate` |
| 4 | ANALYZE | 이름 규칙 필터 → AI 판별(최근 스타 많은 순, 하루 100개, 마감 시각까지) | `AnalysisService.analyzePending` |
| 5 | RANK | AI로 판별한 것이 있으면 다시 계산(오늘 공개된 레포 반영) | `RankingCalculator.calculate` |
| 6 | DONE | SUCCESS 기록 | `BatchRunStore.succeed` |

**추적 대상**: 북마크된 레포 + 공개(PUBLISHED)된 레포 + (거절되지 않았고 60일 안에 푸시된 레포). `full_name`에 `#`가 있는 옛 행은 뺀다.

## 테이블

스키마는 v1과 같다(`V1__init.sql`, 바이트 동일). 테이블별 역할은 v1의 `docs/DB_테이블_역할.md`를 따른다.

| 테이블 | 역할 |
| --- | --- |
| `repo` | 추적 레포. 정체성은 `github_id` |
| `star_snapshot` | 날짜별 스타 수 `(repo_id, snapshot_date)` UNIQUE |
| `repo_analysis` | 판별 결과(레포당 1행). `raw.source = 'rule'`이면 규칙 필터로 거절된 것 |
| `category` | 분야 8개 (AI 프롬프트의 slug 목록과 같아야 함) |
| `daily_ranking` | 날짜·분야별 Top 10 |
| `batch_run` | 날짜별 배치 실행 기록. `run_date` UNIQUE |
| `app_user`, `bookmark` | 로그인 사용자와 북마크 |

## 설정 (`application.yml` → `ToolPickProperties`)

| 키 | 기본값 | 뜻 |
| --- | --- | --- |
| `toolpick.job.stale-after` | 70m | 이보다 오래된 RUNNING은 끊긴 실행으로 보고 다시 시작한다. 판별 마감은 이 값에서 10분을 뺀 시각 |
| `toolpick.candidate.track-pushed-within-days` | 60 | 추적·판별 대상의 푸시 기준 |
| `toolpick.analysis.max-per-day` | 100 | 0이면 판별 단계를 건너뛴다(그림자 실행) |
| `toolpick.analysis.reject-name-words` | awesome, tutorial, tutorials, dotfiles, roadmap | 이름 규칙 필터 |
| `toolpick.batch.run-on-startup` | true | batch 프로필에서 앱이 뜨면 배치를 돌릴지(테스트는 false) |

## 로컬 실행

```powershell
docker compose up -d
cd backend
copy .env.example .env
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun                      # 웹 (Flyway가 테이블을 만든다)
$env:SPRING_PROFILES_ACTIVE="local,batch"
.\gradlew.bat bootRun                      # 배치 한 번 (키가 없으면 DISCOVER에서 실패, 종료 코드 1)
```
