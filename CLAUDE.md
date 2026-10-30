# 툴픽(ToolPick) v2 — Claude Code 설정

공통 규칙은 AGENTS.md에 있다. Claude Code는 이 파일을 읽을 때 아래 줄로 AGENTS.md도 함께 불러온다.

@AGENTS.md

## Claude Code 전용

- 큰 변경(AGENTS.md 3장 기준)은 EGM 플러그인 순서로 진행한다:
  `/egm:egm-brainstorm`(코드 금지) → `/egm:egm-draft`(설계 문서를 `docs/design/NN-이름.md`에 저장) → `/egm:egm-review` → `/egm:egm-resolve` → 사람 승인 → 구현.
- 설계 리뷰(`/egm:egm-review`) 중에는 Ponytail을 끈다(`/ponytail off`). 구현을 시작할 때 다시 켠다.
- 구현이 끝나면 `/ponytail-review`로 바뀐 코드를 검토한 뒤 테스트를 돌린다.
- 코드를 새로 만들거나 고칠 때는 부분 수정 안내 대신 파일 전체를 보여 준다.
- 답변과 문서는 한국어로 쓴다.
