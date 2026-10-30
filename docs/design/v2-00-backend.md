# v2-00-backend: v2 백엔드를 새로 만들고 재설계 01~05를 반영한다

2026-10-30 · 상태: **리뷰 반영 완료 · 구현 완료(테스트 68개 통과, `/ponytail-review` 반영) · 배포 전**
사람 지시(2026-10-30): "백엔드는 v2로 새로 만들고, 새 저장소를 만들고, 프론트엔드만 옮기고, 리뷰한 설계로 구현"
리뷰 기록: `v2-00-backend-review.md` (Goldfish 3명, 🔴 6 · 🟡 23 · 🟢 14 → 전부 반영 또는 사유 기록)

## 1. 문제

v1(`Toolpick1`)은 웹 API와 매일 배치가 한 프로세스 안에 있다.

- 배치가 웹 서버 안의 `@Async` 스레드로 돈다. 그래서 재배포나 재시작이 배치를 끊는다. 서버가 뜰 때는 `DailyJobService.failInterruptedRuns`가 RUNNING 기록을 전부 FAILED로 바꾼다(`DailyJobService.java:68-75`).
- 배치를 돌리려면 Actions가 잠든 Render를 깨우고, HTTP로 시작시키고, 최대 60분 동안 지켜봐야 한다.
- 판별 대기열은 id 오름차순이다(`RepoRepository.java:28-33`). 그래서 지금 뜨는 레포가 늦게 판별된다.
- ANALYZE가 끝나야 RANK가 돈다. 판별(약 37분)이 끝날 때까지는 어제 순위가 보이고, 판별이 실패하면 그날 순위가 없다.
- 이름만 봐도 거를 수 있는 레포까지 AI 하루 한도(100개)를 쓴다.
- 모든 레포를 매일 수집한다(`CollectorService.java:60` `findAll()`). Neon 무료 저장 공간은 1GB다.

운영 DB 숫자(대기열 길이, 일별 유입)는 확인하지 못했다.

## 2. 목표 / 하지 않을 것

**목표**
1. 웹과 배치를 다른 프로세스로 실행한다. 같은 jar를 웹은 기본 프로필로, 배치는 `batch` 프로필로 띄운다. 배치는 오늘 작업을 한 번 동기로 실행하고 종료 코드(0 완료, 1 실패, 2 건너뜀)를 남긴다. 이렇게 하면 Cloud Run 잡, GitHub Actions 러너 어디서 돌려도 같은 코드가 동작한다.
2. 프론트엔드 계약을 그대로 지킨다(3절 표). `frontend/` 코드는 바꾸지 않는다.
3. 재설계 01~05를 4절의 결정대로 반영하고, 항목마다 테스트를 둔다.
4. 스키마를 바꾸지 않는다. `V1__init.sql`은 바이트 그대로 둔다(Flyway 체크섬). `batch_run.step`에도 v1 enum이 모르는 값을 쓰지 않는다.

**하지 않을 것**
- 배포 인프라(Cloud Run, Scheduler, Vercel 전환)는 만들지 않는다. 웹을 어디에 둘지(열린 결정 E3)는 아직 정하지 않았다. 배치는 수동 실행 워크플로만 둔다(10절).
- 프론트엔드 화면을 바꾸지 않는다. 재설계 06은 보류한다.
- 스냅샷 보관 정책(행 삭제), 재판별, 웹과 배치 사이의 캐시 무효화 호출은 만들지 않는다.
- 마이크로서비스, 메시지 큐, 유료 플랜은 쓰지 않는다.

## 3. 현재 동작 (v1)과 지킬 프론트 계약

v1의 배치 흐름은 다음과 같다.

1. Actions가 `POST /internal/jobs/daily`를 보낸다.
2. `synchronized` 안에서 `batch_run`을 확인한다.
3. `@Async`로 DISCOVER → COLLECT → ANALYZE → RANK를 실행한다.
4. 단계마다 `startedAt`을 비교하며 기록하고, 끝나면 `evictAll()`을 부른다.

**프론트 계약**: v2도 이 표대로 동작해야 하고, 테스트로 지킨다.

