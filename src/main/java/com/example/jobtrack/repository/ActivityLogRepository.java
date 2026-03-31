package com.example.jobtrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.jobtrack.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByApplicationIdOrderByActionDateDesc(Long applicationId);
    void deleteByApplicationId(Long applicationId);
}