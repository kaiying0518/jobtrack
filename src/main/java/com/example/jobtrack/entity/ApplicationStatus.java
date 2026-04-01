package com.example.jobtrack.entity;

public enum ApplicationStatus {
    APPLIED("応募済み"),
    WEB_TEST("WEBテスト"),
    DOCUMENT_PASS("書類通過"),
    INTERVIEW_1("一次面接"),
    INTERVIEW_2("二次面接"),
    FINAL_INTERVIEW("最終面接"),
    OFFER("内定"),
    REJECT("不採用"),
    WITHDRAWN("辞退");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}