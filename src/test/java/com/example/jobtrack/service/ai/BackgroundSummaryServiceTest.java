package com.example.jobtrack.service.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.repository.ApplicationRepository;

@SpringBootTest
@Transactional
class BackgroundSummaryServiceTest {

    @Autowired
    private BackgroundSummaryService backgroundSummaryService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();

        Application app = new Application();
        app.setCompanyName("A社");
        app.setPositionName("Backend Engineer");
        app.setCurrentStatus(ApplicationStatus.APPLIED);
        app.setAppliedDate(LocalDate.now().minusDays(3));
        app.setUpdatedAt(LocalDateTime.now().minusDays(2));

        applicationRepository.save(app);
    }

    @Test
    void buildSummary_shouldContainBasicInfo() {
        String summary = backgroundSummaryService.buildSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("応募件数"));
        assertTrue(summary.contains("A社"));
        assertTrue(summary.contains("Backend Engineer"));
    }
}