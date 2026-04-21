package com.example.jobtrack.service.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobtrack.dto.ChatMessageResponse;
import com.example.jobtrack.dto.ChatRequest;
import com.example.jobtrack.dto.ChatResponse;
import com.example.jobtrack.dto.ChatSessionResponse;
import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Chat;
import com.example.jobtrack.entity.ChatMessage;
import com.example.jobtrack.entity.ChatRole;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.ChatMessageRepository;
import com.example.jobtrack.repository.ChatRepository;
import com.example.jobtrack.service.SettingsService;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

@Service
public class AiChatServiceImpl implements AiChatService {

	private final ChatRepository chatRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final SettingsService settingsService;
	private final Map<AiProviderType, AiClient> clientMap;

	private final BackgroundSummaryService backgroundSummaryService;
	private final QueryPlanningService queryPlanningService;
	private final QueryToolService queryToolService;
	private final QueryResultFormatter queryResultFormatter;
	private final ConversationSummaryService conversationSummaryService;

	public AiChatServiceImpl(ChatRepository chatRepository,
			ChatMessageRepository chatMessageRepository,
			SettingsService settingsService,
			List<AiClient> clients,
			BackgroundSummaryService backgroundSummaryService,
			QueryPlanningService queryPlanningService,
			QueryToolService queryToolService,
			QueryResultFormatter queryResultFormatter,
			ConversationSummaryService conversationSummaryService) {
		this.chatRepository = chatRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.settingsService = settingsService;
		this.clientMap = clients.stream()
				.collect(Collectors.toMap(AiClient::supportedProvider, Function.identity()));
		this.backgroundSummaryService = backgroundSummaryService;
		this.queryPlanningService = queryPlanningService;
		this.queryToolService = queryToolService;
		this.queryResultFormatter = queryResultFormatter;
		this.conversationSummaryService = conversationSummaryService;
	}

	@Override
	@Transactional
	public ChatResponse sendMessage(ChatRequest request) {
		if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
			return ChatResponse.error("メッセージを入力してください。");
		}

