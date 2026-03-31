package com.example.jobtrack.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "applications")
@Getter
@Setter
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    private String positionName;
    private String channel;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus currentStatus;

    private LocalDate appliedDate;
    private String resumeVersion;
    private String region;
    private String employmentType;
    private String jobUrl;

    @Column(length = 1000)
    private String memo;

    private LocalDateTime nextActionAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActivityLog> activityLogs = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortalInfo> portalInfos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (appliedDate == null) {
            appliedDate = LocalDate.now();
        }
        if (currentStatus == null) {
            currentStatus = ApplicationStatus.APPLIED;
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}