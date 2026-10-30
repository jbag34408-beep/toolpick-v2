package com.toolpick.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * batch 프로필 진입점: 앱이 뜨면 배치를 한 번 돌리고 결과를 종료 코드로 남긴다.
 * <p>
 * [CS] 여기서 System.exit를 부르지 않는다. 테스트가 이 빈을 띄워도 테스트 JVM이 죽지 않게,
 * 결과만 ExitCodeGenerator로 내놓고 실제 종료는 ToolpickApplication.main이 SpringApplication.exit로 한다.
 * 날짜는 기본이 오늘(KST)이고, 수동 재실행은 --date=2026-10-30 으로 정할 수 있다.
 */
@Slf4j
@Component
@Profile("batch")
@ConditionalOnProperty(name = "toolpick.batch.run-on-startup", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BatchJobRunner implements ApplicationRunner, ExitCodeGenerator {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyJob dailyJob;
    private volatile int exitCode = BatchOutcome.FAILED.exitCode();

    @Override
    public void run(ApplicationArguments args) {
        List<String> dates = args.getOptionValues("date");
        LocalDate date = dates == null || dates.isEmpty() ? LocalDate.now(KST) : LocalDate.parse(dates.getFirst());
        BatchOutcome outcome = dailyJob.run(date);
        exitCode = outcome.exitCode();
        log.info("배치 종료: date={}, 결과={}, 종료 코드={}", date, outcome, exitCode);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