| 동작 | v1 근거 | v2 테스트 |
| --- | --- | --- |
| 공개 GET `/api/categories`, `/api/rankings[/{slug}]?date=`, `/api/repos/{owner}/{name}[/stars?days=]` | 각 컨트롤러 | `PublicApiTest` |
| `getStars`는 fullName을 인코딩하지 않고 `/api/repos/${fullName}/stars`로 부른다 → 두 구간 경로 유지 | `api.ts:161` | `PublicApiTest` |
| 404는 ProblemDetail로 응답하고 프론트는 null로 처리한다. `IllegalArgumentException`은 400 | `GlobalExceptionHandler` | `PublicApiTest` |
| 비로그인 `/api/me`는 401(302 아님) | `SecurityConfig.java:60` | `FrontendContractTest`, `SecurityAndMemberApiTest` |
| XSRF-TOKEN 쿠키를 첫 응답부터 HttpOnly 없이 발급 | `SecurityConfig.java:40-47` | `CsrfCookieHttpTest`(실제 HTTP) |
| 변경 요청은 CSRF가 없으면 403 | 같음 | `SecurityAndMemberApiTest` |
| `POST /api/logout`은 204 | `SecurityConfig.java:69-71` | `FrontendContractTest` |
| 북마크 POST는 201(새로 만듦)/200(이미 있음), DELETE는 204 | `BookmarkController.java:41,47` | `SecurityAndMemberApiTest` |
| `/oauth2/**`, `/login/**` 허용. 콜백은 `${frontend-origin}/login/oauth2/code/github`, `forward-headers-strategy: framework` | `application.yml:9,42` | 설정 그대로 |
| 관리자 API는 ROLE_ADMIN만(`ADMIN_LOGINS`) | `SecurityConfig.java:56` | `SecurityAndMemberApiTest` |
| `/mcp` 도구 3개 | `ToolPickMcpTools` | `ToolPickMcpToolsTest` |

## 4. 제안 (구현된 내용)

### 4.1 프로세스 구조

| 실행 | 프로필 | 하는 일 |
| --- | --- | --- |
| 웹 | 기본 | 읽기 API, 로그인, 북마크, 관리자 검수, MCP, Flyway 마이그레이션 |
| 배치 | `batch` | `BatchJobRunner`가 `DailyJob.run(오늘 KST 또는 --date=)`을 실행하고, `main`이 `SpringApplication.exit`로 종료 코드를 남기고 끝낸다 |

- **`application-batch.yml`**: `spring.main.web-application-type: none`, `spring.flyway.enabled: false`(새벽 배치가 스키마를 바꾸지 않게), `spring.ai.mcp.server.enabled: false`.
- **웹에서만 만드는 빈**: `SecurityConfig`와 `CorsConfig`는 `@ConditionalOnWebApplication`으로 웹에서만 만든다. batch 컨텍스트에 보안 필터와 MCP 서버가 없는지는 `BatchContextTest`로 확인한다.
- **`System.exit` 위치**: 빈 안에서는 부르지 않는다. `BatchJobRunner`는 `ApplicationRunner`와 `ExitCodeGenerator`를 구현해 결과만 내놓고, 실제 종료는 `ToolpickApplication.main`이 batch 프로필일 때만 한다. 그래서 테스트 JVM이 죽지 않는다. 테스트에서는 `toolpick.batch.run-on-startup: false`로 끈다.
- **없앤 것**: `/internal/jobs/**`, `JOB_SECRET`, `@Async`, `AsyncConfig`, `synchronized`, 시작 시 RUNNING을 전부 FAILED로 바꾸던 리스너.
- **웹 키**: 웹은 외부 API를 부르지 않으므로 `GITHUB_TOKEN`·`GEMINI_API_KEY`에 빈 기본값을 준다.
- **캐시**: 배치는 웹의 Caffeine 캐시를 비울 수 없다. 그래서 순위 캐시만 TTL을 10분으로 줄였다(분야 캐시는 1시간 유지). 순위를 한 번 조회하면 쿼리 3~4개가 돈다. 화면에는 Vercel `revalidate`(1시간, 만료 뒤 첫 요청은 옛 값) 때문에 보통 1시간 남짓 늦게 반영된다. 이 지연은 받아들인다(E10 기본값).

