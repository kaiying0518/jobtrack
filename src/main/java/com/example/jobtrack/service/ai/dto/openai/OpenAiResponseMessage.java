package com.example.jobtrack.service.ai.dto.openai;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAiResponseMessage {

    private String role;
    private String content;
}