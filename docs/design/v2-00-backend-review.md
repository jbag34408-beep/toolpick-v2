# v2-00-backend Goldfish 리뷰 기록

Status: resolved · 2026-10-30

- 대상: `v2-00-backend.md` 초안
- 리뷰어: `egm-design`(feature-spec-standard), `egm-implementation-readiness`, `egm-codebase-details`(implementation-readiness-standard). 셋 다 completed.
- 앞선 리뷰: 이 문서의 바탕이 된 v2 리뉴얼 가이드와 재설계 가이드는 5단계 리뷰(18회)를 이미 거쳤다(`../../../egm-review/consolidated_review_report.md`). 그래서 이번에는 설계 비판과 구현 준비 단계만 돌렸다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 지적 | 처리 |
| --- | --- | --- | --- | --- |
| 1 | IR B1, CD M1, DC F12 | 🔴 | `System.exit` 위치, Runner 형태, 반환값 미정 → 테스트 JVM이 죽을 수 있음 | **반영**: `BatchOutcome`, `ApplicationRunner + ExitCodeGenerator`, `main`에서만 exit, `run-on-startup` 스위치 |
| 2 | IR B2, DC F3 | 🔴 | FAILED·끊긴 RUNNING을 다시 시작할 때 동시성 방어 없음 | **반영**: 조건부 `UPDATE … RETURNING`, `INSERT … ON CONFLICT DO NOTHING`, DB `now()` |
| 3 | DC F1 | 🔴 | 70분 가정이 깨지면(81분 기록) 두 실행이 함께 판별 | **반영**: 판별 마감 시각(stale - 10분), 저장 UNIQUE 충돌은 건너뜀, 실행 환경 제한 65분 |
| 4 | IR B3 | 🔴 | batch 프로필로 앱이 뜨는지 검증 수단 없음 | **반영**: `BatchContextTest`, `application-batch.yml` 내용 명시 |
| 5 | CD B1 | 🔴 | CSRF 쿠키 즉시 발급, 로그아웃 204 등 프론트 계약이 문서·테스트에 없음 | **반영**: 3절 계약 표, `CsrfCookieHttpTest`(실제 HTTP), `FrontendContractTest` |
| 6 | CD B2 | 🔴 | 설정 record 변경과 영향 파일 미정 | **반영**: 4.7 표, `TestProps`·yml 수정 |
| 7 | CD M3, DC F5 | 🟡 | `step='RERANK'`가 남으면 v1으로 되돌릴 때 v1이 시작하지 못함 | **반영**: 다시 계산도 `RANK`로 기록, enum 값은 v1과 같게 |
| 8 | DC F2 | 🟡 | 강제 종료 뒤 재시도가 성공(0)으로 묻힘 | **반영**: 건너뜀은 종료 코드 2, SIGTERM이면 FAILED 기록 |
| 9 | DC F4 | 🟡 | 날짜가 다른 두 실행이 동시에 돎 | **반영**: 다른 날짜의 최근 RUNNING이 있으면 건너뜀 |
| 10 | DC F6 | 🟡 | 공개 레포가 수집에서 빠져 상세 화면이 멈춤 | **반영**: PUBLISHED는 항상 수집 |
| 11 | DC F7 | 🟡 | 판별 대기열과 수집 범위가 어긋나 방치 레포에 AI 한도를 씀 | **반영**: 판별 대상에도 60일 푸시 조건 |
| 12 | DC F8 | 🟡 | 규칙 오탐이 영구적이고 되살리는 방법이 없음 | **반영**: `course`·`interview` 제외, 되살리기 SQL·API 명시 |
| 13 | IR M1·M2·M3 | 🟡 | `rankBeforeAnalyze` 픽스처, 다시 계산 결과 검증, 동시성 테스트 | **반영**: `DailyJobTest` 10개 |
| 14 | IR M4, CD M2 | 🟡 | 규칙 필터 대상 쿼리, 호출 순서, `max-per-day: 0`일 때 규칙 필터 | **반영**: `findAllUnanalyzed`, AI 대상 선택 전에 커밋, 0이면 둘 다 건너뜀 |
| 15 | IR M5 | 🟡 | "v1에서 실패" 요건을 새 저장소에서 확인 불가 | **(b) 선택**: 9절 "실패 이유" 열로 갈음. v1에서 직접 실행하지는 않음 |
| 16 | IR M6·M7, CD M6·M7 | 🟡 | 단계별 의존성, 파일 단위 목록, 테스트 기반 재사용, 지울 파일 | **반영**: 4.7 표, 테스트 기반 이식, `insertBookmark` 헬퍼 |
| 17 | IR M8 | 🟡 | 배치를 실행할 계기가 없어짐 | **반영(부분)**: `daily-batch.yml`은 수동 실행(`workflow_dispatch`)만. 예약은 배포 결정(E3) 뒤 |
| 18 | IR M9, CD M4 | 🟡 | RERANK 실패, stale 종료 코드 | **반영**: 4.2 실패 표 |
| 19 | CD M5 | 🟡 | `:since` 타입(KST 자정 Instant) | **반영** |
| 20 | DC F12 | 🟢 | batch 프로필 Flyway | **반영**: 끔 |
| 21 | IR N2 | 🟢 | 웹도 외부 키 필수 | **반영**: 빈 기본값 |
| 22 | DC F10, CD N6 | 🟢 | 캐시 TTL 설명 부정확 | **반영**: 캐시별 TTL(순위 10분, 분야 1시간), 설명 수정 |
| 23 | DC F9 | 🟢 | 중복 방지 대안(advisory lock) 미검토 | **반영**: 5절에 기각 사유 |
| 24 | CD N1·N2·N3·N4·N5, IR N1·N3·N4, DC F11 | 🟢 | `#` 정규식 유지, bookmark 인덱스, 동점 처리, 로그 위치, 로그 문구, Flyway 동시 실행, 300자 테스트, 정밀도, 괄호 | **반영**(DB `now()` 사용으로 정밀도 문제 없음) |

남은 위험(받아들임): SIGKILL이나 정전처럼 기록할 틈이 없는 종료가 나면, 70분 동안 그날 재시도가 2로 끝난다. SIGTERM 경로와 `main`의 exit는 단위 테스트로 확인하지 않았고, 로컬 실행으로 확인한다.
