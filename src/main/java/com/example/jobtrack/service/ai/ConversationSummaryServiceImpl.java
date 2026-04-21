package com.example.jobtrack.service.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Chat;
import com.example.jobtrack.entity.ChatMessage;
import com.example.jobtrack.entity.ChatRole;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.ChatMessageRepository;

@Service
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private final ChatMessageRepository chatMessageRepository;
    private final Map<AiProviderType, AiClient> clientMap;

    public ConversationSummaryServiceImpl(ChatMessageRepository chatMessageRepository,
                                          List<AiClient> clients) {
        this.chatMessageRepository = chatMessageRepository;
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(AiClient::supportedProvider, Function.identity()));
    }

    @Override
    public boolean shouldRefreshSummary(Chat chat, Settings settings) {
        List<ChatMessage> history = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());

        if (history.size() < 12) {
            return false;
        }

        return chat.getSummary() == null || chat.getSummary().isBlank();
    }

    @Override
    public String buildSummary(Chat chat, Settings settings) {
        List<ChatMessage> history = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());

        if (history.isEmpty()) {
            return "";
        }

        int summaryTargetSize = Math.max(0, history.size() - 8);
        List<ChatMessage> targetMessages = history.subList(0, summaryTargetSize);

        if (targetMessages.isEmpty()) {
            return "";
        }

        String conversationText = targetMessages.stream()
                .map(message -> {
                    String role = message.getRole() == ChatRole.USER ? "ユーザー" : "AI";
                    return role + ": " + nullToEmpty(message.getContent());
                })
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                あなたは会話要約アシスタントです。
                以下の会話履歴を短く整理してください。

                要件:
                - ユーザーが主に気にしている点
                - これまでに整理された状況や結論
                - 継続して参照したい文脈
                - 余計な言い回しは避け、簡潔にまとめる
                - 箇条書きでもよい
                """;

        String userPrompt = """
                以下の会話履歴を要約してください。

                %s
                """.formatted(conversationText);

        AiClient client = clientMap.get(settings.getAiProvider());
        if (client == null) {
            throw new IllegalStateException("対応していないAIプロバイダです。");
        }

        return client.generateText(settings, systemPrompt, userPrompt);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}