package com.example.jobtrack.dto;

public class ChatRequest {

    private Long chatId;
    private String message;

    public ChatRequest() {
    }

    public Long getChatId() {
        return chatId;
    }

    public String getMessage() {
        return message;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}