### 4.2 배치 실행과 중복 방지 (재설계 02·05)

**실행 순서**: DISCOVER → COLLECT → RANK → ANALYZE → RANK(다시) → DONE

- 판별 뒤 다시 계산하는 단계도 `batch_run.step`에는 **RANK로 기록**한다. v1 enum(`DISCOVER, COLLECT, ANALYZE, RANK, DONE`)이 모르는 값이 남으면, v1으로 되돌렸을 때 v1이 시작하지 못하기 때문이다.
- 다시 계산은 AI로 하나라도 판별했을 때만 한다.

**시작 판단**(`BatchRunStore.claim`)은 조건부 SQL 한 문장씩으로 처리하고, 시각은 DB `now()`를 쓴다.

| 순서 | SQL | 결과 |
| --- | --- | --- |
| 1 | 다른 날짜에 시작한 지 `stale-after`가 안 된 RUNNING이 있는가 | 있으면 건너뜀(2). v1 단일 실행기의 직렬화를 유지 |
| 2 | `INSERT … ON CONFLICT (run_date) DO NOTHING RETURNING` | 넣었으면 맡는다. 동시에 시작해도 하나만 맡는다 |
| 3 | `UPDATE … WHERE run_date=:d AND (FAILED OR RUNNING이고 started_at <= now()-stale) RETURNING` | 바꿨으면 맡는다. 동시에 다시 시작해도 하나만 맡는다 |
| 4 | 남은 상태 | SUCCESS면 0(이미 완료), RUNNING이면 2(도는 중) |

- **단계·결과 기록**: `WHERE id=? AND started_at=? AND status='RUNNING'`으로 기록한다. 0행이면 다른 실행이 이어받은 것이므로, 덮어쓰지 않고 2로 끝낸다.
- **판별 마감 시각**: `started_at + stale-after - 10분`. 지나면 그날 판별을 멈춘다. 다른 실행이 이 기록을 이어받기 전에 판별을 끝내 AI 호출이 두 배가 되는 일을 막는다. 81분 실행 기록이 있어도 이 장치로 70분 안에 끝난다.
- **판별 결과 저장 충돌**: `repo_analysis.repo_id` UNIQUE 위반이 나면 그 레포만 건너뛴다. 예외는 트랜잭션 밖에서 잡는다.
- **종료 신호**: SIGTERM(시간 초과, 취소)을 받으면 `@PreDestroy`가 지금 실행을 FAILED로 기록한다. 그래서 RUNNING이 70분 동안 남아 재시도를 막지 않는다.
- **실행 환경 제한 시간**: `stale-after`(70분)보다 짧게 둔다. `daily-batch.yml`은 65분이다.

**실패 처리**

| 상황 | 기록 | 종료 코드 | 오늘 순위 |
| --- | --- | --- | --- |
| DISCOVER·COLLECT·첫 RANK 중 예외 | FAILED(error에 단계 이름) | 1 | 어제 |
| ANALYZE 중 예상 못 한 예외 | FAILED | 1 | 첫 RANK 결과 유지 |
| 다시 계산(RANK) 중 예외 | FAILED | 1 | 첫 RANK 결과 유지(delete+insert가 한 트랜잭션이라 빈 상태가 보이지 않음) |
| AI 한도·인증 오류, 마감 시각 도달 | 그날 판별만 멈춤, SUCCESS | 0 | 오늘 |
| 다른 실행이 이어받음 | 건드리지 않음 | 2 | — |
| FAILED 기록 자체가 실패 | 로그만 남김 | 1 | — |

같은 날 FAILED를 다시 돌리면 DISCOVER부터 다시 실행한다. GitHub 호출이 한 번 더 들지만, 모든 단계가 멱등이라 결과는 같다.

### 4.3 판별 순서 (재설계 01)

