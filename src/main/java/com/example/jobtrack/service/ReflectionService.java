package com.example.jobtrack.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.ApplicationStatus;

@Service
public class ReflectionService {

    public long countRecent7Days(List<Application> applications) {
    	LocalDate baseDate = LocalDate.now().minusDays(7);
        return applications.stream()
                .filter(app -> app.getAppliedDate() != null)
                .filter(app -> !app.getAppliedDate().isBefore(baseDate))
                .count();
    }

    public long countRecent30Days(List<Application> applications) {
    	LocalDate baseDate = LocalDate.now().minusDays(30);
        return applications.stream()
                .filter(app -> app.getAppliedDate() != null)
                .filter(app -> !app.getAppliedDate().isBefore(baseDate))
                .count();
    }

    public long countTotal(List<Application> applications) {
        return applications.size();
    }

    public List<String> buildSummaryMessages(List<Application> applications) {
        List<String> messages = new ArrayList<>();

        long total = countTotal(applications);
        long recent7 = countRecent7Days(applications);
        long recent30 = countRecent30Days(applications);

        long offerCount = applications.stream()
                .filter(app -> app.getCurrentStatus() == ApplicationStatus.OFFER)
                .count();

        long interviewCount = applications.stream()
                .filter(app -> app.getCurrentStatus() == ApplicationStatus.INTERVIEW_1
                        || app.getCurrentStatus() == ApplicationStatus.INTERVIEW_2
                        || app.getCurrentStatus() == ApplicationStatus.FINAL_INTERVIEW)
                .count();

        long rejectCount = applications.stream()
                .filter(app -> app.getCurrentStatus() == ApplicationStatus.REJECT)
                .count();
        if (total == 0) {
            messages.add("まだ応募はありません。まずは1件作って、流れを始めてみましょう。");
            return messages;
        }

        if (recent7 == 0) {
            messages.add("ここ1週間は少し止まっています。気になる企業を1件だけでも進めると、また動きやすくなります。");
        } else if (recent7 < 3) {
            messages.add("この1週間は少しずつ動けています。今のペースを無理なく続けられそうです。");
        } else {
            messages.add("この1週間はしっかり動けています。この流れを保てるとよさそうです。");
        }

        if (recent30 == 0) {
            messages.add("この1か月はまだ応募の動きがありません。まずは数件ためて、全体の比較ができる状態を作りたいです。");
        } else if (recent30 < 5) {
            messages.add("この1か月の応募数はまだ少なめです。もう少し増やせると、選択肢を持ちやすくなります。");
        } else {
            messages.add("この1か月はある程度応募できています。いい流れが少しずつできてきています。");
        }

        if (interviewCount > 0) {
            messages.add("面接に進んでいる応募があります。応募数を増やすより、準備を丁寧にする価値も高そうです。");
        }

        if (offerCount > 0) {
            messages.add("内定まで進んだ応募があります。うまくいった進め方を振り返っておくと、次にも活かせそうです。");
        }

        if (rejectCount >= 3) {
            messages.add("見送りが続いているときは、応募先の選び方や書類の出し方を少し見直してもよさそうです。");
        }

        return messages;
    }
}