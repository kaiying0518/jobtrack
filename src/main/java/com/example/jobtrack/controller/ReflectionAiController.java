package com.example.jobtrack.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.jobtrack.service.ReflectionAiService;

@Controller
public class ReflectionAiController {

    private final ReflectionAiService reflectionAiService;

    public ReflectionAiController(ReflectionAiService reflectionAiService) {
        this.reflectionAiService = reflectionAiService;
    }

    @PostMapping("/reflections/ai-summary")
    @ResponseBody
    public Map<String, String> generateAiSummary() {
        return Map.of("summary", reflectionAiService.generateSummary());
    }
}