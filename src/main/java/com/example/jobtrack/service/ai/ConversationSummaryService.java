package com.example.jobtrack.service.ai;

import com.example.jobtrack.entity.Chat;
import com.example.jobtrack.entity.Settings;

public interface ConversationSummaryService {

    String buildSummary(Chat chat, Settings settings);

    boolean shouldRefreshSummary(Chat chat, Settings settings);
}