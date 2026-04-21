package com.example.jobtrack.service.ai.dto;

import com.example.jobtrack.service.ai.QueryToolType;

public class QueryPlan {

    private boolean shouldQuery;
    private QueryToolType tool;
    private Integer days;
    private String reason;

    public QueryPlan() {
    }

    public QueryPlan(boolean shouldQuery, QueryToolType tool, Integer days, String reason) {
        this.shouldQuery = shouldQuery;
        this.tool = tool;
        this.days = days;
        this.reason = reason;
    }

    public boolean isShouldQuery() {
        return shouldQuery;
    }

    public void setShouldQuery(boolean shouldQuery) {
        this.shouldQuery = shouldQuery;
    }

    public QueryToolType getTool() {
        return tool;
    }

    public void setTool(QueryToolType tool) {
        this.tool = tool;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}