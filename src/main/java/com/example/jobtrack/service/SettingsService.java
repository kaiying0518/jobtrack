package com.example.jobtrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                         String defaultChannel) {

        Settings settings = getSettings();
        settings.setDefaultResumeVersion(defaultResumeVersion);
        settings.setDefaultStatus(defaultStatus);
        settings.setUseTodayAsDefault(useTodayAsDefault != null ? useTodayAsDefault : false);
        settings.setDefaultChannel(defaultChannel);

        return settingsRepository.save(settings);
    }

    private Settings createDefaultSettings() {
        Settings settings = new Settings();
        settings.setId(SETTINGS_ID);
        settings.setDefaultResumeVersion("");
        settings.setDefaultStatus(ApplicationStatus.APPLIED);
        settings.setUseTodayAsDefault(true);
        settings.setDefaultChannel("");
        return settingsRepository.save(settings);
    }
}