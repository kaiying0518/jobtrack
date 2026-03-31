package com.example.jobtrack.entity;

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
}
