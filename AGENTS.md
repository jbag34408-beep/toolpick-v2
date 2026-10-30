# 툴픽(ToolPick) v2 에이전트 가이드

이 저장소에서 일하는 AI 에이전트(Claude Code, Codex, Cursor 등)가 작업 전에 읽는 규칙이다.
툴픽은 GitHub에서 스타가 빠르게 늘어나는 개발 도구를 매일 찾아, AI로 거르고, 분야별 순위로 보여 주는 서비스다.
전체 구조는 `docs/ARCHITECTURE.md`에, v2 백엔드 설계와 그 근거는 `docs/design/v2-00-backend.md`에 있다.

## 1. 저장소 구조와 명령

명령은 Windows PowerShell 5 기준이다. PowerShell 5는 `&&`를 지원하지 않으므로, 이어지는 명령은 줄을 나눠 친다.

| 경로 | 내용 | 확인 명령 |
| --- | --- | --- |
| `backend/` | Spring Boot 4, Java 21, JPA, Flyway, Spring Security. 같은 jar를 웹(기본 프로필)과 배치(`batch` 프로필)로 띄운다 | `cd backend` 후 `.\gradlew.bat test` (Docker 필요: Testcontainers) |
| `frontend/` | Next.js 16, TypeScript, Tailwind, shadcn/ui (v1에서 그대로 옮김) | `cd frontend` 후 `npm run lint`, `npm run build` |
| `.github/workflows/` | `ci.yml`(테스트·빌드), `daily-batch.yml`(배치 수동 실행) | 수정 시 cron은 UTC 기준 |
| `docs/` | 구조 문서, 설계 문서(`docs/design/`), 날짜별 작업 내역(`docs/log/`) | - |
| `docker-compose.yml` | 로컬 개발 DB | `docker compose up -d` |

프론트엔드 작업 전에는 `frontend/AGENTS.md`도 읽는다. Next.js 16은 학습 데이터와 API가 다를 수 있다.

## 2. 작업 경계

- **사람 승인 없이 `main`에 푸시하지 않는다.** 배포 연결이 생기면 `main` 푸시가 곧 배포가 된다.
- **PR과 커밋은 사람이 검토하고 테스트를 통과한 뒤에만 올린다.** 자동으로 PR을 열지 않는다.
- 운영 DB에 쓰는 SQL(UPDATE, DELETE)은 실행하지 않고 사람에게 제안만 한다.
- 비밀값(`GITHUB_TOKEN`, `GEMINI_API_KEY`, `GROQ_API_KEY`, `DATABASE_URL`, `GITHUB_OAUTH_CLIENT_SECRET`)은 코드·로그·문서·커밋 메시지에 쓰지 않는다. 로컬 값은 `backend/.env`(git 제외)에만 둔다.
- **프론트엔드 디자인은 사람이 직접 조정한다.** 요청 없이 화면이나 스타일을 바꾸지 않는다.

## 3. 작업 크기 분류

작업을 시작하기 전에 둘 중 어디에 속하는지 먼저 정하고 사람에게 알린다.

| 종류 | 기준 | 절차 |
| --- | --- | --- |
| 작은 수정 | 테스트 제외 변경 100줄 이하 **그리고** 핵심 패키지(`batch`, `analysis`, `collector`, `ranking`) 파일 3개 미만, 스키마 변경 없음 | 바로 구현 → 테스트 → 사람 검토 |
| 큰 변경 | 위 기준을 넘거나, 배치 단계·판별 규칙·순위 계산·DB 스키마·보안 설정을 바꿈 | ① `docs/design/NN-이름.md` 설계 문서 작성 → ② 사람 승인 → ③ 구현 |

- 큰 변경을 100줄 기준에 맞추려고 여러 작업으로 쪼개지 않는다.
- 설계 문서에는 반드시 **목표(이번에 할 것)**와 **비목표(이번에 안 할 것)**, 실패 시나리오, 테스트 계획을 적는다.
- 설계 문서는 대화를 모르는 사람이 읽어도 이해되게 쓴다. 대화에서 정한 것은 전부 문서에 있어야 한다.

## 4. 지킬 설계 원칙

어떤 변경도 아래를 깨면 안 된다. 깨야 한다면 설계 문서에 이유를 쓰고 사람 승인을 받는다.

- **화면과 MCP는 DB만 읽는다.** GitHub·Groq·Gemini API는 배치 프로세스(`batch` 프로필)에서만 부른다.
- **배치 단계는 멱등하다.** 같은 날 다시 돌려도 결과가 같다(upsert, 미판별만 처리, 순위는 지우고 다시 계산).
- **외부 API를 부르는 동안 DB 트랜잭션을 열지 않는다.** 네트워크 호출 뒤에 짧은 트랜잭션으로 저장한다. 커넥션 풀은 5개뿐이다.
- **레포 하나의 오류가 전체를 막지 않는다.** 그 레포만의 문제는 격리(검수 대기)하고 다음으로 넘어간다. 키·한도·장애처럼 전체에 걸친 문제만 그날 작업을 멈춘다.
- **스키마는 새 Flyway 파일(`V2__…`)로만 바꾼다.** 이미 적용된 `V1__init.sql`은 고치지 않는다(운영 DB 체크섬).
  - v1으로 되돌릴 수 있게 열·테이블·인덱스 추가만 한다. 새 `NOT NULL` 열에는 `DEFAULT`를 둔다.
  - `batch_run.step`에는 v1이 아는 값(`DISCOVER, COLLECT, ANALYZE, RANK, DONE`)만 쓴다.
