package com.example.jobtrack.controller;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.jobtrack.entity.ActivityActionType;
import com.example.jobtrack.service.ActivityLogService;

@Controller
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityLogService activityLogService;

    public ActivityController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) ActivityActionType actionType,
                       @RequestParam(required = false) LocalDate fromDate,
                       @RequestParam(required = false) LocalDate toDate,
                       Model model) {

        model.addAttribute("activityLogs",
                activityLogService.search(keyword, actionType, fromDate, toDate));
        model.addAttribute("keyword", keyword);
        model.addAttribute("actionType", actionType);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        return "activities/list";
    }
}