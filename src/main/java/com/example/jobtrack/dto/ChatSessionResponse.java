package com.example.jobtrack.dto;

public class ChatSessionResponse {

    private Long chatId;
    private String title;

    public ChatSessionResponse() {
    }

    public ChatSessionResponse(Long chatId, String title) {
        this.chatId = chatId;
        this.title = title;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}