package com.example.jobtrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.jobtrack.entity.Chat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findAllByOrderByCreatedAtDesc();
}