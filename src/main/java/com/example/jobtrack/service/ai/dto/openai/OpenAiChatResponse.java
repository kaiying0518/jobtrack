package com.example.jobtrack.service.ai.dto.openai;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAiChatResponse {

    private List<OpenAiChoice> choices;
}