- **무료 한도 안에서 돈다.** Groq 분당 8,000토큰·하루 한도, AI 판별 하루 100개·요청 간격 20초, Neon 무료 DB(1GB). 배치는 `stale-after`(70분) 안에 끝나야 한다(판별 마감 시각이 이를 지킨다).
- **웹과 배치는 다른 프로세스다.**
  - 배치 중복 방지는 `synchronized`가 아니라 DB 조건부 SQL(`BatchRunStore`)로 한다.
  - 배치는 웹 캐시를 비울 수 없으므로, 순위 캐시는 TTL(10분)로 갱신한다.
  - 웹은 인스턴스 1개로 운영한다. 로그인 세션이 메모리에 있기 때문이다.
- 하지 않을 것: 마이크로서비스 분리, 메시지 큐 도입, 유료 플랜 전제 설계.

## 5. 코드 규칙

### 백엔드
- 계층은 Controller(HTTP) → Service(업무 규칙·트랜잭션) → Repository(DB) → Entity다. 웹 API와 MCP 도구는 같은 Service를 쓴다.
- Controller는 Entity 대신 DTO(`record`)를 돌려준다. **DTO 모양을 바꾸면 `frontend/lib/api.ts`가 깨진다.** 꼭 바꿔야 하면 설계 문서에 프론트 영향을 적는다.
- 패키지는 기능별로 나눈다(`collector`, `analysis`, `ranking`, `batch`, `repo`, `category`, `user`, `admin`, `mcp`, `common`). 새 기능도 기능 패키지에 넣는다.
- 웹에서만 필요한 설정(보안, CORS)은 `@ConditionalOnWebApplication`을 붙여 batch 프로필에서 만들어지지 않게 한다.
- SQL은 바인딩 변수만 쓴다. 문자열을 이어 붙여 쿼리를 만들지 않는다.
- 외부 글(README 등)을 AI 프롬프트에 넣을 때는 `<repo_data>` 태그를 지워 프롬프트 주입을 막는다. AI가 낸 설치 명령은 README에 글자 그대로 있을 때만 남긴다.
- 레포의 정체성은 `github_id`로 판단한다. `full_name`은 바뀔 수 있다. 이름을 비켜 둔 옛 행(`full_name`에 `#`)은 수집·판별·규칙 필터 대상에서 뺀다.

### 프론트엔드
- 기본은 서버 컴포넌트다. 버튼·차트처럼 상호작용이 필요한 부분만 클라이언트 컴포넌트로 만든다.
- 백엔드 응답 타입은 `frontend/lib/api.ts`에 모은다.
- 브라우저는 Vercel 주소만 부른다. 백엔드 호출은 `/api`, `/oauth2`, `/login` rewrite를 거친다.

### 주석
- 주석은 한국어로 쓴다. 새 개념을 쓰면 기존 코드처럼 `[CS]`(개념)·`[문법]`(언어 문법) 태그로 짧게 설명한다.
- "왜 이렇게 했는가"를 쓴다. 코드를 그대로 다시 읽어 주는 주석은 쓰지 않는다.

## 6. 테스트

- 결함 수정과 새 기능에는 테스트를 함께 쓴다. **고치기 전 코드에서 실패하는 테스트**여야 의미가 있다.
- 외부 API는 WireMock이나 `@MockitoBean`, DB는 Testcontainers(PostgreSQL), 웹·보안은 MockMvc로 시험한다. 실제 외부 API를 테스트에서 부르지 않는다.
- MockMvc의 `csrf()`는 공유 컨텍스트의 CSRF 저장소를 바꿔 끼운다. 쿠키 발급처럼 실제 응답 헤더가 중요한 것은 실제 HTTP로 확인한다(`CsrfCookieHttpTest`).
- 백엔드 변경 후 `.\gradlew.bat test`, 프론트엔드 변경 후 `npm run lint`와 `npm run build`가 통과해야 작업을 끝낸 것으로 본다.

## 7. 운영 주의

- 배치는 `java -jar app.jar --spring.profiles.active=batch`로 실행한다. 수동 재실행은 `--date=YYYY-MM-DD`로 날짜를 준다.
  - 종료 코드: 0은 완료 또는 이미 완료, 1은 실패, 2는 다른 실행이 도는 중이라 건너뜀.
- 실행 환경의 제한 시간(Actions `timeout-minutes`, Cloud Run 잡 task timeout)은 `toolpick.job.stale-after`(70분)보다 짧게 둔다. 지금 `daily-batch.yml`은 65분이다.
- 배치를 예약 실행으로 켤 때 cron은 정시(0분)를 피한다(실행 누락 사례). 03:17 KST는 `17 18 * * *`(UTC)다.
- v1과 같은 DB를 쓰는 기간에는 v1 웹과 v1 배치 워크플로를 꺼 둔다. v1 웹이 깨어나면 실행 중인 배치 기록을 FAILED로 바꾼다.
- 환경 변수 값 끝에 줄바꿈이 들어가면 HTTP 헤더 오류가 난다. 키는 `ToolPickProperties`가 strip하지만 다른 값은 직접 확인한다.

## 8. 작업을 마칠 때

- 무엇을 왜 바꿨는지 파일별로 요약한다.
- 하루 작업은 `docs/log/YYYY-MM-DD_작업내역.md`에 남긴다(커밋 표, 바뀐 코드 요약, 겪은 문제, 남은 일).
- 확인하지 못한 것(실제 외부 API 응답, 운영 DB 숫자 등)은 "확인 못 함"으로 분명히 적는다.
