package com.example.jobtrack.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.jobtrack.entity.AiProviderType;
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
        model.addAttribute("aiProviders", AiProviderType.values());
        model.addAttribute("hasAiApiKey",
                settings.getAiApiKey() != null && !settings.getAiApiKey().isBlank());
        model.addAttribute("currentAiProvider", settings.getAiProvider() != null ? settings.getAiProvider().name() : "");

        return "settings/index";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam(required = false) String defaultResumeVersion,
                               @RequestParam(required = false) ApplicationStatus defaultStatus,
                               @RequestParam(required = false) Boolean useTodayAsDefault,
                               @RequestParam(required = false) String defaultChannel,
                               @RequestParam(required = false) Boolean aiEnabled,
                               @RequestParam(required = false) AiProviderType aiProvider,
                               @RequestParam(required = false) String aiApiKey,
                               @RequestParam(required = false) String aiModel,
                               @RequestParam(required = false) String aiSystemPrompt,
                               @RequestParam(required = false) String aiUserPromptTemplate,
                               @RequestParam(required = false) Integer aiMaxTokens,
                               @RequestParam(required = false) Double aiTemperature,
                               @RequestParam(required = false) Boolean useFullChatHistory,
                               RedirectAttributes redirectAttributes) {

        Settings currentSettings = settingsService.getSettings();

        boolean resolvedAiEnabled = aiEnabled != null ? aiEnabled : false;
        AiProviderType resolvedAiProvider = aiProvider != null ? aiProvider : AiProviderType.OPENAI;

        boolean providerChanged = currentSettings.getAiProvider() != resolvedAiProvider;
        boolean hasSavedApiKey = currentSettings.getAiApiKey() != null && !currentSettings.getAiApiKey().isBlank();

        String resolvedAiApiKey = aiApiKey;

        if (resolvedAiEnabled) {
            if (providerChanged) {
                if (resolvedAiApiKey == null || resolvedAiApiKey.isBlank()) {
                    redirectAttributes.addFlashAttribute("errorMessage", "AI Provider を変更した場合は、API Key を再入力してください。");
                    return "redirect:/settings";
                }
            } else {
                if (resolvedAiApiKey == null || resolvedAiApiKey.isBlank()) {
                    if (hasSavedApiKey) {
                        resolvedAiApiKey = currentSettings.getAiApiKey();
                    } else {
                        redirectAttributes.addFlashAttribute("errorMessage", "AI連携を使う場合は、API Key を入力してください。");
                        return "redirect:/settings";
                    }
                }
            }
        } else {
            if (resolvedAiApiKey == null || resolvedAiApiKey.isBlank()) {
                resolvedAiApiKey = currentSettings.getAiApiKey();
            }
        }

        settingsService.save(
                defaultResumeVersion != null ? defaultResumeVersion : "",
                defaultStatus != null ? defaultStatus : ApplicationStatus.APPLIED,
                useTodayAsDefault != null ? useTodayAsDefault : true,
                defaultChannel != null ? defaultChannel : "",
                resolvedAiEnabled,
                resolvedAiProvider,
                resolvedAiApiKey,
                aiModel != null ? aiModel : "",
                aiSystemPrompt != null ? aiSystemPrompt : "",
                aiUserPromptTemplate != null ? aiUserPromptTemplate : "",
                aiMaxTokens,
                aiTemperature,
                useFullChatHistory != null ? useFullChatHistory : false
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
        sb.append("会社名,職種,応募方法,今の状況,応募日,次の予定\n");

        for (Application app : applications) {
            sb.append(csv(app.getCompanyName())).append(",");
            sb.append(csv(app.getPositionName())).append(",");
            sb.append(csv(formatChannel(app.getChannel()))).append(",");
            sb.append(csv(formatStatus(app.getCurrentStatus()))).append(",");
            sb.append(csv(app.getAppliedDate())).append(",");
            sb.append(csv(formatDateTime(app.getNextActionAt()))).append("\n");
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

    private String formatChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "";
        }

        return switch (channel) {
            case "DIRECT" -> "直接応募";
            case "JOB_SITE" -> "求人サイト";
            case "AGENT" -> "エージェント";
            case "SCOUT" -> "スカウト";
            case "OTHER" -> "その他";
            default -> channel;
        };
    }

    private String formatStatus(ApplicationStatus status) {
        if (status == null) {
            return "";
        }

        return switch (status) {
            case APPLIED -> "応募済み";
            case WEB_TEST -> "WEBテスト";
            case DOCUMENT_PASS -> "書類通過";
            case INTERVIEW_1 -> "一次面接";
            case INTERVIEW_2 -> "二次面接";
            case FINAL_INTERVIEW -> "最終面接";
            case OFFER -> "内定";
            case REJECT -> "見送り";
            case WITHDRAWN -> "辞退";
        };
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}

   