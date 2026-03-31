package com.example.jobtrack.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.service.ApplicationService;
import com.example.jobtrack.service.SettingsService;

@Controller
public class SettingsController {

    private final SettingsService settingsService;
    private final ApplicationService applicationService;

    public SettingsController(SettingsService settingsService,
                              ApplicationService applicationService) {
        this.settingsService = settingsService;
        this.applicationService = applicationService;
    }

    @GetMapping("/settings")
    public String showSettings(Model model) {
        Settings settings = settingsService.getSettings();

        model.addAttribute("settings", settings);
        model.addAttribute("statuses", ApplicationStatus.values());

        return "settings/index";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam(required = false) String defaultResumeVersion,
                               @RequestParam(required = false) ApplicationStatus defaultStatus,
                               @RequestParam(required = false) Boolean useTodayAsDefault,
                               @RequestParam(required = false) String defaultChannel,
                               RedirectAttributes redirectAttributes) {

        settingsService.save(
                defaultResumeVersion != null ? defaultResumeVersion : "",
                defaultStatus != null ? defaultStatus : ApplicationStatus.APPLIED,
                useTodayAsDefault != null ? useTodayAsDefault : true,
                defaultChannel != null ? defaultChannel : ""
        );

        redirectAttributes.addFlashAttribute("successMessage", "設定を保存しました");
        return "redirect:/settings";
    }

    @GetMapping("/settings/export/csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        List<Application> applications = applicationService.findAllForCsv();

        String fileName = URLEncoder.encode("jobtrack-applications.csv", StandardCharsets.UTF_8);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        StringBuilder sb = new StringBuilder();

        sb.append('\uFEFF');
        sb.append("ID,会社名,職種,応募方法,今の状況,応募日,履歴書バージョン,地域,雇用形態,求人URL,メモ,次の予定,最終更新\n");

        for (Application app : applications) {
            sb.append(csv(app.getId())).append(",");
            sb.append(csv(app.getCompanyName())).append(",");
            sb.append(csv(app.getPositionName())).append(",");
            sb.append(csv(app.getChannel())).append(",");
            sb.append(csv(app.getCurrentStatus() != null ? app.getCurrentStatus().name() : "")).append(",");
            sb.append(csv(app.getAppliedDate())).append(",");
            sb.append(csv(app.getResumeVersion())).append(",");
            sb.append(csv(app.getRegion())).append(",");
            sb.append(csv(app.getEmploymentType())).append(",");
            sb.append(csv(app.getJobUrl())).append(",");
            sb.append(csv(app.getMemo())).append(",");
            sb.append(csv(app.getNextActionAt())).append(",");
            sb.append(csv(app.getUpdatedAt())).append("\n");
        }

        response.getWriter().write(sb.toString());
        response.getWriter().flush();
    }

    private String csv(Object value) {
        if (value == null) {
            return "\"\"";
        }

        String text = value.toString().replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}