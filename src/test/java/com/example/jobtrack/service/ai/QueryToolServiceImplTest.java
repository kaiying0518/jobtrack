package com.example.jobtrack.service.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.repository.ApplicationRepository;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

@SpringBootTest
@Transactional
class QueryToolServiceImplTest {

    @Autowired
    private QueryToolService queryToolService;

    @Autowired
    private ApplicationRepository applicationRepository;

    private LocalDateTime baseTime;
    private LocalDate baseDate;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();

        baseTime = LocalDateTime.now();
        baseDate = baseTime.toLocalDate();

        Application app1 = new Application();
        app1.setCompanyName("A社");
        app1.setPositionName("Backend Engineer");
        app1.setCurrentStatus(ApplicationStatus.APPLIED);
        app1.setAppliedDate(baseDate.minusDays(3));
        app1.setUpdatedAt(baseTime.minusDays(20));
        app1.setNextActionAt(baseTime.plusDays(2));

        Application app2 = new Application();
        app2.setCompanyName("B社");
        app2.setPositionName("Frontend Engineer");
        app2.setCurrentStatus(ApplicationStatus.WEB_TEST);
        app2.setAppliedDate(baseDate.minusDays(10));
        app2.setUpdatedAt(baseTime.minusDays(5));
        app2.setNextActionAt(baseTime.plusDays(10));

        Application app3 = new Application();
        app3.setCompanyName("C社");
        app3.setPositionName("Fullstack Engineer");
        app3.setCurrentStatus(ApplicationStatus.INTERVIEW_1);
        app3.setAppliedDate(baseDate.minusDays(1));
        app3.setUpdatedAt(baseTime.minusDays(30));
        app3.setNextActionAt(baseTime.plusDays(1));

        applicationRepository.save(app1);
        applicationRepository.save(app2);
        applicationRepository.save(app3);
        applicationRepository.findAll().forEach(app ->
        System.out.println(app.getCompanyName() + " -> " + app.getUpdatedAt())
    );
    }

    @Test
    void countByPeriod_shouldReturnCorrectCount() {
        QueryPlan plan = new QueryPlan(true, QueryToolType.COUNT_BY_PERIOD, 7, "test");

        QueryResult result = queryToolService.execute(plan);

        assertNotNull(result);
        assertEquals(QueryToolType.COUNT_BY_PERIOD, result.getTool());
        assertTrue(result.getSummary().contains("2"));
        assertEquals(1, result.getRows().size());
        assertEquals(2L, ((Number) result.getRows().get(0).get("count")).longValue());
    }

    @Test
    void staleApplications_shouldReturnOldApplications() {
        QueryPlan plan = new QueryPlan(true, QueryToolType.STALE_APPLICATIONS, 14, "test");

        QueryResult result = queryToolService.execute(plan);

        assertNotNull(result);
        assertEquals(QueryToolType.STALE_APPLICATIONS, result.getTool());
        assertEquals(2, result.getRows().size());
    }

    @Test
    void countByStatus_shouldReturnGroupedStatus() {
        QueryPlan plan = new QueryPlan(true, QueryToolType.COUNT_BY_STATUS, null, "test");

        QueryResult result = queryToolService.execute(plan);

        assertNotNull(result);
        assertEquals(QueryToolType.COUNT_BY_STATUS, result.getTool());
        assertEquals(3, result.getRows().size());

        Map<String, Long> statusCountMap = new HashMap<>();
        for (Map<String, Object> row : result.getRows()) {
            String status = (String) row.get("status");
            Long count = ((Number) row.get("count")).longValue();
            statusCountMap.put(status, count);
        }

        assertEquals(1L, statusCountMap.get("APPLIED"));
        assertEquals(1L, statusCountMap.get("WEB_TEST"));
        assertEquals(1L, statusCountMap.get("INTERVIEW_1"));
    }

    @Test
    void upcomingActions_shouldReturnUpcomingApplications() {
        QueryPlan plan = new QueryPlan(true, QueryToolType.UPCOMING_ACTIONS, 7, "test");

        QueryResult result = queryToolService.execute(plan);

        assertNotNull(result);
        assertEquals(QueryToolType.UPCOMING_ACTIONS, result.getTool());
        assertEquals(2, result.getRows().size());
    }
   
}