package com.example.jobtrack.service.ai;

import java.util.List;

import com.example.jobtrack.dto.ChatMessageResponse;
import com.example.jobtrack.dto.ChatRequest;
import com.example.jobtrack.dto.ChatResponse;
import com.example.jobtrack.dto.ChatSessionResponse;

public interface AiChatService {

    ChatResponse sendMessage(ChatRequest request);

    List<ChatMessageResponse> getMessages(Long chatId);

    List<ChatSessionResponse> getChatSessions();

    void deleteChat(Long chatId);
}