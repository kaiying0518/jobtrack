package com.example.jobtrack.service.ai;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.repository.ApplicationRepository;

@Service
public class BackgroundSummaryService {

    private final ApplicationRepository applicationRepository;

    public BackgroundSummaryService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public String buildSummary() {
        List<Application> applications = applicationRepository.findAll();

        if (applications.isEmpty()) {
            return """
                    【背景概要】
                    現在、登録されている応募データはありません。
                    """;
        }

        long appliedCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != null
                        && app.getCurrentStatus().name().equals("APPLIED"))
                .count();

        long webTestCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != null
                        && app.getCurrentStatus().name().equals("WEB_TEST"))
                .count();

        long interviewCount = applications.stream()
                .filter(app -> app.getCurrentStatus() != null
                        && (app.getCurrentStatus().name().equals("INTERVIEW_1")
                        || app.getCurrentStatus().name().equals("INTERVIEW_2")
                        || app.getCurrentStatus().name().equals("FINAL_INTERVIEW")))
                .count();

        String companyNames = applications.stream()
                .map(Application::getCompanyName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.joining("、"));

        String positions = applications.stream()
                .map(Application::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.joining("、"));

        return """
                【背景概要】
                - 応募件数: %d件
                - 主な会社名: %s
                - 主な職種: %s
                - 応募済み: %d件
                - WEBテスト: %d件
                - 面接中: %d件

                背景概要は全体理解のための参考情報です。
                精確な件数・対象一覧・期間指定の質問については、リアルタイム検索結果を優先してください。
                """.formatted(
                applications.size(),
                companyNames.isBlank() ? "-" : companyNames,
                positions.isBlank() ? "-" : positions,
                appliedCount,
                webTestCount,
                interviewCount
        );
    }
}