package com.example.jobtrack.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.ActivityActionType;
import com.example.jobtrack.entity.ActivityLog;
import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.repository.ActivityLogRepository;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public List<ActivityLog> findByApplicationId(Long applicationId) {
        return activityLogRepository.findByApplicationIdOrderByActionDateDesc(applicationId);
    }

    public List<ActivityLog> search(String keyword,
                                    ActivityActionType actionType,
                                    LocalDate fromDate,
                                    LocalDate toDate) {

        return activityLogRepository.findAll().stream()
                .filter(log -> keyword == null || keyword.isBlank()
                        || (log.getApplication() != null
                        && log.getApplication().getCompanyName() != null
                        && log.getApplication().getCompanyName().toLowerCase().contains(keyword.toLowerCase())))
                .filter(log -> actionType == null || log.getActionType() == actionType)
                .filter(log -> fromDate == null
                        || (log.getActionDate() != null
                        && !log.getActionDate().toLocalDate().isBefore(fromDate)))
                .filter(log -> toDate == null
                        || (log.getActionDate() != null
                        && !log.getActionDate().toLocalDate().isAfter(toDate)))
                .sorted((a, b) -> b.getActionDate().compareTo(a.getActionDate()))
                .toList();
    }

    public void createApplyLog(Application application) {
        ActivityLog log = new ActivityLog();
        log.setApplication(application);
        log.setActionType(ActivityActionType.APPLY);
        log.setNote("応募を追加しました");
        activityLogRepository.save(log);
    }

    public void createStatusUpdateLog(Application application, ApplicationStatus status) {
        ActivityLog log = new ActivityLog();
        log.setApplication(application);
        log.setActionType(ActivityActionType.STATUS_UPDATE);
        log.setNote(getStatusMessage(status));
        activityLogRepository.save(log);
    }

    public void createMemoUpdateLog(Application application, String memo) {
        ActivityLog log = new ActivityLog();
        log.setApplication(application);
        log.setActionType(ActivityActionType.MEMO_UPDATE);
        log.setNote("メモを更新しました");
        activityLogRepository.save(log);
    }

    public void createPortalUpdateLog(Application application) {
        ActivityLog log = new ActivityLog();
        log.setApplication(application);
        log.setActionType(ActivityActionType.PORTAL_UPDATE);
        log.setNote("採用ページ情報を更新しました");
        activityLogRepository.save(log);
    }

    public void deleteByApplicationId(Long applicationId) {
        activityLogRepository.deleteByApplicationId(applicationId);
    }

    private String getStatusMessage(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> "応募済みに更新しました";
            case WEB_TEST -> "Webテストに更新しました";
            case DOCUMENT_PASS -> "書類通過に更新しました";
            case INTERVIEW_1 -> "一次面接に更新しました";
            case INTERVIEW_2 -> "二次面接に更新しました";
            case FINAL_INTERVIEW -> "最終面接に更新しました";
            case OFFER -> "内定に更新しました";
            case REJECT -> "見送りに更新しました";
            case WITHDRAWN -> "辞退に更新しました";
        };
    }
}