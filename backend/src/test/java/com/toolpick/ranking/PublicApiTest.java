package com.toolpick.ranking;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toolpick.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공개 API(순위·상세·스타·카테고리) 응답 형식과 404·400 처리를 MockMvc로 검증한다.
 * [CS] API 계약(contract) 테스트: 프론트가 기대하는 필드 이름과 값(rank, fullName, starGain...)이 바뀌면 여기서 먼저 깨진다.
 */
class PublicApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    RankingCalculator calculator;

    @Test
    void emptyRankingsBeforeFirstBatch() throws Exception {
        mvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].slug").value("ai-agent"))
                .andExpect(jsonPath("$[0].date").value(nullValue()))
                .andExpect(jsonPath("$[0].items", hasSize(0)));
    }

    @Test
    void rankingsDetailAndStars() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        long id = insertRepo("cli/tool", today.minusDays(1) + "T00:00:00Z");
        insertAnalysis(id, "dev-tools", "PUBLISHED", true);
        insertSnapshot(id, today.minusDays(7).toString(), 100);
        insertSnapshot(id, today.toString(), 150);
        calculator.calculate(today);

        mvc.perform(get("/api/rankings/dev-tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].fullName").value("cli/tool"))
                .andExpect(jsonPath("$.items[0].summaryKo").value("요약"))
                .andExpect(jsonPath("$.items[0].starGain").value(50));
        mvc.perform(get("/api/repos/cli/tool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars").value(150))
                .andExpect(jsonPath("$.categorySlug").value("dev-tools"))
                .andExpect(jsonPath("$.installCmd").value("npm i x"));
        mvc.perform(get("/api/repos/cli/tool/stars?days=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    void notFoundAndBadRequest() throws Exception {
        long pending = insertRepo("pending/tool", "2026-10-20T00:00:00Z");
        insertAnalysis(pending, "dev-tools", "PENDING_REVIEW", true);

        mvc.perform(get("/api/rankings/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/repos/pending/tool")).andExpect(status().isNotFound());
        mvc.perform(get("/api/repos/none/tool")).andExpect(status().isNotFound());
        mvc.perform(get("/api/rankings?date=not-a-date")).andExpect(status().isBadRequest());
    }
}
