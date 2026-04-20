package com.example.jobtrack.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.ApplicationRepository;
import com.example.jobtrack.service.ai.AiClient;

@Service
public class ReflectionAiService {

    private final SettingsService settingsService;
    private final ApplicationRepository applicationRepository;
    private final Map<AiProviderType, AiClient> clientMap;

    public ReflectionAiService(SettingsService settingsService,
                               ApplicationRepository applicationRepository,
                               List<AiClient> clients) {
        this.settingsService = settingsService;
        this.applicationRepository = applicationRepository;
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(AiClient::supportedProvider, Function.identity()));
    }

    public String generateSummary() {
        Settings settings = settingsService.getSettings();

        validateSettings(settings);

        AiClient client = clientMap.get(settings.getAiProvider());
        if (client == null) {
            throw new IllegalStateException("Unsupported AI provider: " + settings.getAiProvider());
        }

        String systemPrompt = buildSystemPrompt(settings);
        String userPrompt = buildUserPrompt(settings);

        return client.generateText(settings, systemPrompt, userPrompt);
    }

    private void validateSettings(Settings settings) {
        if (settings == null) {
            throw new IllegalStateException("Settings not found");
        }
        if (!Boolean.TRUE.equals(settings.getAiEnabled())) {
            throw new IllegalStateException("AI is disabled");
        }
        if (settings.getAiProvider() == null) {
            throw new IllegalStateException("AI provider is empty");
        }
        if (settings.getAiApiKey() == null || settings.getAiApiKey().isBlank()) {
            throw new IllegalStateException("AI API key is empty");
        }
    }

    private String buildSystemPrompt(Settings settings) {
        if (settings.getAiSystemPrompt() != null && !settings.getAiSystemPrompt().isBlank()) {
            return settings.getAiSystemPrompt();
        }

        return """
                あなたは JobTrack の就職活動支援AIです。
                ユーザーの応募状況を短く整理し、全体傾向・注意点・次に取るとよい行動を簡潔にまとめてください。
                回答は日本語で、実用的かつ落ち着いたトーンにしてください。
                データにないことは断定しすぎないでください。
                説教くさくならず、短く読みやすく整理してください。
                """;
    }

    private String buildUserPrompt(Settings settings) {
        List<Application> applications = applicationRepository.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("以下は現在の応募状況データです。\n\n");

        if (applications.isEmpty()) {
            sb.append("応募データはまだ登録されていません。\n\n");
        } else {
            appendOverview(sb, applications);
            sb.append("\n");
            appendApplicationSummaries(sb, applications);
        }

        if (settings.getAiUserPromptTemplate() != null && !settings.getAiUserPromptTemplate().isBlank()) {
            sb.append("追加指示:\n");
            sb.append(settings.getAiUserPromptTemplate()).append("\n\n");
        }

        sb.append("""
                この情報をもとに、以下を短くまとめてください。
                1. 現状の全体傾向
                2. 注意して見ておきたい点
                3. 次に取るとよい行動

                長すぎず、すぐ読める長さでお願いします。
                """);

        return sb.toString();
    }

    private void appendOverview(StringBuilder sb, List<Application> applications) {
        long totalCount = applications.size();

        long appliedCount = countByStatus(applications, ApplicationStatus.APPLIED);
        long webTestCount = countByStatus(applications, ApplicationStatus.WEB_TEST);
        long documentPassCount = countByStatus(applications, ApplicationStatus.DOCUMENT_PASS);
        long interviewCount =
                countByStatus(applications, ApplicationStatus.INTERVIEW_1)
                + countByStatus(applications, ApplicationStatus.INTERVIEW_2)
                + countByStatus(applications, ApplicationStatus.FINAL_INTERVIEW);
        long offerCount = countByStatus(applications, ApplicationStatus.OFFER);
        long rejectCount = countByStatus(applications, ApplicationStatus.REJECT);
        long withdrawnCount = countByStatus(applications, ApplicationStatus.WITHDRAWN);

        String positions = applications.stream()
                .map(Application::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(6)
                .collect(Collectors.joining("、"));

        sb.append("【応募全体の概要】\n");
        sb.append("- 応募件数: ").append(totalCount).append("件\n");
        sb.append("- 主な職種: ").append(positions.isBlank() ? "-" : positions).append("\n");
        sb.append("- 応募済み: ").append(appliedCount).append("件\n");
        sb.append("- WEBテスト: ").append(webTestCount).append("件\n");
        sb.append("- 書類通過: ").append(documentPassCount).append("件\n");
        sb.append("- 面接中: ").append(interviewCount).append("件\n");
        sb.append("- 内定: ").append(offerCount).append("件\n");
        sb.append("- 見送り: ").append(rejectCount).append("件\n");
        sb.append("- 辞退: ").append(withdrawnCount).append("件\n");
    }

    private void appendApplicationSummaries(StringBuilder sb, List<Application> applications) {
        sb.append("【応募一覧の要約】\n");

        for (Application app : applications) {
            sb.append("- 会社名: ").append(nullToDash(app.getCompanyName())).append("\n");
            sb.append("  職種: ").append(nullToDash(app.getPositionName())).append("\n");
            sb.append("  状況: ").append(app.getCurrentStatus() != null ? app.getCurrentStatus().name() : "-").append("\n");
            sb.append("  メモ: ").append(nullToDash(app.getMemo())).append("\n\n");
        }
    }

    private long countByStatus(List<Application> applications, ApplicationStatus status) {
        return applications.stream()
                .filter(app -> app.getCurrentStatus() == status)
                .count();
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}