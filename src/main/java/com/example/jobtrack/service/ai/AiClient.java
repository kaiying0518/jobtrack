package com.example.jobtrack.service.ai;

import java.util.List;

import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Settings;

public interface AiClient {

    AiProviderType supportedProvider();

    String generateText(Settings settings, String systemPrompt, String userPrompt);

    String chat(Settings settings, String systemPrompt, List<AiChatMessage> messages);
}