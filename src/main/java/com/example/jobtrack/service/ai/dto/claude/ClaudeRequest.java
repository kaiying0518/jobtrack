package com.example.jobtrack.service.ai.dto.claude;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClaudeRequest {

    private String model;
    private String system;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<ClaudeMessage> messages;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public List<ClaudeMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ClaudeMessage> messages) {
        this.messages = messages;
    }
}