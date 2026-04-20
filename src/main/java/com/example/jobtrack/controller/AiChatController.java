package com.example.jobtrack.controller;

import java.util.List;

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

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        ChatResponse response = aiChatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long chatId) {
        List<ChatMessageResponse> messages = aiChatService.getMessages(chatId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getChatSessions() {
        List<ChatSessionResponse> sessions = aiChatService.getChatSessions();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long chatId) {
        aiChatService.deleteChat(chatId);
        return ResponseEntity.noContent().build();
    }
}