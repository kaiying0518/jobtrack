package com.example.jobtrack.service.ai.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.jobtrack.service.ai.QueryToolType;

public class QueryResult {

    private QueryToolType tool;
    private String summary;
    private List<Map<String, Object>> rows = new ArrayList<>();

    public QueryResult() {
    }

    public QueryResult(QueryToolType tool, String summary, List<Map<String, Object>> rows) {
        this.tool = tool;
        this.summary = summary;
        this.rows = rows;
    }

    public QueryToolType getTool() {
        return tool;
    }

    public void setTool(QueryToolType tool) {
        this.tool = tool;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }
}