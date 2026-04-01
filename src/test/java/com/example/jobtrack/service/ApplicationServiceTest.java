package com.example.jobtrack.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.repository.ApplicationRepository;

@SpringBootTest
class ApplicationServiceTest {

    @Autowired
    private ApplicationService applicationService;

    @MockitoBean
    private ApplicationRepository applicationRepository;

    @MockitoBean
    private ActivityLogService activityLogService;

    @MockitoBean
    private PortalInfoService portalInfoService;
    
    @Test
    void create_shouldSaveApplicationAndCreateApplyLog() {
        Application application = new Application();
        application.setCompanyName("OpenAI");
        application.setCurrentStatus(ApplicationStatus.APPLIED);

        Application savedApplication = new Application();
        savedApplication.setId(1L);
        savedApplication.setCompanyName("OpenAI");
        savedApplication.setCurrentStatus(ApplicationStatus.APPLIED);

        when(applicationRepository.save(application)).thenReturn(savedApplication);

        Application result = applicationService.create(application);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("OpenAI", result.getCompanyName());
        verify(applicationRepository, times(1)).save(application);
        verify(activityLogService, times(1)).createApplyLog(savedApplication);
    }

    @Test
    void updateStatus_shouldChangeStatusAndCreateStatusLog() {
        Application application = new Application();
        application.setId(1L);
        application.setCompanyName("OpenAI");
        application.setCurrentStatus(ApplicationStatus.APPLIED);

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Application result = applicationService.updateStatus(1L, ApplicationStatus.INTERVIEW_1);

        assertEquals(ApplicationStatus.INTERVIEW_1, result.getCurrentStatus());
        verify(applicationRepository, times(1)).findById(1L);
        verify(applicationRepository, times(1)).save(application);
        verify(activityLogService, times(1))
                .createStatusUpdateLog(application, ApplicationStatus.INTERVIEW_1);
    }

    @Test
    void updateStatus_shouldThrowExceptionWhenApplicationNotFound() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicationService.updateStatus(999L, ApplicationStatus.INTERVIEW_1)
        );

        assertEquals("Application not found: 999", exception.getMessage());
        verify(applicationRepository, times(1)).findById(999L);
    }

    @Test
    void deleteById_shouldDeleteLogsPortalInfosAndApplication() {
        Long applicationId = 1L;

        applicationService.deleteById(applicationId);

        verify(activityLogService, times(1)).deleteByApplicationId(applicationId);
        verify(portalInfoService, times(1)).deleteByApplicationId(applicationId);
        verify(applicationRepository, times(1)).deleteById(applicationId);
    }
    @Test
    void updateMemo_shouldUpdateMemoAndCreateMemoLog() {
        Application application = new Application();
        application.setId(1L);
        application.setMemo("old memo");

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        applicationService.updateMemo(1L, "new memo");

        assertEquals("new memo", application.getMemo());
        verify(applicationRepository, times(1)).findById(1L);
        verify(applicationRepository, times(1)).save(application);
        verify(activityLogService, times(1)).createMemoUpdateLog(application, "new memo");
    }

    @Test
    void findById_shouldThrowExceptionWhenApplicationNotFound() {
        when(applicationRepository.findById(123L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicationService.findById(123L)
        );

        assertEquals("Application not found: 123", exception.getMessage());
        verify(applicationRepository, times(1)).findById(123L);
    }

    @Test
    void shouldShowInactiveReminder_shouldReturnTrueWhenUpdatedAtIsOlderThan14Days() {
        Application application = new Application();
        application.setCurrentStatus(ApplicationStatus.APPLIED);
        application.setUpdatedAt(java.time.LocalDateTime.now().minusDays(15));

        boolean result = applicationService.shouldShowInactiveReminder(application);

        assertEquals(true, result);
    }

    @Test
    void shouldShowInactiveReminder_shouldReturnFalseWhenStatusIsOffer() {
        Application application = new Application();
        application.setCurrentStatus(ApplicationStatus.OFFER);
        application.setUpdatedAt(java.time.LocalDateTime.now().minusDays(30));

        boolean result = applicationService.shouldShowInactiveReminder(application);

        assertEquals(false, result);
    }

    @Test
    void shouldShowInactiveReminder_shouldReturnFalseWhenUpdatedAtIsNull() {
        Application application = new Application();
        application.setCurrentStatus(ApplicationStatus.APPLIED);
        application.setUpdatedAt(null);

        boolean result = applicationService.shouldShowInactiveReminder(application);

        assertEquals(false, result);
    }
    @Test
    void search_shouldReturnApplicationsByStatusAndDefaultSort() {
        Application app1 = new Application();
        app1.setCompanyName("OpenAI");
        app1.setCurrentStatus(ApplicationStatus.APPLIED);

        when(applicationRepository.findByCurrentStatusOrderByUpdatedAtDesc(ApplicationStatus.APPLIED))
                .thenReturn(java.util.List.of(app1));

        java.util.List<Application> result =
                applicationService.search(null, ApplicationStatus.APPLIED, null, "updatedAt");

        assertEquals(1, result.size());
        assertEquals("OpenAI", result.get(0).getCompanyName());
        verify(applicationRepository, times(1))
                .findByCurrentStatusOrderByUpdatedAtDesc(ApplicationStatus.APPLIED);
    }

    @Test
    void search_shouldReturnApplicationsByChannelAndCompanyNameSort() {
        Application app1 = new Application();
        app1.setCompanyName("Google");
        app1.setChannel("Wantedly");

        when(applicationRepository.findByChannelOrderByCompanyNameAsc("Wantedly"))
                .thenReturn(java.util.List.of(app1));

        java.util.List<Application> result =
                applicationService.search(null, null, "Wantedly", "companyName");

        assertEquals(1, result.size());
        assertEquals("Google", result.get(0).getCompanyName());
        verify(applicationRepository, times(1))
                .findByChannelOrderByCompanyNameAsc("Wantedly");
    }

    @Test
    void search_shouldFilterByKeyword() {
        Application app1 = new Application();
        app1.setCompanyName("OpenAI");
        app1.setPositionName("Backend Engineer");

        Application app2 = new Application();
        app2.setCompanyName("Microsoft");
        app2.setPositionName("Frontend Engineer");

        when(applicationRepository.findAllByOrderByUpdatedAtDesc())
                .thenReturn(java.util.List.of(app1, app2));

        java.util.List<Application> result =
                applicationService.search("open", null, null, "updatedAt");

        assertEquals(1, result.size());
        assertEquals("OpenAI", result.get(0).getCompanyName());
    }

    @Test
    void search_shouldFilterByPositionNameKeyword() {
        Application app1 = new Application();
        app1.setCompanyName("OpenAI");
        app1.setPositionName("Backend Engineer");

        Application app2 = new Application();
        app2.setCompanyName("Google");
        app2.setPositionName("Data Analyst");

        when(applicationRepository.findAllByOrderByUpdatedAtDesc())
                .thenReturn(java.util.List.of(app1, app2));

        java.util.List<Application> result =
                applicationService.search("backend", null, null, "updatedAt");

        assertEquals(1, result.size());
        assertEquals("Backend Engineer", result.get(0).getPositionName());
    }
}