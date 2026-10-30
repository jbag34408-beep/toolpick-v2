package com.toolpick.batch;

/**
 * batch_run.step에 기록하는 단계 이름.
 * [CS] v2 실행 순서는 DISCOVER → COLLECT → RANK → ANALYZE → (RANK 다시) → DONE 이지만(DailyJob),
 * 값 목록은 v1과 같게 둔다. v1으로 되돌렸을 때 v1의 enum이 모르는 값(예: RERANK)이 DB에 남아 있으면
 * v1이 그 행을 읽다가 시작조차 못 하기 때문이다. 판별 뒤 다시 계산하는 단계도 RANK로 기록한다.
 */
public enum BatchStep {
    DISCOVER, COLLECT, ANALYZE, RANK, DONE
}
