
package com.example.jobtrack.service.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobtrack.dto.ChatMessageResponse;
import com.example.jobtrack.dto.ChatRequest;
import com.example.jobtrack.dto.ChatResponse;
import com.example.jobtrack.dto.ChatSessionResponse;
import com.example.jobtrack.entity.AiProviderType;
import com.example.jobtrack.entity.Application;
import com.example.jobtrack.entity.Chat;
import com.example.jobtrack.entity.ChatMessage;
import com.example.jobtrack.entity.ChatRole;
import com.example.jobtrack.entity.Settings;
import com.example.jobtrack.repository.ApplicationRepository;
import com.example.jobtrack.repository.ChatMessageRepository;
import com.example.jobtrack.repository.ChatRepository;
import com.example.jobtrack.service.SettingsService;
import com.example.jobtrack.service.ai.dto.QueryPlan;
import com.example.jobtrack.service.ai.dto.QueryResult;

@Service
public class AiChatServiceImpl implements AiChatService {

	private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

	private final ChatRepository chatRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ApplicationRepository applicationRepository;
	private final SettingsService settingsService;
	private final Map<AiProviderType, AiClient> clientMap;

	private final BackgroundSummaryService backgroundSummaryService;
	private final QueryPlanningService queryPlanningService;
	private final QueryToolService queryToolService;
	private final QueryResultFormatter queryResultFormatter;
	private final ConversationSummaryService conversationSummaryService;

	public AiChatServiceImpl(ChatRepository chatRepository,
			ChatMessageRepository chatMessageRepository,
			ApplicationRepository applicationRepository,
			SettingsService settingsService,
			List<AiClient> clients,
			BackgroundSummaryService backgroundSummaryService,
			QueryPlanningService queryPlanningService,
			QueryToolService queryToolService,
			QueryResultFormatter queryResultFormatter,
			ConversationSummaryService conversationSummaryService) {
		this.chatRepository = chatRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.applicationRepository = applicationRepository;
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
			log.warn("AI chat request rejected. message was empty.");
			return ChatResponse.error("メッセージを入力してください。");
		}

		String userMessage = request.getMessage().trim();
		Long requestChatId = request.getChatId();

		log.info("AI chat processing started. chatId={}, messageLength={}",
				requestChatId,
				userMessage.length());

