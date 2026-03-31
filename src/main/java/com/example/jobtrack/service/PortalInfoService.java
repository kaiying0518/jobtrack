package com.example.jobtrack.service;

import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.PortalInfo;
import com.example.jobtrack.repository.PortalInfoRepository;

@Service
@Transactional
public class PortalInfoService {

    private final PortalInfoRepository portalInfoRepository;
    private final ActivityLogService activityLogService;

    public PortalInfoService(PortalInfoRepository portalInfoRepository,
                             ActivityLogService activityLogService) {
        this.portalInfoRepository = portalInfoRepository;
        this.activityLogService = activityLogService;
    }

    public List<PortalInfo> findByApplicationId(Long applicationId) {
        return portalInfoRepository.findByApplicationId(applicationId);
    }

    public PortalInfo save(Application application,
                           String portalName,
                           String portalUrl,
                           String loginId,
                           String loginMemo) {

        PortalInfo portalInfo = new PortalInfo();
        portalInfo.setApplication(application);
        portalInfo.setPortalName(blankToNull(portalName));
        portalInfo.setPortalUrl(blankToNull(portalUrl));
        portalInfo.setLoginId(blankToNull(loginId));
        portalInfo.setLoginMemo(blankToNull(loginMemo));

        PortalInfo saved = portalInfoRepository.save(portalInfo);
        activityLogService.createPortalUpdateLog(application);
        return saved;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public void deleteByApplicationId(Long applicationId) {
        portalInfoRepository.deleteByApplicationId(applicationId);
    }
}