7일 증가량 대신 **가장 최근 스냅샷 스타 수 큰 순**으로 판별한다. 7일 증가량은 오늘 발견된 레포가 스냅샷 1개라 0이 되기 때문이다. 대상은 수집 범위와 같은 푸시 조건으로 제한한다. 방치된 레포는 순위에 오를 수 없으므로 AI 한도를 쓰지 않는다. 동점은 id 순이라 스냅샷이 없으면 v1과 같은 순서다(`RepoRepository.findUnanalyzedByStars`, native, LATERAL이 `(repo_id, snapshot_date)` UNIQUE 인덱스를 거꾸로 탄다).

### 4.4 규칙 필터 (재설계 03)

- **적용 대상**: 레포 이름(`/` 뒤)만 본다. 이름을 `-`, `_`, `.`로 나눈 단어를 소문자로 바꿔 목록과 비교한다(`RuleFilter`).
- **목록**: `awesome, tutorial, tutorials, dotfiles, roadmap`. 도구 이름에도 쓰이는 `course`, `interview`는 넣지 않았다(리뷰 F8).
- **실행 시점**: AI 판별 대상을 고르기 **전에**, 미판별 전체(`findAllUnanalyzedNames`: id·이름 두 열만, `#` 제외)에 대해 레포마다 짧은 트랜잭션으로 저장한다. 그래서 하루 100개 한도에 세지 않고, 요청 간격 대기나 README 조회도 하지 않는다.
- **저장 값**: `RepoAnalysis.of(repo, AnalysisResult.ruleRejected(raw), null, REJECTED)`. `useful=false`, `confidence=1.00`, `raw={"source":"rule","rule":"<단어>"}`(ObjectMapper로 만든다).
- **오탐 되살리기**: `SELECT a.id, r.full_name FROM repo_analysis a JOIN repo r ON r.id=a.repo_id WHERE a.raw->>'source'='rule'`로 찾는다. 관리자 API `PATCH /api/admin/reviews/{id}`에 `{"decision":"APPROVE","categorySlug":"…"}`를 보내면 승인된다(상태 확인을 하지 않음).
- **판별을 끈 경우**: `max-per-day: 0`이면 규칙 필터도 건너뛴다(그림자 실행에서 DB 쓰기를 막는다).

### 4.5 수집 범위 (재설계 04)

`RepoRepository.findTrackableFullNames(since)`(native)의 조건은 `#` 제외이고, 다음 중 하나에 해당하면 수집한다.

- 북마크된 레포
- 공개(PUBLISHED)된 레포
- REJECTED가 아니고, `last_pushed_at`이 NULL이거나 `>= since`인 레포

`since`는 배치 날짜 - 60일, KST 자정의 `Instant`다(`last_pushed_at`이 TIMESTAMPTZ라서).

- 공개 레포를 항상 수집하는 이유는, 상세 화면의 스타 수와 그래프가 멈추지 않고 다시 활동하면 순위에 오르게 하려는 것이다(리뷰 F6).
- `bookmark.repo_id`에는 단독 인덱스가 없지만 북마크가 적어서 받아들인다.
- `GitHubClient`의 이름 정규식(GraphQL 인젝션 방어)은 그대로 둔다.
- **남는 한계**: 공개되지 않았고 60일 넘게 푸시가 없는 레포는, 생성 90일 이내라 DISCOVER에 다시 걸리지 않는 한 수집에서 빠진 채로 남는다.

### 4.6 운영 보강 (재설계 05의 나머지)

- 판별을 건너뛰거나 연속 오류로 멈출 때 남기는 응답 본문 로그는 앞 300자로 자른다(`AnalysisService.head`).
- `toolpick.analysis.max-per-day: 0`이면 판별 단계를 예외 없이 건너뛴다. v1은 `PageRequest.of(0, 0)`이 예외를 던졌다.
- 키 공백 제거(`ToolPickProperties` compact constructor)는 v1 그대로 둔다.

### 4.7 패키지와 파일

AGENTS.md 5장의 기능별 패키지와 계층을 지킨다.

