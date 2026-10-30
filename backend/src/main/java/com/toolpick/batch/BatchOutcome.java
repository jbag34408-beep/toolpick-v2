package com.toolpick.batch;

/**
 * 배치 한 번의 결과와 프로세스 종료 코드.
 * [CS] 종료 코드로 실행 환경(Cloud Run 잡, GitHub Actions)이 성공·실패를 판단하고 알림을 보낸다.
 * "다른 실행이 도는 중이라 건너뜀"은 성공(0)과 구분해 2로 남긴다. 강제 종료 뒤 재시도가 조용히 성공으로 묻히지 않게.
 */
public enum BatchOutcome {
    COMPLETED(0),
    ALREADY_DONE(0),
    FAILED(1),
    SKIPPED_IN_PROGRESS(2);

    private final int exitCode;

    BatchOutcome(int exitCode) {
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
