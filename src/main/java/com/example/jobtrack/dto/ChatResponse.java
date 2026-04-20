package com.example.jobtrack.dto;

public class ChatResponse {

    private boolean success;
    private Long chatId;
    private String reply;
    private String error;

    public ChatResponse() {
    }

    public ChatResponse(boolean success, Long chatId, String reply, String error) {
        this.success = success;
        this.chatId = chatId;
        this.reply = reply;
        this.error = error;
    }

    public static ChatResponse success(Long chatId, String reply) {
        return new ChatResponse(true, chatId, reply, null);
    }

    public static ChatResponse error(String error) {
        return new ChatResponse(false, null, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getReply() {
        return reply;
    }

    public String getError() {
        return error;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public void setError(String error) {
        this.error = error;
    }
}