| 구분 | 파일 |
| --- | --- |
| 새로 씀 | `batch/DailyJob`, `batch/BatchRunStore`, `batch/BatchOutcome`, `batch/BatchJobRunner`, `analysis/RuleFilter`, `AnalysisService`(규칙 필터·마감·저장 충돌·300자), `CollectorService`(수집 범위), `RepoRepository`(판별 순서·수집 범위 쿼리), `ToolPickProperties`(`Job.staleAfter`, `Candidate.trackPushedWithinDays`, `Analysis.rejectNameWords`), `CacheConfig`(캐시별 TTL), `ToolpickApplication`, `application*.yml`, `Dockerfile` |
| v1 로직을 다시 씀(동작 같음) | category·repo·ranking 패키지, `RankingCalculator`(검증된 SQL), `HttpRetry`, `RestClientConfig`, `GlobalExceptionHandler`, `SecurityConfig`·`CorsConfig`(`/internal/**` 제거, 웹 전용) |
| v1 그대로 옮김(계약·보안) | user·admin·mcp 패키지, `GitHubClient`, `GitHubRepoDto`, `GeminiAnalyzer`, `GroqAnalyzer`, `FallbackAnalyzer`, `AiAnalyzer`, `AiQuotaExceededException`, `V1__init.sql`(바이트 동일) |
| 없앰 | `InternalJobController`, `JobResponse`, `DailyJobService`, `DailyJobRunner`, `BatchRun`·`BatchStatus`(JDBC로 대체), `AsyncConfig`, 테스트 `DailyJobServiceTest`·`InternalJobControllerTest`(시나리오는 `DailyJobTest`로 옮김) |

## 5. 검토한 대안

| 대안 | 장점 | 버린 이유 |
| --- | --- | --- |
| v1처럼 웹 안에서 `@Async` + HTTP 트리거 | 변경 적음 | 재시작이 배치를 끊고, 웹을 Cloud Run으로 옮기면 콜드 스타트마다 배치 기록이 FAILED가 된다 |
| Postgres advisory lock으로 중복 방지 | 프로세스가 죽으면 잠금이 자동으로 풀림 | Neon pooler(트랜잭션 모드)에서는 세션 잠금을 보장할 수 없다. 조건부 SQL은 연결 방식과 상관없다 |
| 01: 7일 증가량 순 | 순위와 같은 기준 | 오늘 발견된 레포가 0으로 맨 뒤에 간다 |
| 03: 이름·설명 부분 문자열 | 더 많이 거름 | 오탐이 크고, 거절되면 수집도 멈춘다 |
| 배치 → 웹 캐시 무효화 호출 | 즉시 반영 | 웹 주소, 비밀값, 실패 처리가 더 필요하다. 새벽 배치라 TTL 10분으로 충분하다 |
| 다시 계산 단계를 `RERANK`로 기록 | 로그가 명확 | v1으로 되돌릴 때 enum 변환 예외로 v1이 시작하지 못한다 |

## 6. 지킬 원칙 점검 (AGENTS.md 4장)

- **DB만 읽기(화면·MCP)**: 유지. 외부 API는 배치 프로세스만 부른다.
- **멱등성**: 유지. 발견과 수집은 upsert, RANK는 delete+insert, 판별과 규칙 필터는 미판별만 처리한다. 판별 결과 저장이 충돌하면 그 레포만 건너뛴다.
- **짧은 트랜잭션**: 유지. 외부 호출 뒤에 짧게 저장한다. 시작 판단과 단계 기록은 SQL 한 문장씩이다.
- **한 레포 격리**: 유지. v1의 오류 분류를 그대로 쓴다.
- **Flyway 새 파일**: 스키마 변경 없음. batch 프로필은 Flyway를 돌리지 않는다.
- **무료 한도**: AI는 하루 100개, 간격 20초로 v1과 같다. 규칙 필터는 AI 호출을 줄일 뿐이다. 마감 시각 덕분에 배치는 70분 안에 끝난다.
- **서버 1대 가정 → 바뀜(이유와 대체 수단)**: 배치가 별도 프로세스가 된다. 중복 방지는 DB 조건부 SQL로 한다. 웹 캐시는 TTL로 갱신한다. 웹은 1개 인스턴스로 운영한다(로그인 세션이 메모리에 있음). v2 `AGENTS.md` 4장을 이에 맞게 고쳤다.
- **`#` 옛 행**: 판별 대상, 규칙 필터 대상, 수집 대상 모두에서 뺀다(`RepoRepositoryTest`).
- **`evictAll()`**: 없앴다. 다른 프로세스의 캐시는 비울 수 없으므로 TTL로 대체했다.

