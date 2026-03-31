package com.example.jobtrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.jobtrack.entity.PortalInfo;

public interface PortalInfoRepository extends JpaRepository<PortalInfo, Long> {

    List<PortalInfo> findByApplicationId(Long applicationId);

    @Modifying
    @Query("delete from PortalInfo p where p.application.id = :applicationId")
    void deleteByApplicationId(Long applicationId);
}