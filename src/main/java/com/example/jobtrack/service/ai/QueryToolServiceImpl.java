package com.example.jobtrack.service.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.repository.ApplicationRepository;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

@Service
public class QueryToolServiceImpl implements QueryToolService {

    private final ApplicationRepository applicationRepository;

    public QueryToolServiceImpl(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    public QueryResult execute(QueryPlan plan) {
        if (plan == null || !plan.isShouldQuery() || plan.getTool() == null || plan.getTool() == QueryToolType.NONE) {
            return null;
        }

        return switch (plan.getTool()) {
            case COUNT_BY_PERIOD -> countByPeriod(plan.getDays());
            case STALE_APPLICATIONS -> staleApplications(plan.getDays());
            case COUNT_BY_STATUS -> countByStatus();
            case UPCOMING_ACTIONS -> upcomingActions(plan.getDays());
            default -> null;
        };
    }

    private QueryResult countByPeriod(Integer days) {
        int targetDays = days != null ? days : 14;
        LocalDate fromDate = LocalDate.now().minusDays(targetDays);

        long count = applicationRepository.countByAppliedDateGreaterThanEqual(fromDate);

        QueryResult result = new QueryResult();
        result.setTool(QueryToolType.COUNT_BY_PERIOD);
        result.setSummary("最近 " + targetDays + " 日間の応募件数は " + count + " 件です。");
        result.setRows(List.of(Map.of(
                "days", targetDays,
                "count", count
        )));
        return result;
    }

    private QueryResult staleApplications(Integer days) {
        int targetDays = days != null ? days : 14;
        LocalDateTime before = LocalDateTime.now().minusDays(targetDays);

        List<Application> applications = applicationRepository.findByUpdatedAtBeforeOrderByUpdatedAtAsc(before);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Application app : applications) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyName", nullToDash(app.getCompanyName()));
            row.put("positionName", nullToDash(app.getPositionName()));
            row.put("status", app.getCurrentStatus() != null ? app.getCurrentStatus().name() : "-");
            row.put("updatedAt", app.getUpdatedAt() != null ? app.getUpdatedAt().toString() : "-");
            rows.add(row);
        }

        QueryResult result = new QueryResult();
        result.setTool(QueryToolType.STALE_APPLICATIONS);
        result.setSummary(targetDays + "日以上更新されていない応募は " + applications.size() + " 件です。");
        result.setRows(rows);
        return result;
    }

    private QueryResult countByStatus() {
        List<Object[]> raw = applicationRepository.countGroupByStatus();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] item : raw) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", item[0] != null ? item[0].toString() : "-");
            row.put("count", item[1]);
            rows.add(row);
        }

        QueryResult result = new QueryResult();
        result.setTool(QueryToolType.COUNT_BY_STATUS);
        result.setSummary("現在のステータス分布を取得しました。");
        result.setRows(rows);
        return result;
    }

    private QueryResult upcomingActions(Integer days) {
        int targetDays = days != null ? days : 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now.plusDays(targetDays);

        List<Application> applications = applicationRepository.findByNextActionAtBetweenOrderByNextActionAtAsc(now, end);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Application app : applications) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyName", nullToDash(app.getCompanyName()));
            row.put("positionName", nullToDash(app.getPositionName()));
            row.put("status", app.getCurrentStatus() != null ? app.getCurrentStatus().name() : "-");
            row.put("nextActionAt", app.getNextActionAt() != null ? app.getNextActionAt().toString() : "-");
            rows.add(row);
        }

        QueryResult result = new QueryResult();
        result.setTool(QueryToolType.UPCOMING_ACTIONS);
        result.setSummary("今後 " + targetDays + " 日以内の予定がある応募は " + applications.size() + " 件です。");
        result.setRows(rows);
        return result;
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}