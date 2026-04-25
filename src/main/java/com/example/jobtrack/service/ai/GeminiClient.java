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
import com.example.jobtrack.service.ai.dto.gemini.GeminiContent;
import com.example.jobtrack.service.ai.dto.gemini.GeminiPart;
import com.example.jobtrack.service.ai.dto.gemini.GeminiRequest;
import com.example.jobtrack.service.ai.dto.gemini.GeminiResponse;

@Service
public class GeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String DEFAULT_MODEL = "gemini-1.5-flash";

    private final RestTemplate restTemplate;

    public GeminiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public AiProviderType supportedProvider() {
        return AiProviderType.GEMINI;
    }

    @Override
    public String generateText(Settings settings, String systemPrompt, String userPrompt) {
        validateSettings(settings);

        String model = resolveModel(settings);
        String url = buildUrl(settings);

        List<GeminiContent> contents = new ArrayList<>();
        contents.add(new GeminiContent("user", List.of(
                new GeminiPart(buildSinglePrompt(systemPrompt, userPrompt))
        )));

        GeminiRequest requestBody = new GeminiRequest();
        requestBody.setContents(contents);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GeminiRequest> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling Gemini generateText. model={}", model);

        ResponseEntity<GeminiResponse> response;
        try {
            response = restTemplate.postForEntity(url, request, GeminiResponse.class);
            log.info("Gemini generateText completed. status={}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("Gemini generateText failed. errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw convertGeminiException(e);
        }

        return extractText(response.getBody());
    }

    @Override
    public String chat(Settings settings, String systemPrompt, List<AiChatMessage> messages) {
        validateSettings(settings);

        String model = resolveModel(settings);
        String url = buildUrl(settings);

        List<GeminiContent> contents = new ArrayList<>();
        contents.add(new GeminiContent("user", List.of(
                new GeminiPart(buildChatPrompt(systemPrompt, messages))
        )));

        GeminiRequest requestBody = new GeminiRequest();
        requestBody.setContents(contents);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GeminiRequest> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling Gemini chat. model={}, messageCount={}",
                model,
                messages == null ? 0 : messages.size());

        ResponseEntity<GeminiResponse> response;
        try {
            response = restTemplate.postForEntity(url, request, GeminiResponse.class);
            log.info("Gemini chat completed. status={}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("Gemini chat failed. errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw convertGeminiException(e);
        }

        return extractText(response.getBody());
    }

    private String buildUrl(Settings settings) {
        String model = resolveModel(settings);
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + model
                + ":generateContent?key="
                + settings.getAiApiKey();
    }

    private String resolveModel(Settings settings) {
        if (settings.getAiModel() != null && !settings.getAiModel().isBlank()) {
            return settings.getAiModel().trim();
        }
        return DEFAULT_MODEL;
    }

    private void validateSettings(Settings settings) {
        if (settings == null) {
            throw new IllegalStateException("Settings is null");
        }
        if (settings.getAiApiKey() == null || settings.getAiApiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key is empty");
        }
    }

    private String buildSinglePrompt(String systemPrompt, String userPrompt) {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append("System instruction:\n")
              .append(systemPrompt)
              .append("\n\n");
        }

        sb.append("User request:\n")
          .append(userPrompt);

        return sb.toString();
    }

    private String buildChatPrompt(String systemPrompt, List<AiChatMessage> messages) {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append("System instruction:\n")
              .append(systemPrompt)
              .append("\n\n");
        }

        sb.append("Conversation:\n");

        for (AiChatMessage message : messages) {
            String role = "user".equalsIgnoreCase(message.getRole()) ? "User" : "Assistant";
            sb.append(role)
              .append(": ")
              .append(message.getContent() == null ? "" : message.getContent())
              .append("\n");
        }

        sb.append("\nPlease continue the conversation naturally in Japanese.");
        return sb.toString();
    }

    private String extractText(GeminiResponse response) {
        if (response == null
                || response.getCandidates() == null
                || response.getCandidates().isEmpty()
                || response.getCandidates().get(0).getContent() == null
                || response.getCandidates().get(0).getContent().getParts() == null
                || response.getCandidates().get(0).getContent().getParts().isEmpty()
                || response.getCandidates().get(0).getContent().getParts().get(0).getText() == null
                || response.getCandidates().get(0).getContent().getParts().get(0).getText().isBlank()) {
            throw new IllegalStateException("Gemini から有効な応答が返されませんでした。");
        }

        return response.getCandidates().get(0).getContent().getParts().get(0).getText().trim();
    }

    private IllegalStateException convertGeminiException(Exception e) {
        if (e instanceof HttpClientErrorException httpError) {
            String body = httpError.getResponseBodyAsString();
            int status = httpError.getStatusCode().value();

            if (status == 401) {
                return new IllegalStateException("Gemini APIキーが無効、または認証に失敗しました。APIキーを確認してください。");
            }

            if (status == 403) {
                return new IllegalStateException("Gemini API へのアクセスが拒否されました。権限設定や利用条件を確認してください。");
            }

            if (status == 404) {
                return new IllegalStateException("Gemini API のエンドポイントまたはモデル設定が正しくありません。");
            }

            if (status == 429) {
                if (body != null && !body.isBlank()) {
                    return new IllegalStateException("Gemini API の利用上限またはリクエスト制限に達しました。しばらくしてから再試行してください。");
                }
                return new IllegalStateException("Gemini API のリクエスト制限に達しました。しばらくしてから再試行してください。");
            }

            return new IllegalStateException("Gemini API 呼び出しに失敗しました: " + httpError.getStatusCode());
        }

        if (e instanceof ResourceAccessException) {
            return new IllegalStateException("Gemini API へ接続できませんでした。ネットワーク接続または外部サービスの状態を確認してください。");
        }

        if (e instanceof RestClientException) {
            return new IllegalStateException("Gemini API との通信中にエラーが発生しました。しばらくしてから再試行してください。");
        }

        return new IllegalStateException("Gemini 呼び出し中に予期しないエラーが発生しました。");
    }
}