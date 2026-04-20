package com.example.jobtrack.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Map<String, Object>> generateAiSummary() {
        try {
            String summary = reflectionAiService.generateSummary();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "summary", summary
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", resolveErrorMessage(e)
            ));
        }
    }

    private String resolveErrorMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return "AIサマリーの生成に失敗しました。";
        }
        return e.getMessage();
    }
}