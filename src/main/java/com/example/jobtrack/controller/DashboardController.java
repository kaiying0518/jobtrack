package com.example.jobtrack.controller;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.service.ApplicationService;

@Controller
public class DashboardController {

    private final ApplicationService applicationService;

    public DashboardController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping({"/", "/dashboard"})
    public String showDashboard(Model model) {
        List<Application> applications = applicationService.findAll();

        long totalCount = applications.size();

        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        long weeklyApplyCount = applications.stream()
                .filter(app -> app.getAppliedDate() != null)
                .filter(app -> !app.getAppliedDate().isBefore(sevenDaysAgo))
                .count();

        long webTestCount = countByStatus(applications, ApplicationStatus.WEB_TEST);
        long documentPassCount = countByStatus(applications, ApplicationStatus.DOCUMENT_PASS);

        long interview1Count = countByStatus(applications, ApplicationStatus.INTERVIEW_1);
        long interview2Count = countByStatus(applications, ApplicationStatus.INTERVIEW_2);
        long finalInterviewCount = countByStatus(applications, ApplicationStatus.FINAL_INTERVIEW);
        long interviewCount = interview1Count + interview2Count + finalInterviewCount;

        long offerCount = countByStatus(applications, ApplicationStatus.OFFER);
        long rejectCount = countByStatus(applications, ApplicationStatus.REJECT);
        long withdrawnCount = countByStatus(applications, ApplicationStatus.WITHDRAWN);

        String documentPassRate = totalCount == 0
                ? "0%"
                : Math.round((double) documentPassCount * 100 / totalCount) + "%";

        String interviewBreakdown =
                "一次 " + interview1Count +
                " / 二次 " + interview2Count +
                " / 最終 " + finalInterviewCount;

        List<Application> recentApplications = applications.stream()
                .sorted(Comparator.comparing(
                        Application::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(5)
                .collect(Collectors.toList());

        List<String> reflectionMessages = buildReflectionMessages(
                applications,
                weeklyApplyCount,
                webTestCount,
                documentPassCount,
                interviewCount
        );

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("weeklyApplyCount", weeklyApplyCount);
        model.addAttribute("webTestCount", webTestCount);
        model.addAttribute("documentPassCount", documentPassCount);
        model.addAttribute("documentPassRate", documentPassRate);
        model.addAttribute("interviewCount", interviewCount);
        model.addAttribute("interviewBreakdown", interviewBreakdown);
        model.addAttribute("offerCount", offerCount);
        model.addAttribute("rejectCount", rejectCount);
        model.addAttribute("withdrawnCount", withdrawnCount);
        model.addAttribute("recentApplications", recentApplications);
        model.addAttribute("reflectionMessages", reflectionMessages);

        return "dashboard";
    }

    private long countByStatus(List<Application> applications, ApplicationStatus status) {
        return applications.stream()
                .filter(app -> app.getCurrentStatus() == status)
                .count();
    }

    private List<String> buildReflectionMessages(List<Application> applications,
                                                 long weeklyApplyCount,
                                                 long webTestCount,
                                                 long documentPassCount,
                                                 long interviewCount) {
        List<String> messages = new java.util.ArrayList<>();

        if (applications.isEmpty()) {
            messages.add("まだ応募データがありません。まずは1件追加してみましょう。");
            return messages;
        }

        if (weeklyApplyCount == 0) {
            messages.add("直近7日間の応募がありません。気になる求人を1件だけでも追加してみましょう。");
        } else {
            messages.add("今週の応募数は " + weeklyApplyCount + " 件です。無理のないペースで継続できています。");
        }

        if (webTestCount > 0) {
            messages.add("WEBテスト待ちが " + webTestCount + " 件あります。受験期限を確認しておくと安心です。");
        }

        if (documentPassCount > 0 || interviewCount > 0) {
            messages.add("選考が進んでいる応募があります。次の予定と準備メモを整理しておきましょう。");
        }

        long noNextActionCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != ApplicationStatus.OFFER)
                .filter(app -> app.getCurrentStatus() != ApplicationStatus.REJECT)
                .filter(app -> app.getCurrentStatus() != ApplicationStatus.WITHDRAWN)
                .filter(app -> app.getNextActionAt() == null)
                .count();

        if (noNextActionCount > 0) {
            messages.add("次の予定が未設定の応募が " + noNextActionCount + " 件あります。必要なら日付を入れておくと見返しやすいです。");
        }

        return messages;
    }
}