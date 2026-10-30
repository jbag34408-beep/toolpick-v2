package com.toolpick.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * upsert 멱등성 검증: 같은 날 두 번 저장해도 한 행만 남고 값은 마지막 것으로 바뀌는지.
 * [CS] 네이티브 SQL(ON CONFLICT)은 컴파일러가 검사해 주지 않으므로, 실제 DB에서 돌려 보는 것이 유일한 검증 방법이다.
 * TransactionTemplate으로 호출마다 트랜잭션을 열어 실제 배치처럼 각각 커밋되게 한다(@Modifying 쿼리는 트랜잭션이 필요).
 */
class StarSnapshotRepositoryTest extends IntegrationTest {

    @Autowired
    StarSnapshotRepository snapshotRepository;

    @Autowired
    TransactionTemplate tx;

    @Test
    void upsertTwiceKeepsOneRow() {
        long repoId = insertRepo("a/tool", "2026-10-20T00:00:00Z");
        LocalDate date = LocalDate.of(2026, 10, 26);

        tx.executeWithoutResult(s -> snapshotRepository.upsert(repoId, date, 100));
        tx.executeWithoutResult(s -> snapshotRepository.upsert(repoId, date, 150));

        assertThat(jdbc.queryForList("SELECT stars FROM star_snapshot WHERE repo_id = ?", Integer.class, repoId))
                .containsExactly(150);
    }
}