		try {
			String userMessage = request.getMessage().trim();
			Chat chat = getOrCreateChat(request, userMessage);

			ChatMessage userChatMessage = new ChatMessage(
					chat,
					ChatRole.USER,
					userMessage,
					LocalDateTime.now());
			chatMessageRepository.save(userChatMessage);

			String aiReply = generateAiReply(chat);

			ChatMessage assistantChatMessage = new ChatMessage(
					chat,
					ChatRole.ASSISTANT,
					aiReply,
					LocalDateTime.now());
			chatMessageRepository.save(assistantChatMessage);

			return ChatResponse.success(chat.getId(), aiReply);

		} catch (Exception e) {
			return ChatResponse.error(resolveErrorMessage(e));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getMessages(Long chatId) {
		return chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId)
				.stream()
				.map(message -> new ChatMessageResponse(
						message.getRole().name(),
						message.getContent(),
						message.getCreatedAt()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChatSessionResponse> getChatSessions() {
		return chatRepository.findAllByOrderByCreatedAtDesc()
				.stream()
				.map(chat -> new ChatSessionResponse(
						chat.getId(),
						chat.getTitle()))
				.toList();
	}

	@Override
	@Transactional
	public void deleteChat(Long chatId) {
		if (chatId == null) {
			throw new IllegalArgumentException("チャットIDが不正です。");
		}

		if (!chatRepository.existsById(chatId)) {
			throw new IllegalArgumentException("チャットが見つかりません。");
		}

		chatMessageRepository.deleteByChatId(chatId);
		chatRepository.deleteById(chatId);
	}

	private String generateAiReply(Chat chat) {
		Settings settings = settingsService.getSettings();
		validateSettings(settings);

		AiClient client = getClient(settings);

		String latestUserMessage = getLatestUserMessage(chat);
		String backgroundSummary = backgroundSummaryService.buildSummary();
		String conversationSummary = buildConversationSummary(chat, settings);

		QueryPlan firstPlan = queryPlanningService.planFirstStep(
				settings,
				latestUserMessage,
				backgroundSummary);

		String queryContext = "";

		if (firstPlan != null && firstPlan.isShouldQuery()) {
			QueryResult firstResult = queryToolService.execute(firstPlan);
			queryContext = queryResultFormatter.format(firstResult);

			QueryPlan secondPlan = queryPlanningService.planSecondStep(
					settings,
					latestUserMessage,
					backgroundSummary,
					firstPlan,
					firstResult);

			if (shouldRunSecondQuery(firstPlan, secondPlan)) {
				QueryResult secondResult = queryToolService.execute(secondPlan);
				String secondContext = queryResultFormatter.format(secondResult);

				if (!secondContext.isBlank()) {
					queryContext = queryContext + "\n\n" + secondContext;
				}
			}
		}

		return answerWithContext(
				client,
				settings,
				chat,
				backgroundSummary,
				conversationSummary,
				queryContext);
	}

	private boolean shouldRunSecondQuery(QueryPlan firstPlan, QueryPlan secondPlan) {
		if (secondPlan == null || !secondPlan.isShouldQuery()) {
			return false;
		}
		if (secondPlan.getTool() == null || secondPlan.getTool() == QueryToolType.NONE) {
			return false;
		}
		return firstPlan == null || secondPlan.getTool() != firstPlan.getTool();
	}

	private String answerWithContext(AiClient client,
			Settings settings,
			Chat chat,
			String backgroundSummary,
			String conversationSummary,
			String queryContext) {
		String systemPrompt = buildAnswerSystemPrompt(
				settings,
				backgroundSummary,
				conversationSummary,
				queryContext);
		List<AiChatMessage> messages = buildChatMessages(chat, settings);
		return client.chat(settings, systemPrompt, messages);
	}

	private AiClient getClient(Settings settings) {
		AiClient client = clientMap.get(settings.getAiProvider());
		if (client == null) {
			throw new IllegalStateException("対応していないAIプロバイダです。");
		}
		return client;
	}

	private void validateSettings(Settings settings) {
		if (settings == null) {
			throw new IllegalStateException("設定が見つかりません。");
		}
		if (!Boolean.TRUE.equals(settings.getAiEnabled())) {
			throw new IllegalStateException("AI機能が無効です。");
		}
		if (settings.getAiProvider() == null) {
			throw new IllegalStateException("AIプロバイダが未設定です。");
		}
		if (settings.getAiApiKey() == null || settings.getAiApiKey().isBlank()) {
			throw new IllegalStateException("AI APIキーが未設定です。");
		}
	}

	private String buildAnswerSystemPrompt(Settings settings,
			String backgroundSummary,
			String conversationSummary,
			String queryContext) {
		String basePrompt = buildBaseSystemPrompt(settings);

		StringBuilder sb = new StringBuilder();
		sb.append(basePrompt).append("\n\n");
		sb.append(backgroundSummary);
		if (conversationSummary != null && !conversationSummary.isBlank()) {
			sb.append("\n\n【会話履歴の要約】\n");
			sb.append(conversationSummary).append("\n");
		}

		if (queryContext != null && !queryContext.isBlank()) {
			sb.append("\n\n").append(queryContext);
		} else {
			sb.append("""

					【リアルタイム検索結果】
					今回の質問では追加検索は実行されていません。
					精確な件数や一覧が必要な場合、見えている背景情報だけで断定しないでください。
					""");
		}

		return sb.toString();
	}

	private String buildBaseSystemPrompt(Settings settings) {
		if (settings.getAiSystemPrompt() != null && !settings.getAiSystemPrompt().isBlank()) {
			return settings.getAiSystemPrompt() + """

					追加ルール:
					- 背景概要は全体理解のための参考情報です
					- リアルタイム検索結果がある場合は、必ずその結果を優先してください
					- 件数・一覧・分布・期間指定の質問については、検索結果なしに断定しすぎないでください
					- 直近のユーザー入力と同じ言語で回答してください
					""";
		}

		return """
				あなたは JobTrack の就職活動支援AIです。
				保存データを参考にしながら、ユーザーが自分の状況を整理できるよう支援してください。
				直近のユーザー入力と同じ言語で回答してください。
				中国語で質問されたら中国語で、日本語で質問されたら日本語で回答してください。
				回答は実用的かつ落ち着いたトーンにしてください。
				データにないことは断定しすぎないでください。

				追加ルール:
				- 背景概要は全体理解のための参考情報です
				- リアルタイム検索結果がある場合は、必ずその結果を優先してください
				- 件数・一覧・分布・期間指定の質問については、検索結果なしに断定しすぎないでください
				- 検索結果が十分であれば、その結果に基づいて整理して回答してください
				""";
	}

	private String getLatestUserMessage(Chat chat) {
		List<ChatMessage> history = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());

		for (int i = history.size() - 1; i >= 0; i--) {
			ChatMessage message = history.get(i);
			if (message.getRole() == ChatRole.USER) {
				return message.getContent();
			}
		}

		return "";
	}

	private List<AiChatMessage> buildChatMessages(Chat chat, Settings settings) {
		List<ChatMessage> history = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());

		boolean useFullChatHistory = Boolean.TRUE.equals(settings.getUseFullChatHistory());

		List<ChatMessage> targetMessages;
		if (useFullChatHistory) {
			targetMessages = history;
		} else {
			int fromIndex = Math.max(0, history.size() - 8);
			targetMessages = history.subList(fromIndex, history.size());
		}

		return targetMessages.stream()
				.map(message -> new AiChatMessage(
						message.getRole() == ChatRole.USER ? "user" : "assistant",
						nullToEmpty(message.getContent())))
				.toList();
	}

	private Chat getOrCreateChat(ChatRequest request, String userMessage) {
		if (request.getChatId() != null) {
			return chatRepository.findById(request.getChatId())
					.orElseThrow(() -> new IllegalArgumentException("チャットが見つかりません。"));
		}

		String title = createTitle(userMessage);
		Chat newChat = new Chat(title, LocalDateTime.now());
		return chatRepository.save(newChat);
	}

	private String createTitle(String message) {
		if (message == null || message.isBlank()) {
			return "新しいチャット";
		}

		String trimmed = message.trim();
		return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20);
	}

	private String resolveErrorMessage(Exception e) {
		if (e.getMessage() == null || e.getMessage().isBlank()) {
			return "AIチャットの処理中にエラーが発生しました。";
		}
		return e.getMessage();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String buildConversationSummary(Chat chat, Settings settings) {
		if (chat == null) {
			return "";
		}

		if (conversationSummaryService.shouldRefreshSummary(chat, settings)) {
			String summary = conversationSummaryService.buildSummary(chat, settings);
			chat.setSummary(summary);
			chatRepository.save(chat);
			return summary;
		}

		return chat.getSummary() != null ? chat.getSummary() : "";
	}
}