## 7. 실패 시나리오

| 상황 | 동작 | 사용자에게 보이는 것 |
| --- | --- | --- |
| GitHub 429·5xx | 3회 재시도 후 예외 → FAILED, 1 | 어제 순위 |
| AI 429·인증 오류 | 그날 판별 중단, SUCCESS | 오늘 순위 |
| ANALYZE·다시 계산 중 예외 | FAILED, 1 | 오늘 순위(첫 RANK) |
| 시간 초과로 SIGTERM | FAILED 기록, 다음 실행이 바로 다시 시작 가능 | 어제 또는 오늘 순위 |
| SIGKILL·정전(기록 못 함) | RUNNING이 남는다. 70분 동안 재시도는 2로 끝나고, 그 뒤 재시도는 다시 시작한다 | 어제 또는 오늘 순위 |
| 같은 날 두 번 실행 | SUCCESS면 0, 도는 중이면 2 | 변화 없음 |
| 동시에 시작 | 하나만 맡는다(`concurrentStartsClaimOnlyOnce`) | 변화 없음 |
| 자정 전후로 날짜가 다른 두 실행 | 늦게 시작한 쪽이 2로 끝남 | 변화 없음 |
| `max-per-day: 0` | 판별·규칙 필터 건너뜀, SUCCESS | 오늘 순위 |

## 8. 데이터·마이그레이션과 v1 공존

- 스키마 변경은 없다. `V1__init.sql`은 바이트가 같다(`cmp`로 확인).
- 규칙 필터가 앞으로 미판별 레포 중 이름이 걸리는 것을 REJECTED로 저장한다. 기존 행은 건드리지 않는다.
- **v1과 같은 DB를 쓸 때**: v1 웹이 깨어나면 `failInterruptedRuns`가 v2 배치의 RUNNING을 FAILED로 바꾼다. 그래서 v2 배치를 운영 DB에 붙이기 전에 v1 `daily-batch.yml`·`keep-warm.yml`을 끄고 v1 웹을 정지한다(v2 리뉴얼 가이드 전환 순서 2·3번).
- v1로 되돌릴 때는 v2 배치를 먼저 멈춘다. `step`에는 v1이 아는 값만 기록하므로 DB는 정리하지 않아도 된다.

## 9. 테스트 계획과 결과 (`.\gradlew.bat test`: 68개 통과)

v1 회귀 테스트 43개를 옮겼다. v1 50개 중 없앤 HTTP 트리거 테스트 7개는 뺐다. 아래는 새로 쓴 25개다. "고치기 전 실패" 열은 v1 코드 기준의 근거다. v1 저장소에서 직접 실행해 보지는 않았다(리뷰 M5의 (b) 선택).

