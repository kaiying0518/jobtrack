package com.example.jobtrack.service.ai.dto.claude;

import java.util.List;

public class ClaudeMessage {

    private String role;
    private List<ClaudeContent> content;

    public ClaudeMessage() {
    }

    public ClaudeMessage(String role, List<ClaudeContent> content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<ClaudeContent> getContent() {
        return content;
    }

    public void setContent(List<ClaudeContent> content) {
        this.content = content;
    }
}