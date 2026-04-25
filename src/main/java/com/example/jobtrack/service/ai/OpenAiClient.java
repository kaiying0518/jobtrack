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
import com.example.jobtrack.service.ai.dto.openai.OpenAiChatMessage;
import com.example.jobtrack.service.ai.dto.openai.OpenAiChatRequest;
import com.example.jobtrack.service.ai.dto.openai.OpenAiChatResponse;
import com.example.jobtrack.service.ai.dto.openai.OpenAiChoice;
import com.example.jobtrack.service.ai.dto.openai.OpenAiResponseMessage;

@Service
public class OpenAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private static final String OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    private final RestTemplate restTemplate;

    public OpenAiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public AiProviderType supportedProvider() {
        return AiProviderType.OPENAI;
    }

    @Override
    public String generateText(Settings settings, String systemPrompt, String userPrompt) {
        validateSettings(settings);
        validateUserPrompt(userPrompt);

        HttpHeaders headers = createHeaders(settings);

        List<OpenAiChatMessage> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new OpenAiChatMessage("system", systemPrompt));
        }

        messages.add(new OpenAiChatMessage("user", userPrompt));

        OpenAiChatRequest requestBody = new OpenAiChatRequest();
        requestBody.setModel(resolveModel(settings));
        requestBody.setMessages(messages);

        if (settings.getAiTemperature() != null) {
            requestBody.setTemperature(settings.getAiTemperature());
        }

        if (settings.getAiMaxTokens() != null) {
            requestBody.setMaxTokens(settings.getAiMaxTokens());
        }

        HttpEntity<OpenAiChatRequest> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling OpenAI generateText. model={}, messageCount={}",
                requestBody.getModel(),
                messages.size());

        ResponseEntity<OpenAiChatResponse> response;
        try {
            response = restTemplate.postForEntity(
                    OPENAI_CHAT_COMPLETIONS_URL,
                    request,
                    OpenAiChatResponse.class
            );
            log.info("OpenAI generateText completed. status={}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("OpenAI generateText failed. errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw convertOpenAiException(e);
        }

        OpenAiChatResponse responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("OpenAI response body is null");
        }

        List<OpenAiChoice> choices = responseBody.getChoices();
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI choices is empty");
        }

        OpenAiChoice firstChoice = choices.get(0);
        if (firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {
            throw new IllegalStateException("OpenAI content is empty");
        }

        return firstChoice.getMessage().getContent().trim();
    }

    @Override
    public String chat(Settings settings, String systemPrompt, List<AiChatMessage> messages) {
        validateSettings(settings);

        List<OpenAiChatMessage> requestMessages = new ArrayList<>();
        requestMessages.add(new OpenAiChatMessage("system", systemPrompt));

        for (AiChatMessage message : messages) {
            requestMessages.add(new OpenAiChatMessage(message.getRole(), message.getContent()));
        }

        OpenAiChatRequest request = new OpenAiChatRequest();
        request.setModel(resolveModel(settings));
        request.setMessages(requestMessages);

        if (settings.getAiTemperature() != null) {
            request.setTemperature(settings.getAiTemperature());
        }

        if (settings.getAiMaxTokens() != null) {
            request.setMaxTokens(settings.getAiMaxTokens());
        }

        HttpHeaders headers = createHeaders(settings);
        HttpEntity<OpenAiChatRequest> entity = new HttpEntity<>(request, headers);

        log.info("Calling OpenAI chat. model={}, messageCount={}",
                request.getModel(),
                requestMessages.size());

        ResponseEntity<OpenAiChatResponse> response;
        try {
            response = restTemplate.postForEntity(
                    OPENAI_CHAT_COMPLETIONS_URL,
                    entity,
                    OpenAiChatResponse.class
            );
            log.info("OpenAI chat completed. status={}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("OpenAI chat failed. errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw convertOpenAiException(e);
        }

        OpenAiChatResponse body = response.getBody();

        if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
            throw new IllegalStateException("OpenAI から有効な応答が返されませんでした。");
        }

        OpenAiResponseMessage message = body.getChoices().get(0).getMessage();

        if (message == null || message.getContent() == null || message.getContent().isBlank()) {
            throw new IllegalStateException("OpenAI の応答内容が空です。");
        }

        return message.getContent().trim();
    }

    private HttpHeaders createHeaders(Settings settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(settings.getAiApiKey());
        return headers;
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
            throw new IllegalStateException("OpenAI API key is empty");
        }
    }

    private void validateUserPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalStateException("User prompt is empty");
        }
    }

    private IllegalStateException convertOpenAiException(Exception e) {
        if (e instanceof HttpClientErrorException httpError) {
            String body = httpError.getResponseBodyAsString();
            int status = httpError.getStatusCode().value();

            if (body != null && body.contains("insufficient_quota")) {
                return new IllegalStateException("OpenAI API の利用上限に達しています。APIの利用枠・課金設定を確認してください。");
            }

            if (status == 401) {
                return new IllegalStateException("OpenAI APIキーが無効、または権限がありません。APIキーを確認してください。");
            }

            if (status == 403) {
                return new IllegalStateException("OpenAI API へのアクセスが拒否されました。権限設定や利用条件を確認してください。");
            }

            if (status == 404) {
                return new IllegalStateException("OpenAI API のエンドポイントまたはモデル設定が正しくありません。");
            }

            if (status == 429) {
                return new IllegalStateException("OpenAI API のリクエスト制限に達しました。しばらく待ってから再試行してください。");
            }

            return new IllegalStateException("OpenAI API 呼び出しに失敗しました: " + httpError.getStatusCode());
        }

        if (e instanceof ResourceAccessException) {
            return new IllegalStateException("OpenAI API へ接続できませんでした。ネットワーク接続または外部サービスの状態を確認してください。");
        }

        if (e instanceof RestClientException) {
            return new IllegalStateException("OpenAI API との通信中にエラーが発生しました。しばらくしてから再試行してください。");
        }

        return new IllegalStateException("AI呼び出し中に予期しないエラーが発生しました。");
    }
}