package com.example.jobtrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.SettingsRepository;

@Service
@Transactional
public class SettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public Settings getSettings() {
        return settingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);
    }

    public Settings save(String defaultResumeVersion,
                         ApplicationStatus defaultStatus,
                         Boolean useTodayAsDefault,
                         String defaultChannel,
                         Boolean aiEnabled,
                         AiProviderType aiProvider,
                         String aiApiKey,
                         String aiModel,
                         String aiSystemPrompt,
                         String aiUserPromptTemplate,
                         Integer aiMaxTokens,
                         Double aiTemperature, Boolean useFullChatHistory) {

        Settings settings = getSettings();

        settings.setDefaultResumeVersion(defaultResumeVersion != null ? defaultResumeVersion : "");
        settings.setDefaultStatus(defaultStatus != null ? defaultStatus : ApplicationStatus.APPLIED);
        settings.setUseTodayAsDefault(useTodayAsDefault != null ? useTodayAsDefault : false);
        settings.setDefaultChannel(defaultChannel != null ? defaultChannel : "");

        settings.setAiEnabled(aiEnabled != null ? aiEnabled : false);
        settings.setAiProvider(aiProvider != null ? aiProvider : AiProviderType.OPENAI);
        settings.setAiModel(aiModel != null ? aiModel : "");
        settings.setAiSystemPrompt(aiSystemPrompt != null ? aiSystemPrompt : "");
        settings.setAiUserPromptTemplate(aiUserPromptTemplate != null ? aiUserPromptTemplate : "");
        settings.setAiMaxTokens(aiMaxTokens);
        settings.setAiTemperature(aiTemperature);
        settings.setUseFullChatHistory(useFullChatHistory != null ? useFullChatHistory : false);

        if (aiApiKey != null && !aiApiKey.isBlank()) {
            settings.setAiApiKey(aiApiKey);
        }

        return settingsRepository.save(settings);
    }

    private Settings createDefaultSettings() {
        Settings settings = new Settings();
        settings.setId(SETTINGS_ID);

        settings.setDefaultResumeVersion("");
        settings.setDefaultStatus(ApplicationStatus.APPLIED);
        settings.setUseTodayAsDefault(true);
        settings.setDefaultChannel("");

        settings.setAiEnabled(false);
        settings.setAiProvider(AiProviderType.OPENAI);
        settings.setAiApiKey("");
        settings.setAiModel("");
        settings.setAiSystemPrompt("");
        settings.setAiUserPromptTemplate("");
        settings.setAiMaxTokens(null);
        settings.setAiTemperature(null);

        return settingsRepository.save(settings);
    }
}