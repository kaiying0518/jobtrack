package com.example.jobtrack.service.ai;

import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

public interface QueryPlanningService {

    QueryPlan planFirstStep(Settings settings, String userMessage, String backgroundSummary);

    QueryPlan planSecondStep(Settings settings,
                             String userMessage,
                             String backgroundSummary,
                             QueryPlan firstPlan,
                             QueryResult firstResult);
}