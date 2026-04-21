package com.example.jobtrack.service.ai;

import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

public interface QueryToolService {
    QueryResult execute(QueryPlan plan);
}