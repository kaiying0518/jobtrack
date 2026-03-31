package com.example.jobtrack.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.service.ApplicationService;
import com.example.jobtrack.service.PortalInfoService;

@Controller
public class PortalInfoController {

    private final PortalInfoService portalInfoService;
    private final ApplicationService applicationService;

    public PortalInfoController(PortalInfoService portalInfoService,
                                ApplicationService applicationService) {
        this.portalInfoService = portalInfoService;
        this.applicationService = applicationService;
    }

    @PostMapping("/applications/{applicationId}/portals")
    public String create(@PathVariable Long applicationId,
                         @RequestParam(required = false) String portalName,
                         @RequestParam(required = false) String portalUrl,
                         @RequestParam(required = false) String loginId,
                         @RequestParam(required = false) String loginMemo,
                         RedirectAttributes redirectAttributes) {

        boolean hasPortalInfo =
                hasText(portalName) ||
                hasText(portalUrl) ||
                hasText(loginId) ||
                hasText(loginMemo);

        if (hasPortalInfo) {
            Application application = applicationService.findById(applicationId);
            portalInfoService.save(application, portalName, portalUrl, loginId, loginMemo);
            redirectAttributes.addFlashAttribute("successMessage", "採用ページ情報を追加しました");
        }

        return "redirect:/applications/" + applicationId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}