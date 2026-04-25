package com.example.jobtrack.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.jobtrack.service.ReflectionAiService;

@Controller
public class ReflectionAiController {

    private static final Logger log = LoggerFactory.getLogger(ReflectionAiController.class);

    private final ReflectionAiService reflectionAiService;

    public ReflectionAiController(ReflectionAiService reflectionAiService) {
        this.reflectionAiService = reflectionAiService;
    }

    @PostMapping("/reflections/ai-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateAiSummary() {
        log.info("AI summary generation requested");

        try {
            String summary = reflectionAiService.generateSummary();

            log.info("AI summary generated successfully. length={}",
                    summary != null ? summary.length() : 0);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "summary", summary
            ));
        } catch (Exception e) {
            log.error("AI summary generation failed", e);

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