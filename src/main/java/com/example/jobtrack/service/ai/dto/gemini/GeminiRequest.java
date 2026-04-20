package com.example.jobtrack.service.ai.dto.gemini;

import java.util.List;

public class GeminiRequest {
    private List<GeminiContent> contents;

    public List<GeminiContent> getContents() {
        return contents;
    }

    public void setContents(List<GeminiContent> contents) {
        this.contents = contents;
    }
}