| 테스트 | 확인하는 것 | v1에서 실패하는 이유 |
| --- | --- | --- |
| `RepoRepositoryTest.unanalyzedOrderedByLatestStars` | 스타 많은 순, 스냅샷 없음은 뒤, `#`·판별됨·방치 제외 | v1은 id 순, 방치 포함 |
| `RepoRepositoryTest.trackableScope` | 공개·북마크 항상, REJECTED·방치 제외, 미판별·검수 대기 포함 | v1은 전체 |
| `RuleFilterTest` ×2 | 단어 단위, 대소문자 무시, `concourse` 안 걸림 | 없음 |
| `AnalysisV2Test.ruleRejectedWithoutAiCall` | AI·README 호출 0, REJECTED, `raw.source=rule`, 1.00 | v1은 AI 호출 |
| `AnalysisV2Test.ruleRejectedDoesNotUseDailyLimit` | 한도 1에서도 진짜 도구가 판별됨 | 없음 |
| `AnalysisV2Test.zeroMaxPerDaySkipsWithoutError` | 예외 없이 0, DB 쓰기 없음 | v1은 `IllegalArgumentException` |
| `AnalysisV2Test.stopsAtDeadline`, `errorBodyIsTruncatedForLogs` | 마감, 300자 | 없음 |
| `DailyJobTest.rankIsPublishedBeforeAnalyzeAndRecalculatedAfter` | AI 호출 시점에 오늘 순위가 이미 있고, 끝나면 새 레포도 순위에 있음 | v1은 이 시점에 0행 |
| `DailyJobTest.analyzeFailureKeepsTodaysRanking` | 순위 유지, FAILED(step=ANALYZE), 1 | v1은 순위 없음 |
| `DailyJobTest.failedRunIsRetriedFromStart`, `successfulDayIsNotRunAgain` | 재시도, 중복 실행 방지 | (v1 시나리오 이식) |
| `DailyJobTest.recentRunningRunIsLeftAloneAndReportedAsSkipped` | 2로 끝남 | v1은 응답만 200 |
| `DailyJobTest.staleRunningRunIsRestarted` | 70분 넘은 RUNNING 재시작 | v1은 막힘 |
| `DailyJobTest.runningRunOfAnotherDateBlocks` | 날짜 달라도 직렬화 | (v1은 실행기로 보장) |
| `DailyJobTest.runTakenOverByAnotherProcessStopsWithoutOverwriting` | 이어받기 감지 | (v1 시나리오 이식) |
| `DailyJobTest.shutdownSignalMarksRunningRunFailed` | 종료 신호 시 FAILED 기록 | 없음 |
| `DailyJobTest.restartIsConditional`, `concurrentStartsClaimOnlyOnce` | 조건부 재시작, 동시 시작 4개 중 1개만 | v1은 `synchronized`(한 프로세스) |
| `BatchContextTest` | batch 프로필: 웹·보안 필터·MCP 서버 없이 뜸 | 없음 |
| `FrontendContractTest` ×3, `CsrfCookieHttpTest` | 401, 로그아웃 204, `/internal` 없음, XSRF 쿠키 | 계약 회귀 방지 |

테스트하지 않은 것: 실제 OS 신호 전달과 `main`의 `System.exit`. 종료 코드 1·2는 로컬 프로세스 실행으로 확인했다(11절).

## 10. 구현 계획 (완료)

| 순서 | 내용 | 확인 |
| --- | --- | --- |
| 1 | 빌드·설정·`V1__init.sql`·테스트 기반 | `compileJava` |
| 2 | 웹 API(계약 유지) | `PublicApiTest`, `SecurityAndMemberApiTest`, `FrontendContractTest`, `CsrfCookieHttpTest` |
| 3 | 수집·판별·규칙 필터 | `RepoRepositoryTest`, `AnalysisServiceTest`, `AnalysisV2Test` |
| 4 | 배치 | `DailyJobTest`, `BatchContextTest` |
| 5 | `AGENTS.md`, `CLAUDE.md`, `docs/ARCHITECTURE.md`, `ci.yml`, `daily-batch.yml`(수동 실행만, `timeout-minutes: 65`) | `.\gradlew.bat test`, `npm run lint`, `npm run build` |

## 11. 배포 전 확인과 되돌리기

- 로컬 확인: 웹(`local`)을 띄워 테이블을 만든 뒤, 배치(`local,batch`)를 실행한다. 키가 없으면 DISCOVER에서 GitHub 401이 나고 종료 코드 1, `batch_run`은 FAILED(step=DISCOVER)가 나와야 정상이다.
- 운영 전환 때 볼 로그
  - `오늘 순위 공개`가 `AI 판별` 앞에 나온다.
  - `규칙 거절: N개`
  - `스타 수집: N/M개 (전체 K개 중 추적 대상)`에서 M < K
  - `배치 종료: 결과=COMPLETED, 종료 코드=0`
- 되돌리기: 스키마를 바꾸지 않았고 `step`에도 v1이 아는 값만 쓰므로, v2 배치를 멈추고 v1을 다시 켜면 된다(8절).
