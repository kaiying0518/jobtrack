package com.example.jobtrack.service.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

import tools.jackson.databind.ObjectMapper;

@Service
public class QueryPlanningServiceImpl implements QueryPlanningService {

    private final Map<AiProviderType, AiClient> clientMap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryPlanningServiceImpl(List<AiClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(AiClient::supportedProvider, Function.identity()));
    }

    @Override
    public QueryPlan planFirstStep(Settings settings, String userMessage, String backgroundSummary) {
        try {
            String prompt = """
                    あなたは JobTrack の検索プランナーです。
                    ユーザーの質問に対して、まず検索が必要かどうかを判断してください。

                    利用可能なツール:
                    - NONE: 検索不要
                    - COUNT_BY_PERIOD: 最近N日間の応募件数
                    - STALE_APPLICATIONS: N日以上更新されていない応募
                    - COUNT_BY_STATUS: 現在のステータス分布
                    - UPCOMING_ACTIONS: 今後N日以内の nextActionAt

                    判断ルール:
                    - 件数、一覧、分布、期間指定、優先対象の判断に必要な場合は shouldQuery=true
                    - 一般的な相談、感想、励まし、方向性の相談は shouldQuery=false
                    - days が必要で読み取れない場合は 14 を使ってください

                    背景概要:
                    %s

                    ユーザー質問:
                    %s

                    JSONのみ返してください:
                    {
                      "shouldQuery": true,
                      "tool": "COUNT_BY_PERIOD",
                      "days": 14,
                      "reason": "最近2週間の応募件数という精確な数値が必要なため"
                    }
                    """.formatted(
                    safe(backgroundSummary),
                    safe(userMessage)
            );

            String raw = getClient(settings).generateText(settings, "", prompt);
            return parsePlan(raw);
        } catch (Exception e) {
            return new QueryPlan(false, QueryToolType.NONE, null, "planner fallback");
        }
    }

    @Override
    public QueryPlan planSecondStep(Settings settings,
                                    String userMessage,
                                    String backgroundSummary,
                                    QueryPlan firstPlan,
                                    QueryResult firstResult) {
        try {
            String prompt = """
                    あなたは JobTrack の二段階検索プランナーです。
                    すでに1回目の検索結果があります。
                    この結果だけで最終回答に十分か、もう1回だけ追加検索が必要かを判断してください。

                    利用可能なツール:
                    - NONE: 追加検索不要
                    - STALE_APPLICATIONS: N日以上更新されていない応募
                    - COUNT_BY_STATUS: 現在のステータス分布
                    - UPCOMING_ACTIONS: 今後N日以内の nextActionAt

                    ルール:
                    - 追加検索が不要なら shouldQuery=false
                    - 追加検索が必要でも1回だけ
                    - 同じ意味の無駄な再検索はしない
                    - days が必要で読み取れない場合は 14、UPCOMING_ACTIONS は 7 を優先

                    背景概要:
                    %s

                    ユーザー質問:
                    %s

                    1回目プラン:
                    tool=%s, days=%s, reason=%s

                    1回目結果:
                    %s

                    JSONのみ返してください:
                    {
                      "shouldQuery": false,
                      "tool": "NONE",
                      "days": null,
                      "reason": "1回目結果で十分"
                    }
                    """.formatted(
                    safe(backgroundSummary),
                    safe(userMessage),
                    firstPlan != null ? firstPlan.getTool() : "NONE",
                    firstPlan != null ? firstPlan.getDays() : null,
                    firstPlan != null ? safe(firstPlan.getReason()) : "",
                    firstResult != null ? safe(firstResult.getSummary()) : ""
            );

            String raw = getClient(settings).generateText(settings, "", prompt);
            QueryPlan plan = parsePlan(raw);

            if (plan.getTool() == firstPlan.getTool()) {
                return new QueryPlan(false, QueryToolType.NONE, null, "same tool not needed");
            }

            return plan;
        } catch (Exception e) {
            return new QueryPlan(false, QueryToolType.NONE, null, "second planner fallback");
        }
    }

    private AiClient getClient(Settings settings) {
        AiClient client = clientMap.get(settings.getAiProvider());
        if (client == null) {
            throw new IllegalStateException("対応していないAIプロバイダです。");
        }
        return client;
    }

    private QueryPlan parsePlan(String raw) {
        try {
            String json = extractJson(raw);
            QueryPlan plan = objectMapper.readValue(json, QueryPlan.class);

            if (plan.getTool() == null) {
                plan.setTool(QueryToolType.NONE);
            }

            return plan;
        } catch (Exception e) {
            return new QueryPlan(false, QueryToolType.NONE, null, "parse fallback");
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }

        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return "{}";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}