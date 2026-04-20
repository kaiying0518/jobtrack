package com.example.jobtrack.service.ai.dto.claude;

import java.util.List;

public class ClaudeResponse {

    private List<ClaudeContent> content;

    public List<ClaudeContent> getContent() {
        return content;
    }

    public void setContent(List<ClaudeContent> content) {
        this.content = content;
    }
}