package com.example.jobtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.jobtrack.entity.Settings;

public interface SettingsRepository extends JpaRepository<Settings, Long> {
}