		try {
			Chat chat = getOrCreateChat(request, userMessage);

			log.info("Chat resolved. chatId={}", chat.getId());

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

			log.info("AI chat processing completed. chatId={}, replyLength={}",
					chat.getId(),
					aiReply != null ? aiReply.length() : 0);

			return ChatResponse.success(chat.getId(), aiReply);

		} catch (Exception e) {
			log.error("AI chat processing failed. chatId={}", requestChatId, e);
			return ChatResponse.error(resolveErrorMessage(e));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getMessages(Long chatId) {
		log.info("Loading chat messages. chatId={}", chatId);

		List<ChatMessageResponse> messages = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId)
				.stream()
				.map(message -> new ChatMessageResponse(
						message.getRole().name(),
						message.getContent(),
						message.getCreatedAt()))
				.toList();

		log.info("Chat messages loaded. chatId={}, count={}", chatId, messages.size());

		return messages;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChatSessionResponse> getChatSessions() {
		log.info("Loading chat sessions");

		List<ChatSessionResponse> sessions = chatRepository.findAllByOrderByCreatedAtDesc()
				.stream()
				.map(chat -> new ChatSessionResponse(
						chat.getId(),
						chat.getTitle()))
				.toList();

		log.info("Chat sessions loaded. count={}", sessions.size());

		return sessions;
	}

	@Override
	@Transactional
	public void deleteChat(Long chatId) {
		log.info("Deleting chat. chatId={}", chatId);

		if (chatId == null) {
			log.warn("Delete chat rejected. chatId was null.");
			throw new IllegalArgumentException("チャットIDが不正です。");
		}

		if (!chatRepository.existsById(chatId)) {
			log.warn("Delete chat rejected. chat not found. chatId={}", chatId);
			throw new IllegalArgumentException("チャットが見つかりません。");
		}

		chatMessageRepository.deleteByChatId(chatId);
		chatRepository.deleteById(chatId);

		log.info("Chat deleted. chatId={}", chatId);
	}

	private String generateAiReply(Chat chat) {
		log.info("Generating AI reply. chatId={}", chat.getId());

		Settings settings = settingsService.getSettings();
		validateSettings(settings);

		log.info("AI settings loaded. provider={}, model={}",
				settings.getAiProvider(),
				settings.getAiModel());

		AiClient client = getClient(settings);

		log.info("AI client resolved. provider={}", settings.getAiProvider());

		String latestUserMessage = getLatestUserMessage(chat);
		String backgroundSummary = backgroundSummaryService.buildSummary();
		String conversationSummary = buildConversationSummary(chat, settings);
		String applicationFactsContext = buildApplicationFactsContext();

		log.info(
				"AI context prepared. chatId={}, backgroundLength={}, conversationSummaryLength={}, applicationFactsLength={}",
				chat.getId(),
				backgroundSummary != null ? backgroundSummary.length() : 0,
				conversationSummary != null ? conversationSummary.length() : 0,
				applicationFactsContext != null ? applicationFactsContext.length() : 0);

		log.info("Planning query for message. chatId={}", chat.getId());

		QueryPlan firstPlan = queryPlanningService.planFirstStep(
				settings,
				latestUserMessage,
				backgroundSummary);

		String queryContext = "";

		if (firstPlan != null && firstPlan.isShouldQuery()) {
			log.info("Executing first query plan. tool={}", firstPlan.getTool());

			QueryResult firstResult = queryToolService.execute(firstPlan);
			queryContext = queryResultFormatter.format(firstResult);

			QueryPlan secondPlan = queryPlanningService.planSecondStep(
					settings,
					latestUserMessage,
					backgroundSummary,
					firstPlan,
					firstResult);

			if (shouldRunSecondQuery(firstPlan, secondPlan)) {
				log.info("Executing second query plan. tool={}", secondPlan.getTool());

				QueryResult secondResult = queryToolService.execute(secondPlan);
				String secondContext = queryResultFormatter.format(secondResult);

				if (!secondContext.isBlank()) {
					queryContext = queryContext + "\n\n" + secondContext;
				}
			}
		} else {
			log.info("No query plan executed. chatId={}", chat.getId());
		}

		log.info("Calling AI with context. chatId={}, queryContextLength={}",
				chat.getId(),
				queryContext != null ? queryContext.length() : 0);

		return answerWithContext(
				client,
				settings,
				chat,
				backgroundSummary,
				conversationSummary,
				applicationFactsContext,
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
			String applicationFactsContext,
			String queryContext) {
		String systemPrompt = buildAnswerSystemPrompt(
				settings,
				backgroundSummary,
				conversationSummary,
				applicationFactsContext,
				queryContext);

		List<AiChatMessage> messages = buildChatMessages(chat, settings);

		log.info("AI answer request prepared. chatId={}, messageCount={}, systemPromptLength={}",
				chat.getId(),
				messages.size(),
				systemPrompt.length());

		return client.chat(settings, systemPrompt, messages);
	}

	private AiClient getClient(Settings settings) {
		AiClient client = clientMap.get(settings.getAiProvider());
		if (client == null) {
			log.warn("Unsupported AI provider. provider={}", settings.getAiProvider());
			throw new IllegalStateException("対応していないAIプロバイダです。");
		}
		return client;
	}

	private void validateSettings(Settings settings) {
		if (settings == null) {
			log.warn("AI settings validation failed. settings not found.");
			throw new IllegalStateException("設定が見つかりません。");
		}
		if (!Boolean.TRUE.equals(settings.getAiEnabled())) {
			log.warn("AI settings validation failed. AI disabled.");
			throw new IllegalStateException("AI機能が無効です。");
		}
		if (settings.getAiProvider() == null) {
			log.warn("AI settings validation failed. provider missing.");
			throw new IllegalStateException("AIプロバイダが未設定です。");
		}
		if (settings.getAiApiKey() == null || settings.getAiApiKey().isBlank()) {
			log.warn("AI settings validation failed. API key missing. provider={}", settings.getAiProvider());
			throw new IllegalStateException("AI APIキーが未設定です。");
		}
	}

	private String buildAnswerSystemPrompt(Settings settings,
			String backgroundSummary,
			String conversationSummary,
			String applicationFactsContext,
			String queryContext) {
		String basePrompt = buildBaseSystemPrompt(settings);

		StringBuilder sb = new StringBuilder();
		sb.append(basePrompt).append("\n\n");

		sb.append("""
				【回答方針】
				ユーザーの質問に合わせて、自然に回答してください。

				- 事実確認が必要な場合は、JobTrack の保存データ・検索結果を根拠にしてください
				- 助言が必要な場合は、保存データを前提にしつつ、次の行動を具体的に提案してください
				- 毎回「データ上わかること」「AIとしての提案」のように固定形式で分ける必要はありません
				- 直前に説明した応募一覧や件数は、ユーザーが再確認を求めない限り繰り返さないでください
				- ただし、会社名・応募件数・ステータス・応募日・次の予定などの事実は推測で作らないでください
				""");

		if (backgroundSummary != null && !backgroundSummary.isBlank()) {
			sb.append("\n\n【背景概要】\n");
			sb.append(backgroundSummary).append("\n");
		}

		if (conversationSummary != null && !conversationSummary.isBlank()) {
			sb.append("\n\n【会話履歴の要約】\n");
			sb.append(conversationSummary).append("\n");
		}

		if (applicationFactsContext != null && !applicationFactsContext.isBlank()) {
			sb.append("\n\n【JobTrack保存データ】\n");
			sb.append(applicationFactsContext).append("\n");
		}

		if (queryContext != null && !queryContext.isBlank()) {
			sb.append("\n\n【リアルタイム検索結果】\n");
			sb.append(queryContext).append("\n");
		} else {
			sb.append("""

					【リアルタイム検索結果】
					今回の質問では追加検索結果はありません。
					ただし、上記の JobTrack保存データ は利用できます。
					""");
		}

		return sb.toString();
	}

	private String buildBaseSystemPrompt(Settings settings) {
	    String commonRule = """

	            追加ルール:
	            - 保存データに基づく事実は正確に扱ってください
	            - 会社名、応募件数、ステータス、応募日、次の予定などは推測で作らないでください
	            - データに存在しない会社名・件数・日付を作らないでください
	            - ただし、回答形式は固定しすぎず、ユーザーの質問に合わせて自然に答えてください
	            - 必要な場合だけ応募データを整理して示してください
	            - 直前に説明した内容は、ユーザーが求めない限り繰り返さないでください
	            - 助言を求められた場合は、事実を短く前提として触れたうえで、次の行動・理由・優先順位を自然に提案してください
	            - 改善提案・次にやるべきこと・考察は、保存データを踏まえて自由に提案してかまいません
	            - 回答は少し柔らかく、相談に乗るようなトーンにしてください
	            - 直近のユーザー入力と同じ言語で回答してください
	            """;

	    if (settings.getAiSystemPrompt() != null && !settings.getAiSystemPrompt().isBlank()) {
	        return settings.getAiSystemPrompt() + commonRule;
	    }

	    return """
	            あなたは JobTrack の就職活動支援AIです。
	            ユーザーの応募データをもとに、状況整理と次の行動提案を支援してください。
	            中国語で質問されたら中国語で、日本語で質問されたら日本語で回答してください。
	            回答は実用的で、落ち着いた相談相手のようなトーンにしてください。
	            必要に応じて理由や具体例も添えてください。
	            """ + commonRule;
	}

	private String buildApplicationFactsContext() {
		List<Application> applications = applicationRepository.findAll();

		StringBuilder sb = new StringBuilder();

		sb.append("応募総数: ").append(applications.size()).append("件\n");

		if (applications.isEmpty()) {
			sb.append("現在、保存されている応募データはありません。\n");
			return sb.toString();
		}

		sb.append("応募一覧:\n");

		int index = 1;
		for (Application app : applications) {
			sb.append(index++).append(". ");
			sb.append("会社名=").append(nullToUnknown(app.getCompanyName()));
			sb.append(", 職種=").append(nullToUnknown(app.getPositionName()));
			sb.append(", 応募方法=").append(nullToUnknown(app.getChannel()));
			sb.append(", 状態=").append(app.getCurrentStatus() != null ? app.getCurrentStatus().getLabel() : "未設定");
			sb.append(", 応募日=").append(app.getAppliedDate() != null ? app.getAppliedDate() : "未設定");
			sb.append(", 次の予定=").append(app.getNextActionAt() != null ? app.getNextActionAt() : "なし");
			sb.append(", 最終更新=").append(app.getUpdatedAt() != null ? app.getUpdatedAt() : "未設定");
			sb.append("\n");
		}

		sb.append("\n重要: 上記にない会社名・応募件数・ステータス・日付は保存データ上確認できません。\n");

		return sb.toString();
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

		log.info("Chat history prepared. chatId={}, totalHistory={}, targetMessages={}, useFullChatHistory={}",
				chat.getId(),
				history.size(),
				targetMessages.size(),
				useFullChatHistory);

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
		Chat savedChat = chatRepository.save(newChat);

		log.info("New chat created. chatId={}, titleLength={}",
				savedChat.getId(),
				title != null ? title.length() : 0);

		return savedChat;
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

	private String nullToUnknown(String value) {
		return value == null || value.isBlank() ? "未設定" : value;
	}

	private String buildConversationSummary(Chat chat, Settings settings) {
		if (chat == null) {
			return "";
		}

		if (conversationSummaryService.shouldRefreshSummary(chat, settings)) {
			log.info("Refreshing conversation summary. chatId={}", chat.getId());

			String summary = conversationSummaryService.buildSummary(chat, settings);
			chat.setSummary(summary);
			chatRepository.save(chat);

			log.info("Conversation summary refreshed. chatId={}, summaryLength={}",
					chat.getId(),
					summary != null ? summary.length() : 0);

			return summary;
		}

		return chat.getSummary() != null ? chat.getSummary() : "";
	}
}