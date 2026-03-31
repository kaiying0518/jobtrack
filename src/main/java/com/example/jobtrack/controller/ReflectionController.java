package com.example.jobtrack.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.service.ApplicationService;
import com.example.jobtrack.service.ReflectionService;

@Controller
@RequestMapping("/reflections")
public class ReflectionController {

    private final ApplicationService applicationService;
    private final ReflectionService reflectionService;

    public ReflectionController(ApplicationService applicationService,
                                ReflectionService reflectionService) {
        this.applicationService = applicationService;
        this.reflectionService = reflectionService;
    }

    @GetMapping
    public String summary(Model model) {
        List<Application> applications = applicationService.findAll();

        model.addAttribute("recent7Count", reflectionService.countRecent7Days(applications));
        model.addAttribute("recent30Count", reflectionService.countRecent30Days(applications));
        model.addAttribute("totalCount", reflectionService.countTotal(applications));
        model.addAttribute("summaryMessages", reflectionService.buildSummaryMessages(applications));

        return "reflections/summary";
    }
}