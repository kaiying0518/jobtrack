package com.example.jobtrack.service.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobtrack.dto.ChatMessageResponse;
import com.example.jobtrack.dto.ChatRequest;
import com.example.jobtrack.dto.ChatResponse;
import com.example.jobtrack.dto.ChatSessionResponse;
import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.Chat;
import com.example.jobtrack.entity.ChatMessage;
import com.example.jobtrack.entity.ChatRole;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.ApplicationRepository;
import com.example.jobtrack.repository.ChatMessageRepository;
import com.example.jobtrack.repository.ChatRepository;
import com.example.jobtrack.service.SettingsService;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationRepository applicationRepository;
    private final SettingsService settingsService;
    private final Map<AiProviderType, AiClient> clientMap;

    public AiChatServiceImpl(ChatRepository chatRepository,
                             ChatMessageRepository chatMessageRepository,
                             ApplicationRepository applicationRepository,
                             SettingsService settingsService,
                             List<AiClient> clients) {
        this.chatRepository = chatRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.applicationRepository = applicationRepository;
        this.settingsService = settingsService;
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(AiClient::supportedProvider, Function.identity()));
    }

    @Override
    @Transactional
    public ChatResponse sendMessage(ChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ChatResponse.error("メッセージを入力してください。");
        }

        try {
            String userMessage = request.getMessage().trim();
            Chat chat = getOrCreateChat(request, userMessage);

            ChatMessage userChatMessage = new ChatMessage(
                    chat,
                    ChatRole.USER,
                    userMessage,
                    LocalDateTime.now()
            );
            chatMessageRepository.save(userChatMessage);

            String aiReply = generateAiReply(chat);

            ChatMessage assistantChatMessage = new ChatMessage(
                    chat,
                    ChatRole.ASSISTANT,
                    aiReply,
                    LocalDateTime.now()
            );
            chatMessageRepository.save(assistantChatMessage);

            return ChatResponse.success(chat.getId(), aiReply);

        } catch (Exception e) {
            return ChatResponse.error(resolveErrorMessage(e));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long chatId) {
        return chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId)
                .stream()
                .map(message -> new ChatMessageResponse(
                        message.getRole().name(),
                        message.getContent(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getChatSessions() {
        return chatRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(chat -> new ChatSessionResponse(
                        chat.getId(),
                        chat.getTitle()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteChat(Long chatId) {
        if (chatId == null) {
            throw new IllegalArgumentException("チャットIDが不正です。");
        }

        if (!chatRepository.existsById(chatId)) {
            throw new IllegalArgumentException("チャットが見つかりません。");
        }

        chatMessageRepository.deleteByChatId(chatId);
        chatRepository.deleteById(chatId);
    }

    private String generateAiReply(Chat chat) {
        Settings settings = settingsService.getSettings();
        validateSettings(settings);

        AiClient client = clientMap.get(settings.getAiProvider());
        if (client == null) {
            throw new IllegalStateException("対応していないAIプロバイダです。");
        }

        String latestUserMessage = getLatestUserMessage(chat);

        String systemPrompt = buildSystemPrompt(settings)
                + "\n\n"
                + buildOverviewContext()
                + "\n\n"
                + buildDefaultChatContext();

        String additionalContext = buildAdditionalContext(latestUserMessage);
        if (!additionalContext.isBlank()) {
            systemPrompt = systemPrompt + "\n\n" + additionalContext;
        }

        List<AiChatMessage> messages = buildChatMessages(chat, settings);

        return client.chat(settings, systemPrompt, messages);
    }

    private void validateSettings(Settings settings) {
        if (settings == null) {
            throw new IllegalStateException("設定が見つかりません。");
        }
        if (!Boolean.TRUE.equals(settings.getAiEnabled())) {
            throw new IllegalStateException("AI機能が無効です。");
        }
        if (settings.getAiProvider() == null) {
            throw new IllegalStateException("AIプロバイダが未設定です。");
        }
        if (settings.getAiApiKey() == null || settings.getAiApiKey().isBlank()) {
            throw new IllegalStateException("AI APIキーが未設定です。");
        }
    }

    private String buildSystemPrompt(Settings settings) {
        if (settings.getAiSystemPrompt() != null && !settings.getAiSystemPrompt().isBlank()) {
            return settings.getAiSystemPrompt();
        }

        return """
                あなたは JobTrack の就職活動支援AIです。
                保存データを参考にしながら、ユーザーが自分の状況を整理できるよう支援してください。
                直近のユーザー入力と同じ言語で回答してください。
                中国語で質問されたら中国語で、日本語で質問されたら日本語で回答してください。
                回答は実用的かつ落ち着いたトーンにしてください。
                データにないことは断定しすぎないでください。
                """;
    }

    private String buildOverviewContext() {
        List<Application> applications = applicationRepository.findAll();

        if (applications.isEmpty()) {
            return """
                    【応募全体の概要】
                    現在、登録されている応募データはありません。
                    """;
        }

        long appliedCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != null
                        && app.getCurrentStatus().name().equals("APPLIED"))
                .count();

        long webTestCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != null
                        && app.getCurrentStatus().name().equals("WEB_TEST"))
                .count();

        long interviewCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != null
                        && (app.getCurrentStatus().name().equals("INTERVIEW_1")
                        || app.getCurrentStatus().name().equals("INTERVIEW_2")
                        || app.getCurrentStatus().name().equals("FINAL_INTERVIEW")))
                .count();

        String companyNames = applications.stream()
                .map(Application::getCompanyName)
                .filter(name -> name != null && !name.isBlank())
                .limit(5)
                .collect(Collectors.joining("、"));

        String positions = applications.stream()
                .map(Application::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.joining("、"));

        StringBuilder sb = new StringBuilder();
        sb.append("【応募全体の概要】\n");
        sb.append("- 応募件数: ").append(applications.size()).append("件\n");
        sb.append("- 主な会社名: ").append(companyNames.isBlank() ? "-" : companyNames).append("\n");
        sb.append("- 主な職種: ").append(positions.isBlank() ? "-" : positions).append("\n");
        sb.append("- 応募済み: ").append(appliedCount).append("件\n");
        sb.append("- WEBテスト: ").append(webTestCount).append("件\n");
        sb.append("- 面接中: ").append(interviewCount).append("件\n");

        return sb.toString();
    }

    private String buildDefaultChatContext() {
        List<Application> applications = applicationRepository.findAll();

        if (applications.isEmpty()) {
            return """
                    【保存データ】
                    現在、登録されている応募データはありません。
                    """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【保存データ】\n");
        sb.append("以下はユーザーの応募状況の要約です。\n\n");

        applications.stream()
                .limit(8)
                .forEach(app -> {
                    sb.append("- 会社名: ").append(nullToDash(app.getCompanyName())).append("\n");
                    sb.append("  職種: ").append(nullToDash(app.getPositionName())).append("\n");
                    sb.append("  現在ステータス: ")
                      .append(app.getCurrentStatus() != null ? app.getCurrentStatus().name() : "-")
                      .append("\n");
                    sb.append("  メモ: ").append(nullToDash(app.getMemo())).append("\n\n");
                });

        sb.append("""
                この要約を参考に回答してください。
                必要なら追加データを踏まえて整理してください。
                """);

        return sb.toString();
    }

    private String buildAdditionalContext(String userMessage) {
        String text = userMessage == null ? "" : userMessage;

        if (isAnalysisQuestion(text) || isAdviceQuestion(text) || isDirectionQuestion(text)) {
            return buildDetailedContext();
        }

        return "";
    }

    private boolean isAnalysisQuestion(String text) {
        return text.contains("分析")
                || text.contains("傾向")
                || text.contains("整理")
                || text.contains("状況")
                || text.contains("情况")
                || text.contains("整体")
                || text.contains("总结");
    }

    private boolean isAdviceQuestion(String text) {
        return text.contains("次")
                || text.contains("優先")
                || text.contains("どうすれば")
                || text.contains("何をする")
                || text.contains("下一步")
                || text.contains("优先")
                || text.contains("怎么办")
                || text.contains("该做什么");
    }

    private boolean isDirectionQuestion(String text) {
        return text.contains("方向")
                || text.contains("向いて")
                || text.contains("合って")
                || text.contains("軸")
                || text.contains("适合")
                || text.contains("合适")
                || text.contains("路线");
    }

    private String buildDetailedContext() {
        List<Application> applications = applicationRepository.findAll();

        if (applications.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【追加データ】\n");
        sb.append("以下はより詳しい応募データです。\n\n");

        for (Application app : applications) {
            sb.append("- 会社名: ").append(nullToDash(app.getCompanyName())).append("\n");
            sb.append("  職種: ").append(nullToDash(app.getPositionName())).append("\n");
            sb.append("  現在ステータス: ")
              .append(app.getCurrentStatus() != null ? app.getCurrentStatus().name() : "-")
              .append("\n");
            sb.append("  メモ: ").append(nullToDash(app.getMemo())).append("\n\n");
        }

        sb.append("""
                この追加データは、分析・提案・方向性の整理が必要な場合に使ってください。
                """);

        return sb.toString();
    }

    private String getLatestUserMessage(Chat chat) {
        List<ChatMessage> history = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getRole() == ChatRole.USER) {
                return message.getContent();
            }
        }

        return "";
    }

    private List<AiChatMessage> buildChatMessages(Chat chat, Settings settings) {
        List<ChatMessage> history = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());

        boolean useFullChatHistory = Boolean.TRUE.equals(settings.getUseFullChatHistory());

        List<ChatMessage> targetMessages;
        if (useFullChatHistory) {
            targetMessages = history;
        } else {
            int fromIndex = Math.max(0, history.size() - 8);
            targetMessages = history.subList(fromIndex, history.size());
        }

        return targetMessages.stream()
                .map(message -> new AiChatMessage(
                        message.getRole() == ChatRole.USER ? "user" : "assistant",
                        nullToEmpty(message.getContent())
                ))
                .toList();
    }

    private Chat getOrCreateChat(ChatRequest request, String userMessage) {
        if (request.getChatId() != null) {
            return chatRepository.findById(request.getChatId())
                    .orElseThrow(() -> new IllegalArgumentException("チャットが見つかりません。"));
        }

        String title = createTitle(userMessage);
        Chat newChat = new Chat(title, LocalDateTime.now());
        return chatRepository.save(newChat);
    }

    private String createTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新しいチャット";
        }

        String trimmed = message.trim();
        return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20);
    }

    private String resolveErrorMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return "AIチャットの処理中にエラーが発生しました。";
        }
        return e.getMessage();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}