package com.example.jobtrack.service.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.jobtrack.service.ai.dto.QueryResult;

class QueryResultFormatterTest {

    private final QueryResultFormatter formatter = new QueryResultFormatter();

    @Test
    void format_shouldBuildReadableText() {
        QueryResult result = new QueryResult(
                QueryToolType.COUNT_BY_STATUS,
                "現在のステータス分布を取得しました。",
                List.of(
                        Map.of("status", "APPLIED", "count", 3),
                        Map.of("status", "WEB_TEST", "count", 1)
                )
        );

        String formatted = formatter.format(result);

        assertNotNull(formatted);
        assertTrue(formatted.contains("リアルタイム検索結果"));
        assertTrue(formatted.contains("APPLIED"));
        assertTrue(formatted.contains("WEB_TEST"));
    }
}