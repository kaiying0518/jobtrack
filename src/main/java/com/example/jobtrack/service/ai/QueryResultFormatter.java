package com.example.jobtrack.service.ai;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.jobtrack.service.ai.dto.QueryResult;

@Service
public class QueryResultFormatter {

    public String format(QueryResult result) {
        if (result == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【リアルタイム検索結果】\n");
        sb.append(result.getSummary()).append("\n");

        List<Map<String, Object>> rows = result.getRows();
        if (rows != null && !rows.isEmpty()) {
            for (Map<String, Object> row : rows) {
                sb.append("- ");
                boolean first = true;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!first) {
                        sb.append(" / ");
                    }
                    sb.append(entry.getKey()).append(": ").append(entry.getValue());
                    first = false;
                }
                sb.append("\n");
            }
        }

        sb.append("""
                
                上記は今回の質問に対する最新の検索結果です。
                件数・一覧・分布については、この検索結果を優先して回答してください。
                """);

        return sb.toString();
    }
}