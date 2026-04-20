package com.example.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "settings")
@Getter
@Setter
public class Settings {

    @Id
    private Long id;

    private String defaultResumeVersion;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus defaultStatus;

    private Boolean useTodayAsDefault;

    private String defaultChannel;

    private Boolean aiEnabled;

    @Enumerated(EnumType.STRING)
    private AiProviderType aiProvider;

    private String aiApiKey;

    private String aiModel;

    @Column(length = 5000)
    private String aiSystemPrompt;

    @Column(length = 5000)
    private String aiUserPromptTemplate;

    private Integer aiMaxTokens;

    private Double aiTemperature;

    private Boolean useFullChatHistory;

	public boolean setUseFullChatHistory;
}