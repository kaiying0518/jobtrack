package com.example.jobtrack.service.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.service.ai.dto.claude.ClaudeContent;
import com.example.jobtrack.service.ai.dto.claude.ClaudeMessage;
import com.example.jobtrack.service.ai.dto.claude.ClaudeRequest;
import com.example.jobtrack.service.ai.dto.claude.ClaudeResponse;

@Service
public class ClaudeClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private static final String CLAUDE_MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-latest";
    private static final int DEFAULT_MAX_TOKENS = 1000;

    private final RestTemplate restTemplate;

    public ClaudeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public AiProviderType supportedProvider() {
        return AiProviderType.CLAUDE;
    }

    @Override
    public String generateText(Settings settings, String systemPrompt, String userPrompt) {
        validateSettings(settings);

        ClaudeRequest requestBody = new ClaudeRequest();
        requestBody.setModel(resolveModel(settings));
        requestBody.setSystem(systemPrompt);
        requestBody.setMaxTokens(resolveMaxTokens(settings));

        List<ClaudeMessage> messages = new ArrayList<>();
        messages.add(new ClaudeMessage("user", List.of(
                new ClaudeContent("text", userPrompt)
        )));
        requestBody.setMessages(messages);

        HttpHeaders headers = createHeaders(settings);
        HttpEntity<ClaudeRequest> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling Claude generateText. model={}, maxTokens={}",
                requestBody.getModel(),
                requestBody.getMaxTokens());

        ResponseEntity<ClaudeResponse> response;
        try {
            response = restTemplate.postForEntity(
                    CLAUDE_MESSAGES_URL,
                    request,
                    ClaudeResponse.class
            );
            log.info("Claude generateText completed. status={}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("Claude generateText failed. errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw convertClaudeException(e);
        }

        return extractText(response.getBody());
    }

    @Override
    public String chat(Settings settings, String systemPrompt, List<AiChatMessage> messages) {
        validateSettings(settings);

        ClaudeRequest requestBody = new ClaudeRequest();
        requestBody.setModel(resolveModel(settings));
        requestBody.setSystem(systemPrompt);
        requestBody.setMaxTokens(resolveMaxTokens(settings));

        List<ClaudeMessage> requestMessages = new ArrayList<>();
        for (AiChatMessage message : messages) {
            String role = "assistant".equalsIgnoreCase(message.getRole()) ? "assistant" : "user";
            requestMessages.add(new ClaudeMessage(
                    role,
                    List.of(new ClaudeContent("text",
                            message.getContent() == null ? "" : message.getContent()))
            ));
        }
        requestBody.setMessages(requestMessages);

        HttpHeaders headers = createHeaders(settings);
        HttpEntity<ClaudeRequest> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling Claude chat. model={}, maxTokens={}, messageCount={}",
                requestBody.getModel(),
                requestBody.getMaxTokens(),
                requestMessages.size());

        ResponseEntity<ClaudeResponse> response;
        try {
            response = restTemplate.postForEntity(
                    CLAUDE_MESSAGES_URL,
                    request,
                    ClaudeResponse.class
            );
            log.info("Claude chat completed. status={}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("Claude chat failed. errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw convertClaudeException(e);
        }

        return extractText(response.getBody());
    }

    private HttpHeaders createHeaders(Settings settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", settings.getAiApiKey());
        headers.set("anthropic-version", "2023-06-01");
        return headers;
    }

    private String resolveModel(Settings settings) {
        if (settings.getAiModel() != null && !settings.getAiModel().isBlank()) {
            return settings.getAiModel().trim();
        }
        return DEFAULT_MODEL;
    }

    private int resolveMaxTokens(Settings settings) {
        if (settings.getAiMaxTokens() != null && settings.getAiMaxTokens() > 0) {
            return settings.getAiMaxTokens();
        }
        return DEFAULT_MAX_TOKENS;
    }

    private void validateSettings(Settings settings) {
        if (settings == null) {
            throw new IllegalStateException("Settings is null");
        }
        if (settings.getAiApiKey() == null || settings.getAiApiKey().isBlank()) {
            throw new IllegalStateException("Claude API key is empty");
        }
    }

    private String extractText(ClaudeResponse response) {
        if (response == null
                || response.getContent() == null
                || response.getContent().isEmpty()
                || response.getContent().get(0).getText() == null
                || response.getContent().get(0).getText().isBlank()) {
            throw new IllegalStateException("Claude から有効な応答が返されませんでした。");
        }

        return response.getContent().get(0).getText().trim();
    }

    private IllegalStateException convertClaudeException(Exception e) {
        if (e instanceof HttpClientErrorException httpError) {
            int status = httpError.getStatusCode().value();

            if (status == 401) {
                return new IllegalStateException("Claude APIキーが無効、または認証に失敗しました。APIキーを確認してください。");
            }

            if (status == 403) {
                return new IllegalStateException("Claude API へのアクセスが拒否されました。権限設定や利用条件を確認してください。");
            }

            if (status == 404) {
                return new IllegalStateException("Claude API のエンドポイントまたはモデル設定が正しくありません。");
            }

            if (status == 429) {
                return new IllegalStateException("Claude API の利用上限またはリクエスト制限に達しました。しばらくしてから再試行してください。");
            }

            return new IllegalStateException("Claude API 呼び出しに失敗しました: " + httpError.getStatusCode());
        }

        if (e instanceof ResourceAccessException) {
            return new IllegalStateException("Claude API へ接続できませんでした。ネットワーク接続または外部サービスの状態を確認してください。");
        }

        if (e instanceof RestClientException) {
            return new IllegalStateException("Claude API との通信中にエラーが発生しました。しばらくしてから再試行してください。");
        }

        return new IllegalStateException("Claude 呼び出し中に予期しないエラーが発生しました。");
    }
}