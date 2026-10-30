package com.toolpick.batch;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * batch_run 기록을 SQL 한 문장씩으로 다룬다 (docs/design/v2-00-backend.md 4.2).
 * <p>
 * [CS] v1은 한 서버 안의 synchronized로 동시 시작을 막았다. v2는 배치가 별도 프로세스라(여러 개가 동시에 뜰 수 있음)
 * 판단과 기록을 각각 조건부 SQL 한 문장으로 끝내 DB가 원자성을 보장하게 한다.
 * <ul>
 *   <li>새 기록: INSERT … ON CONFLICT DO NOTHING — 같은 날짜를 둘이 동시에 넣어도 하나만 성공</li>
 *   <li>다시 시작: UPDATE … WHERE (FAILED 또는 오래된 RUNNING) — 둘이 동시에 해도 하나만 1행을 바꾼다</li>
 *   <li>단계·결과 기록: WHERE started_at = 내가 시작한 시각 — 다른 실행이 이어받았으면 0행이라 멈춘다</li>
 * </ul>
 * 시각은 JVM이 아니라 DB now()를 쓴다. 프로세스마다 시계가 달라도 판단이 같다.
 */
@Component
public class BatchRunStore {

    private static final int MAX_ERROR_LENGTH = 2000;

    /** 내가 맡은 실행. startedAt은 이 실행의 표식(다른 실행이 다시 시작하면 바뀐다) */
    public record Claim(long id, OffsetDateTime startedAt) {
    }

    public enum Skip {
        /** 오늘 이미 성공 */
        ALREADY_DONE,
        /** 다른 실행이 도는 중 (같은 날짜 또는 다른 날짜) */
        IN_PROGRESS
    }

    /** 맡았으면 claim, 아니면 skip이 채워진다 */
    public record Decision(Claim claim, Skip skip) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public BatchRunStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Decision claim(LocalDate date, Duration staleAfter) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("date", date)
                .addValue("staleSecs", (double) staleAfter.toSeconds());

        // v1은 실행기 스레드 1개가 날짜와 상관없이 배치를 하나씩만 돌렸다. 그 성질을 유지한다(자정 전후 수동 실행 겹침 방지)
        Integer otherActive = jdbc.queryForObject("""
                SELECT count(*) FROM batch_run
                WHERE run_date <> :date AND status = 'RUNNING'
                  AND started_at > now() - make_interval(secs => :staleSecs)""", p, Integer.class);
        if (otherActive != null && otherActive > 0) {
            return new Decision(null, Skip.IN_PROGRESS);
        }

        List<Claim> inserted = jdbc.query("""
                INSERT INTO batch_run (run_date, status, step, started_at)
                VALUES (:date, 'RUNNING', 'DISCOVER', now())
                ON CONFLICT (run_date) DO NOTHING
                RETURNING id, started_at""", p, BatchRunStore::toClaim);
        if (!inserted.isEmpty()) {
            return new Decision(inserted.getFirst(), null);
        }

        List<Claim> restarted = jdbc.query("""
                UPDATE batch_run
                SET status = 'RUNNING', step = 'DISCOVER', started_at = now(), finished_at = NULL, error = NULL
                WHERE run_date = :date
                  AND (status = 'FAILED'
                       OR (status = 'RUNNING' AND started_at <= now() - make_interval(secs => :staleSecs)))
                RETURNING id, started_at""", p, BatchRunStore::toClaim);
        if (!restarted.isEmpty()) {
            return new Decision(restarted.getFirst(), null);
        }

        String status = jdbc.queryForObject("SELECT status FROM batch_run WHERE run_date = :date", p, String.class);
        return new Decision(null, "SUCCESS".equals(status) ? Skip.ALREADY_DONE : Skip.IN_PROGRESS);
    }

    /** @return false면 다른 실행이 이 기록을 이어받았다 */
    public boolean moveTo(Claim claim, BatchStep step) {
        return jdbc.update("""
                UPDATE batch_run SET step = :step
                WHERE id = :id AND started_at = :startedAt AND status = 'RUNNING'""",
                ownership(claim).addValue("step", step.name())) == 1;
    }

    public boolean succeed(Claim claim) {
        return jdbc.update("""
                UPDATE batch_run SET status = 'SUCCESS', step = 'DONE', finished_at = now()
                WHERE id = :id AND started_at = :startedAt AND status = 'RUNNING'""", ownership(claim)) == 1;
    }

    public boolean fail(Claim claim, String message) {
        String msg = message == null ? "알 수 없는 오류" : message;
        return jdbc.update("""
                UPDATE batch_run SET status = 'FAILED', finished_at = now(), error = :error
                WHERE id = :id AND started_at = :startedAt AND status = 'RUNNING'""",
                ownership(claim).addValue("error", msg.length() > MAX_ERROR_LENGTH ? msg.substring(0, MAX_ERROR_LENGTH) : msg)) == 1;
    }

    private static MapSqlParameterSource ownership(Claim claim) {
        return new MapSqlParameterSource().addValue("id", claim.id()).addValue("startedAt", claim.startedAt());
    }

    private static Claim toClaim(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Claim(rs.getLong("id"), rs.getObject("started_at", OffsetDateTime.class));
    }
}
