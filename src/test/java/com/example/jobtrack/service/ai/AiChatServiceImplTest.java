package com.example.jobtrack.service.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.jobtrack.dto.ChatRequest;
import com.example.jobtrack.dto.ChatResponse;
import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Chat;
import com.example.jobtrack.entity.ChatMessage;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.ApplicationRepository;
import com.example.jobtrack.repository.ChatMessageRepository;
import com.example.jobtrack.repository.ChatRepository;
import com.example.jobtrack.service.SettingsService;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private BackgroundSummaryService backgroundSummaryService;

    @Mock
    private QueryPlanningService queryPlanningService;

    @Mock
    private QueryToolService queryToolService;

    @Mock
    private QueryResultFormatter queryResultFormatter;

    @Mock
    private ConversationSummaryService conversationSummaryService;

    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private ApplicationRepository applicationRepository;

    private AiChatServiceImpl aiChatService;

    @BeforeEach
    void setUp() {
        when(openAiClient.supportedProvider()).thenReturn(AiProviderType.OPENAI);

        aiChatService = new AiChatServiceImpl(
                chatRepository,
                chatMessageRepository,
                applicationRepository,
                settingsService,
                List.<AiClient>of(openAiClient),
                backgroundSummaryService,
                queryPlanningService,
                queryToolService,
                queryResultFormatter,
                conversationSummaryService
        );

        Settings settings = new Settings();
        settings.setAiEnabled(true);
        settings.setAiProvider(AiProviderType.OPENAI);
        settings.setAiApiKey("test-key");
        settings.setUseFullChatHistory(false);

        when(settingsService.getSettings()).thenReturn(settings);
        when(backgroundSummaryService.buildSummary()).thenReturn("BACKGROUND");
        when(applicationRepository.findAll()).thenReturn(List.of());
        

        when(openAiClient.chat(any(Settings.class), anyString(), anyList()))
                .thenReturn("FAKE_AI_REPLY");
    }

    @Test
    void sendMessage_shouldDirectAnswer_whenPlannerSaysNoQuery() {
        ChatRequest request = new ChatRequest();
        request.setMessage("帮我总结一下最近情况");

        Chat savedChat = new Chat("帮我总结一下最近情况", LocalDateTime.now());
        savedChat.setId(1L);

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);

        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            history.add(msg);
            return msg;
        });
        when(chatMessageRepository.findByChatIdOrderByCreatedAtAsc(1L)).thenAnswer(invocation -> new ArrayList<>(history));

        QueryPlan noQueryPlan = new QueryPlan(false, QueryToolType.NONE, null, "test no query");
        when(queryPlanningService.planFirstStep(any(Settings.class), anyString(), anyString()))
                .thenReturn(noQueryPlan);

        ChatResponse response = aiChatService.sendMessage(request);

        assertNotNull(response);
        assertTrue(response.isSuccess(), "error = " + response.getError());
        assertEquals("FAKE_AI_REPLY", response.getReply());
        assertEquals(1L, response.getChatId());

        verify(queryToolService, never()).execute(any());
        verify(openAiClient).chat(any(Settings.class), anyString(), anyList());
    }

    @Test
    void sendMessage_shouldRunSingleQueryFlow() {
        ChatRequest request = new ChatRequest();
        request.setMessage("最近14天投了多少家公司");

        Chat savedChat = new Chat("最近14天投了多少家公司", LocalDateTime.now());
        savedChat.setId(2L);

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);

        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            history.add(msg);
            return msg;
        });
        when(chatMessageRepository.findByChatIdOrderByCreatedAtAsc(2L)).thenAnswer(invocation -> new ArrayList<>(history));

        QueryPlan firstPlan = new QueryPlan(true, QueryToolType.COUNT_BY_PERIOD, 14, "test");
        QueryResult firstResult = new QueryResult();
        firstResult.setTool(QueryToolType.COUNT_BY_PERIOD);
        firstResult.setSummary("最近14日間の応募件数は 2 件です。");
        firstResult.setRows(List.of());

        when(queryPlanningService.planFirstStep(any(Settings.class), anyString(), anyString()))
                .thenReturn(firstPlan);
        when(queryToolService.execute(firstPlan)).thenReturn(firstResult);
        when(queryResultFormatter.format(firstResult)).thenReturn("QUERY_CONTEXT");
        when(queryPlanningService.planSecondStep(any(), any(), any(), any(), any()))
                .thenReturn(new QueryPlan(false, QueryToolType.NONE, null, "no second"));

        ChatResponse response = aiChatService.sendMessage(request);

        assertTrue(response.isSuccess(), "error = " + response.getError());
        assertEquals("FAKE_AI_REPLY", response.getReply());

        verify(queryToolService, times(1)).execute(firstPlan);
        verify(openAiClient).chat(any(Settings.class), anyString(), anyList());
    }

    @Test
    void sendMessage_shouldRunTwoStepLoopFlow() {
        ChatRequest request = new ChatRequest();
        request.setMessage("我最近主要卡在哪个阶段");

        Chat savedChat = new Chat("我最近主要卡在哪个阶段", LocalDateTime.now());
        savedChat.setId(3L);

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);

        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            history.add(msg);
            return msg;
        });
        when(chatMessageRepository.findByChatIdOrderByCreatedAtAsc(3L)).thenAnswer(invocation -> new ArrayList<>(history));

        QueryPlan firstPlan = new QueryPlan(true, QueryToolType.COUNT_BY_STATUS, null, "first");
        QueryPlan secondPlan = new QueryPlan(true, QueryToolType.STALE_APPLICATIONS, 14, "second");

        QueryResult firstResult = new QueryResult();
        firstResult.setTool(QueryToolType.COUNT_BY_STATUS);
        firstResult.setSummary("status summary");
        firstResult.setRows(List.of());

        QueryResult secondResult = new QueryResult();
        secondResult.setTool(QueryToolType.STALE_APPLICATIONS);
        secondResult.setSummary("stale summary");
        secondResult.setRows(List.of());

        when(queryPlanningService.planFirstStep(any(Settings.class), anyString(), anyString()))
                .thenReturn(firstPlan);
        when(queryToolService.execute(firstPlan)).thenReturn(firstResult);
        when(queryResultFormatter.format(firstResult)).thenReturn("FIRST_CONTEXT");

        when(queryPlanningService.planSecondStep(any(), any(), any(), eq(firstPlan), eq(firstResult)))
                .thenReturn(secondPlan);
        when(queryToolService.execute(secondPlan)).thenReturn(secondResult);
        when(queryResultFormatter.format(secondResult)).thenReturn("SECOND_CONTEXT");

        ChatResponse response = aiChatService.sendMessage(request);

        assertTrue(response.isSuccess(), "error = " + response.getError());
        assertEquals("FAKE_AI_REPLY", response.getReply());

        verify(queryToolService, times(1)).execute(firstPlan);
        verify(queryToolService, times(1)).execute(secondPlan);
    }

    @Test
    void shouldAppendMessages_whenSameChatContinues() {
        Long chatId = 10L;

        Chat existingChat = new Chat("第一句", LocalDateTime.now());
        existingChat.setId(chatId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(existingChat));

        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            history.add(msg);
            return msg;
        });
        when(chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenAnswer(invocation -> new ArrayList<>(history));

        when(queryPlanningService.planFirstStep(any(Settings.class), anyString(), anyString()))
                .thenReturn(new QueryPlan(false, QueryToolType.NONE, null, "no query"));

        ChatRequest request1 = new ChatRequest();
        request1.setChatId(chatId);
        request1.setMessage("第一句");

        ChatResponse response1 = aiChatService.sendMessage(request1);
        assertTrue(response1.isSuccess(), "error = " + response1.getError());

        ChatRequest request2 = new ChatRequest();
        request2.setChatId(chatId);
        request2.setMessage("第二句");

        ChatResponse response2 = aiChatService.sendMessage(request2);
        assertTrue(response2.isSuccess(), "error = " + response2.getError());

        assertEquals(chatId, response1.getChatId());
        assertEquals(chatId, response2.getChatId());
        assertEquals(4, history.size());
    }

    @Test
    void shouldGenerateSummary_whenConversationBecomesLong() {
        Long chatId = 20L;

        Chat existingChat = new Chat("第0轮对话", LocalDateTime.now());
        existingChat.setId(chatId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(existingChat));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            history.add(msg);
            return msg;
        });
        when(chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenAnswer(invocation -> new ArrayList<>(history));

        when(queryPlanningService.planFirstStep(any(Settings.class), anyString(), anyString()))
                .thenReturn(new QueryPlan(false, QueryToolType.NONE, null, "no query"));

        when(conversationSummaryService.shouldRefreshSummary(any(Chat.class), any(Settings.class)))
                .thenReturn(false, false, false, false, false, true, false);
        when(conversationSummaryService.buildSummary(any(Chat.class), any(Settings.class)))
                .thenReturn("会話要約: ユーザーは就活状況の整理を継続的に求めている。");

        for (int i = 0; i < 7; i++) {
            ChatRequest request = new ChatRequest();
            request.setChatId(chatId);
            request.setMessage("第" + i + "轮对话");

            ChatResponse response = aiChatService.sendMessage(request);
            assertTrue(response.isSuccess(), "error = " + response.getError());
        }

        ArgumentCaptor<Chat> captor = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository, atLeastOnce()).save(captor.capture());

        boolean summarySaved = captor.getAllValues().stream()
                .anyMatch(chat -> chat.getSummary() != null && chat.getSummary().contains("会話要約"));

        assertTrue(summarySaved);
    }
}