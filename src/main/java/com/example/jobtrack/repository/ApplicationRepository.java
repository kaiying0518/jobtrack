package com.example.jobtrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByCompanyNameContainingIgnoreCaseOrPositionNameContainingIgnoreCase(String companyKeyword,
                                                                                               String positionKeyword);

    List<Application> findByCurrentStatus(ApplicationStatus status);

    List<Application> findByChannel(String channel);

    List<Application> findByCurrentStatusAndChannel(ApplicationStatus status, String channel);

    List<Application> findByCurrentStatusOrderByUpdatedAtDesc(ApplicationStatus status);

    List<Application> findByCurrentStatusOrderByAppliedDateDesc(ApplicationStatus status);

    List<Application> findByCurrentStatusOrderByCompanyNameAsc(ApplicationStatus status);

    List<Application> findByChannelOrderByUpdatedAtDesc(String channel);

    List<Application> findByChannelOrderByAppliedDateDesc(String channel);

    List<Application> findByChannelOrderByCompanyNameAsc(String channel);

    List<Application> findByCurrentStatusAndChannelOrderByUpdatedAtDesc(ApplicationStatus status, String channel);

    List<Application> findByCurrentStatusAndChannelOrderByAppliedDateDesc(ApplicationStatus status, String channel);

    List<Application> findByCurrentStatusAndChannelOrderByCompanyNameAsc(ApplicationStatus status, String channel);

    List<Application> findAllByOrderByUpdatedAtDesc();

    List<Application> findAllByOrderByAppliedDateDesc();

    List<Application> findAllByOrderByCompanyNameAsc();
}