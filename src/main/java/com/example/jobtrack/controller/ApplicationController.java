package com.example.jobtrack.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.service.ActivityLogService;
import com.example.jobtrack.service.ApplicationService;
import com.example.jobtrack.service.PortalInfoService;
import com.example.jobtrack.service.SettingsService;

@Controller
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ActivityLogService activityLogService;
    private final PortalInfoService portalInfoService;
    private final SettingsService settingsService;

    public ApplicationController(ApplicationService applicationService,
                                 ActivityLogService activityLogService,
                                 PortalInfoService portalInfoService,
                                 SettingsService settingsService) {
        this.applicationService = applicationService;
        this.activityLogService = activityLogService;
        this.portalInfoService = portalInfoService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) ApplicationStatus status,
                       @RequestParam(required = false) String channel,
                       @RequestParam(required = false, defaultValue = "updatedAt") String sort,
                       Model model) {

        List<Application> applications = applicationService.search(keyword, status, channel, sort);

        Map<Long, Boolean> inactiveReminderMap = new LinkedHashMap<>();
        for (Application application : applications) {
            inactiveReminderMap.put(
                    application.getId(),
                    applicationService.shouldShowInactiveReminder(application)
            );
        }

        model.addAttribute("applications", applications);
        model.addAttribute("inactiveReminderMap", inactiveReminderMap);

        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status != null ? status.name() : "");
        model.addAttribute("channel", channel);
        model.addAttribute("sort", sort);

        return "applications/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Settings settings = settingsService.getSettings();

        Application application = new Application();
        application.setResumeVersion(settings.getDefaultResumeVersion());
        application.setChannel(settings.getDefaultChannel());

        if (settings.getUseTodayAsDefault() != null && settings.getUseTodayAsDefault()) {
            application.setAppliedDate(LocalDate.now());
        }

        if (settings.getDefaultStatus() != null) {
            application.setCurrentStatus(settings.getDefaultStatus());
        } else {
            application.setCurrentStatus(ApplicationStatus.APPLIED);
        }

        model.addAttribute("application", application);
        model.addAttribute("applicationId", null);
        model.addAttribute("statuses", ApplicationStatus.values());
        return "applications/form";
    }

    @PostMapping
    public String create(@ModelAttribute Application application,
                         @RequestParam(required = false) String portalName,
                         @RequestParam(required = false) String portalUrl,
                         @RequestParam(required = false) String loginId,
                         @RequestParam(required = false) String loginMemo,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        if (application.getCompanyName() == null || application.getCompanyName().isBlank()) {
            model.addAttribute("application", application);
            model.addAttribute("applicationId", null);
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("errorMessage", "会社名は必須です");
            return "applications/form";
        }

        Application saved = applicationService.create(application);

        boolean hasPortalInfo =
                (portalName != null && !portalName.isBlank()) ||
                (portalUrl != null && !portalUrl.isBlank()) ||
                (loginId != null && !loginId.isBlank()) ||
                (loginMemo != null && !loginMemo.isBlank());

        if (hasPortalInfo) {
            portalInfoService.save(saved, portalName, portalUrl, loginId, loginMemo);
        }

        redirectAttributes.addFlashAttribute("successMessage", "応募情報を追加しました");
        return "redirect:/applications/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Application application = applicationService.findById(id);


        model.addAttribute("jobApplication", application);
        model.addAttribute("applicationId", id);
        model.addAttribute("activityLogs", activityLogService.findByApplicationId(id));
        model.addAttribute("portalInfos", portalInfoService.findByApplicationId(id));
        model.addAttribute("inactiveReminder",
                applicationService.shouldShowInactiveReminder(application));

        return "applications/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Application application = applicationService.findById(id);
        model.addAttribute("application", application);
        model.addAttribute("applicationId", id);
        model.addAttribute("statuses", ApplicationStatus.values());
        return "applications/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute Application application,
                         @RequestParam(required = false) String portalName,
                         @RequestParam(required = false) String portalUrl,
                         @RequestParam(required = false) String loginId,
                         @RequestParam(required = false) String loginMemo,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        if (application.getCompanyName() == null || application.getCompanyName().isBlank()) {
            model.addAttribute("application", application);
            model.addAttribute("applicationId", id);
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("errorMessage", "会社名は必須です");
            return "applications/form";
        }

        applicationService.update(id, application);

        portalInfoService.deleteByApplicationId(id);

        boolean hasPortalInfo =
                (portalName != null && !portalName.isBlank()) ||
                (portalUrl != null && !portalUrl.isBlank()) ||
                (loginId != null && !loginId.isBlank()) ||
                (loginMemo != null && !loginMemo.isBlank());

        if (hasPortalInfo) {
            Application updatedApplication = applicationService.findById(id);
            portalInfoService.save(updatedApplication, portalName, portalUrl, loginId, loginMemo);
        }

        redirectAttributes.addFlashAttribute("successMessage", "応募情報を更新しました");
        return "redirect:/applications/" + id;
    }

    @GetMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam ApplicationStatus status,
                               RedirectAttributes redirectAttributes) {
        applicationService.updateStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "選考ステータスを更新しました");
        return "redirect:/applications/" + id;
    }

    @PostMapping("/{id}/memo")
    public String updateMemo(@PathVariable Long id,
                             @RequestParam String memo,
                             RedirectAttributes redirectAttributes) {
        applicationService.updateMemo(id, memo);
        redirectAttributes.addFlashAttribute("successMessage", "メモを更新しました");
        return "redirect:/applications/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        applicationService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "応募情報を削除しました");
        return "redirect:/applications";
    }
}