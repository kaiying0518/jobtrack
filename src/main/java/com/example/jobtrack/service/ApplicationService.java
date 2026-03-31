package com.example.jobtrack.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.repository.ApplicationRepository;

@Service
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ActivityLogService activityLogService;
    private final PortalInfoService portalInfoService;

    public ApplicationService(ApplicationRepository applicationRepository,
                              ActivityLogService activityLogService,
                              PortalInfoService portalInfoService) {
        this.applicationRepository = applicationRepository;
        this.activityLogService = activityLogService;
        this.portalInfoService = portalInfoService;
    }

    public List<Application> findAll() {
        return applicationRepository.findAll();
    }
    public List<Application> findAllForCsv() {
        return applicationRepository.findAll();
    }
    public Application findById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
    }

    public Application create(Application application) {
        Application saved = applicationRepository.save(application);
        activityLogService.createApplyLog(saved);
        return saved;
    }

    public Application updateStatus(Long id, ApplicationStatus newStatus) {
        Application application = findById(id);
        application.setCurrentStatus(newStatus);
        Application saved = applicationRepository.save(application);
        activityLogService.createStatusUpdateLog(saved, newStatus);
        return saved;
    }

    public void updateMemo(Long id, String memo) {
        Application application = findById(id);
        application.setMemo(memo);
        applicationRepository.save(application);
        activityLogService.createMemoUpdateLog(application, memo);
    }

    public boolean shouldShowInactiveReminder(Application application) {
        if (application.getCurrentStatus() == ApplicationStatus.OFFER
                || application.getCurrentStatus() == ApplicationStatus.REJECT
                || application.getCurrentStatus() == ApplicationStatus.WITHDRAWN) {
            return false;
        }

        if (application.getUpdatedAt() == null) {
            return false;
        }

        return application.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(14));
    }

    public void deleteById(Long id) {
        activityLogService.deleteByApplicationId(id);
        portalInfoService.deleteByApplicationId(id);
        applicationRepository.deleteById(id);
    }
    public Application update(Long id, Application formApplication) {
        Application application = findById(id);

        application.setCompanyName(formApplication.getCompanyName());
        application.setPositionName(formApplication.getPositionName());
        application.setChannel(formApplication.getChannel());
        application.setCurrentStatus(formApplication.getCurrentStatus());
        application.setAppliedDate(formApplication.getAppliedDate());
        application.setResumeVersion(formApplication.getResumeVersion());
        application.setRegion(formApplication.getRegion());
        application.setEmploymentType(formApplication.getEmploymentType());
        application.setJobUrl(formApplication.getJobUrl());
        application.setMemo(formApplication.getMemo());
        application.setNextActionAt(formApplication.getNextActionAt());

        return applicationRepository.save(application);
    }
    public List<Application> search(String keyword, ApplicationStatus status, String channel, String sort) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = status != null;
        boolean hasChannel = channel != null && !channel.isBlank();

        List<Application> applications;

        if (hasStatus && hasChannel) {
            applications = switch (sort) {
                case "appliedDate" -> applicationRepository.findByCurrentStatusAndChannelOrderByAppliedDateDesc(status, channel);
                case "companyName" -> applicationRepository.findByCurrentStatusAndChannelOrderByCompanyNameAsc(status, channel);
                default -> applicationRepository.findByCurrentStatusAndChannelOrderByUpdatedAtDesc(status, channel);
            };
        } else if (hasStatus) {
            applications = switch (sort) {
                case "appliedDate" -> applicationRepository.findByCurrentStatusOrderByAppliedDateDesc(status);
                case "companyName" -> applicationRepository.findByCurrentStatusOrderByCompanyNameAsc(status);
                default -> applicationRepository.findByCurrentStatusOrderByUpdatedAtDesc(status);
            };
        } else if (hasChannel) {
            applications = switch (sort) {
                case "appliedDate" -> applicationRepository.findByChannelOrderByAppliedDateDesc(channel);
                case "companyName" -> applicationRepository.findByChannelOrderByCompanyNameAsc(channel);
                default -> applicationRepository.findByChannelOrderByUpdatedAtDesc(channel);
            };
        } else {
            applications = switch (sort) {
                case "appliedDate" -> applicationRepository.findAllByOrderByAppliedDateDesc();
                case "companyName" -> applicationRepository.findAllByOrderByCompanyNameAsc();
                default -> applicationRepository.findAllByOrderByUpdatedAtDesc();
            };
        }

        if (hasKeyword) {
            String lowerKeyword = keyword.toLowerCase();
            applications = applications.stream()
                    .filter(app ->
                            (app.getCompanyName() != null && app.getCompanyName().toLowerCase().contains(lowerKeyword))
                            || (app.getPositionName() != null && app.getPositionName().toLowerCase().contains(lowerKeyword)))
                    .toList();
        }

        return applications;
    }
}