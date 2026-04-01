package com.example.jobtrack.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;
import com.example.jobtrack.repository.ApplicationRepository;

@Service
public class ReflectionAiService {

    private final ApplicationRepository applicationRepository;

    public ReflectionAiService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public String generateSummary() {
        List<Application> applications = applicationRepository.findAll();

        long total = applications.size();
        long applied = countByStatus(applications, ApplicationStatus.APPLIED);
        long webTest = countByStatus(applications, ApplicationStatus.WEB_TEST);
        long interviewing = applications.stream()
                .filter(a -> a.getCurrentStatus() == ApplicationStatus.INTERVIEW_1
                        || a.getCurrentStatus() == ApplicationStatus.INTERVIEW_2
                        || a.getCurrentStatus() == ApplicationStatus.FINAL_INTERVIEW)
                .count();
        long offer = countByStatus(applications, ApplicationStatus.OFFER);
        long inactive = applications.stream()
                .filter(this::isInactive)
                .count();

        return """
                現在の応募件数は %d 件です。
                応募済みは %d 件、WEBテスト中は %d 件、面接中は %d 件、内定は %d 件です。
                2週間以上動きのない応募は %d 件あります。
                まずは直近対応が必要な応募先と、停滞している応募先を見直すのがおすすめです。
                """.formatted(total, applied, webTest, interviewing, offer, inactive);
    }

    private long countByStatus(List<Application> applications, ApplicationStatus status) {
        return applications.stream()
                .filter(a -> a.getCurrentStatus() == status)
                .count();
    }

    private boolean isInactive(Application application) {
        if (application.getCurrentStatus() == ApplicationStatus.OFFER
                || application.getCurrentStatus() == ApplicationStatus.REJECT
                || application.getCurrentStatus() == ApplicationStatus.WITHDRAWN) {
            return false;
        }

        if (application.getUpdatedAt() == null) {
            return false;
        }

        return application.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(14));
    }
}