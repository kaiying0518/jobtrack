package com.example.jobtrack.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobtrack.dto.ChatMessageResponse;
import com.example.jobtrack.dto.ChatRequest;
import com.example.jobtrack.dto.ChatResponse;
import com.example.jobtrack.dto.ChatSessionResponse;
import com.example.jobtrack.service.ai.AiChatService;

@RestController
@RequestMapping("/api/chat")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        Long chatId = request != null ? request.getChatId() : null;
        int messageLength = request != null && request.getMessage() != null
                ? request.getMessage().length()
                : 0;

        log.info("AI chat send requested. chatId={}, messageLength={}", chatId, messageLength);

        try {
            ChatResponse response = aiChatService.sendMessage(request);

            log.info("AI chat send completed. chatId={}, success={}",
                    response != null ? response.getChatId() : null,
                    response != null && response.isSuccess());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI chat send failed. chatId={}", chatId, e);
            throw e;
        }
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long chatId) {
        log.info("AI chat messages requested. chatId={}", chatId);

        List<ChatMessageResponse> messages = aiChatService.getMessages(chatId);

        log.info("AI chat messages loaded. chatId={}, count={}",
                chatId,
                messages != null ? messages.size() : 0);

        return ResponseEntity.ok(messages);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getChatSessions() {
        log.info("AI chat sessions requested");

        List<ChatSessionResponse> sessions = aiChatService.getChatSessions();

        log.info("AI chat sessions loaded. count={}",
                sessions != null ? sessions.size() : 0);

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long chatId) {
        log.info("AI chat delete requested. chatId={}", chatId);

        aiChatService.deleteChat(chatId);

        log.info("AI chat deleted. chatId={}", chatId);

        return ResponseEntity.noContent().build();
    }
}