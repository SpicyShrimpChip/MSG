package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.analysis.dao.AnalysisModelResultRepository;
import com.ailms.backend.domain.analysis.dao.AnalysisRunConversationRepository;
import com.ailms.backend.domain.analysis.dao.AnalysisRunRepository;
import com.ailms.backend.domain.analysis.dao.ConceptConsensusRepository;
import com.ailms.backend.domain.analysis.dao.ConceptJudgmentRepository;
import com.ailms.backend.domain.analysis.dao.JudgmentEvidenceRepository;
import com.ailms.backend.domain.analysis.dto.AnalysisRunResponse;
import com.ailms.backend.domain.analysis.dto.ProviderStatusResponse;
import com.ailms.backend.domain.analysis.entity.AnalysisModelResult;
import com.ailms.backend.domain.analysis.entity.AnalysisRun;
import com.ailms.backend.domain.analysis.entity.AnalysisRunConversation;
import com.ailms.backend.domain.analysis.entity.AnalysisRunStatus;
import com.ailms.backend.domain.analysis.entity.ConceptConsensus;
import com.ailms.backend.domain.analysis.entity.ConceptJudgment;
import com.ailms.backend.domain.analysis.entity.JudgmentEvidence;
import com.ailms.backend.domain.analysis.entity.ModelResultStatus;
import com.ailms.backend.domain.analysis.entity.UnderstandingStatus;
import com.ailms.backend.domain.conversation.dao.ConversationMessageRepository;
import com.ailms.backend.domain.conversation.dao.ConversationRepository;
import com.ailms.backend.domain.conversation.entity.Conversation;
import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.entity.User;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmClients;
import com.ailms.backend.global.llm.LlmProvider;
import com.ailms.backend.global.llm.LlmRequest;
import com.ailms.backend.global.security.ApiErrorResponder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 학습 대화 개념 분석. 과목 하나의 분석 대상 대화를 설정된 모든 모델(GPT·Claude·Gemini)에 같은 지시문으로 따로 보내고,
 * 모델별 결과를 검증해 저장한 뒤 개념마다 모델 의견을 비교한다. 숙련도 점수는 만들지 않는다.
 *
 * <p>순서: (1) 동의·소유·횟수 확인 후 분석 행을 만들고 커밋 → (2) DB 트랜잭션 밖에서 모델 호출(동시에, 서로의 결과를 모름)
 * → (3) 다시 동의를 확인하고 결과 저장. 모델을 기다리는 동안 DB 연결과 잠금을 잡고 있지 않는다.
 * 그 사이 동의를 철회하거나 대화를 지우면 분석 행이 지워지므로, (3)에서 결과를 버리고 409로 알린다.
 */
@Slf4j
@Service
public class AnalysisService {

	static final String CONSENT_REQUIRED_MESSAGE = "AI 대화 분석에 동의해야 학습 분석을 할 수 있어요.";
	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String NO_KEYWORDS_MESSAGE = "과목에 키워드를 먼저 넣어 주세요. 키워드가 분석할 개념이 돼요.";
	static final String NO_CONVERSATIONS_MESSAGE = "이 과목에 분석할 AI 대화가 없어요. 대화를 먼저 가져와 주세요.";
	static final String NO_MATCHING_MESSAGES = "과목 키워드가 들어간 대화가 없어 분석할 수 없어요.";
	static final String NO_MODEL_MESSAGE = "연결된 AI 모델이 없어 지금은 분석할 수 없어요.";
	static final String ALREADY_RUNNING_MESSAGE = "이미 분석하고 있어요. 끝난 뒤 다시 시도해 주세요.";
	static final String CHANGED_MESSAGE = "분석하는 동안 대화나 AI 대화 분석 동의가 바뀌어 결과를 저장하지 않았어요.";

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final ConversationRepository conversationRepository;
	private final ConversationMessageRepository messageRepository;
	private final AnalysisRunRepository runRepository;
	private final AnalysisRunConversationRepository runConversationRepository;
	private final AnalysisModelResultRepository modelResultRepository;
	private final ConceptJudgmentRepository judgmentRepository;
	private final JudgmentEvidenceRepository evidenceRepository;
	private final ConceptConsensusRepository consensusRepository;
	private final LlmClients llmClients;
	private final ModelInvoker modelInvoker;
	private final ModelOutputParser outputParser;
	private final AnalysisInputBuilder inputBuilder;
	private final AnalysisProperties properties;
	private final AnalysisQueryService queryService;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public AnalysisService(UserRepository userRepository, CourseRepository courseRepository,
			ConversationRepository conversationRepository, ConversationMessageRepository messageRepository,
			AnalysisRunRepository runRepository, AnalysisRunConversationRepository runConversationRepository,
			AnalysisModelResultRepository modelResultRepository, ConceptJudgmentRepository judgmentRepository,
			JudgmentEvidenceRepository evidenceRepository, ConceptConsensusRepository consensusRepository,
			LlmClients llmClients, ModelInvoker modelInvoker, ModelOutputParser outputParser,
			AnalysisInputBuilder inputBuilder, AnalysisProperties properties, AnalysisQueryService queryService,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.runRepository = runRepository;
		this.runConversationRepository = runConversationRepository;
		this.modelResultRepository = modelResultRepository;
		this.judgmentRepository = judgmentRepository;
		this.evidenceRepository = evidenceRepository;
		this.consensusRepository = consensusRepository;
		this.llmClients = llmClients;
		this.modelInvoker = modelInvoker;
		this.outputParser = outputParser;
		this.inputBuilder = inputBuilder;
		this.properties = properties;
		this.queryService = queryService;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	private record Prepared(Long runId, AnalysisInput input) {
	}

	// 키 값은 내보내지 않고, 연결됐는지만 알려 준다
	public List<ProviderStatusResponse> providers() {
		return llmClients.all().stream()
				.map(client -> new ProviderStatusResponse(client.provider().apiValue(),
						client.isConfigured() ? client.model() : null, client.isConfigured()))
				.toList();
	}

	public AnalysisRunResponse run(Long userId, Long courseId) {
		List<LlmClient> clients = llmClients.all();
		List<LlmClient> configured = clients.stream().filter(LlmClient::isConfigured).toList();
		if (configured.isEmpty()) {
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, NO_MODEL_MESSAGE);
		}
		Prepared prepared = transaction.execute(status -> prepare(userId, courseId));

		LlmRequest request = new LlmRequest(AnalysisPrompt.SYSTEM, prepared.input().userPrompt(),
				properties.maxOutputTokens());
		Map<LlmProvider, CompletableFuture<ModelInvoker.Outcome>> calls = new EnumMap<>(LlmProvider.class);
		configured.forEach(client -> calls.put(client.provider(), modelInvoker.invokeAsync(client, request)));
		Map<LlmProvider, ModelInvoker.Outcome> outcomes = new EnumMap<>(LlmProvider.class);
		calls.forEach((provider, call) -> outcomes.put(provider, call.join()));

		try {
			transaction.executeWithoutResult(status -> save(userId, prepared, clients, outcomes));
		} catch (BusinessException e) {
			throw e;
		} catch (RuntimeException e) {
			log.error("분석 결과를 저장하지 못했다 (run {})", prepared.runId(), e);
			transaction.executeWithoutResult(status -> runRepository.findById(prepared.runId())
					.ifPresent(run -> run.finish(AnalysisRunStatus.FAILED, LocalDateTime.now(clock))));
			throw e;
		}
		return queryService.getRun(userId, prepared.runId());
	}

	private Prepared prepare(Long userId, Long courseId) {
		// 사용자 행을 잠가 같은 사용자의 동의 철회·다른 분석 시작과 차례로 처리한다
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		if (!user.isAiAnalysisConsent()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, CONSENT_REQUIRED_MESSAGE);
		}
		Course course = courseRepository.findByIdAndUserId(courseId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE));
		if (course.getKeywords().isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NO_KEYWORDS_MESSAGE);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		if (runRepository.existsByUserIdAndStatusAndCreatedAtAfter(userId, AnalysisRunStatus.RUNNING,
				now.minus(properties.runningTimeout()))) {
			throw new BusinessException(HttpStatus.CONFLICT, ALREADY_RUNNING_MESSAGE);
		}
		if (runRepository.countByUserIdAndCreatedAtAfter(userId, now.minusHours(24)) >= properties.dailyRunLimit()) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
					"24시간 동안 분석할 수 있는 횟수(" + properties.dailyRunLimit() + "번)를 다 썼어요. 나중에 다시 시도해 주세요.");
		}
		List<Conversation> conversations = conversationRepository.findAnalyzableInCourse(userId, courseId);
		if (conversations.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NO_CONVERSATIONS_MESSAGE);
		}
		AnalysisInput input = inputBuilder.build(course, conversations,
				messageRepository.findAllByConversationIdInOrderByConversationIdAscPartIndexAscSeqAsc(
						conversations.stream().map(Conversation::getId).toList()));
		if (input.sentMessages().isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NO_MATCHING_MESSAGES);
		}
		AnalysisRun run = runRepository.save(AnalysisRun.start(userId, courseId, AnalysisPrompt.VERSION,
				input.conversationIds().size(), input.sentMessages().size(), input.truncatedMessageCount(),
				input.userPrompt().length(), now));
		runConversationRepository.saveAll(input.conversationIds().stream()
				.map(conversationId -> AnalysisRunConversation.of(run.getId(), conversationId))
				.toList());
		return new Prepared(run.getId(), input);
	}

	private void save(Long userId, Prepared prepared, List<LlmClient> clients,
			Map<LlmProvider, ModelInvoker.Outcome> outcomes) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		// 철회하거나 쓴 대화를 지우면 분석 행이 지워진다(AnalysisCleanup). 동의만 꺼진 경우도 저장하지 않는다
		AnalysisRun run = runRepository.findByIdAndUserId(prepared.runId(), userId).orElse(null);
		if (run == null || !user.isAiAnalysisConsent()) {
			if (run != null) {
				runRepository.delete(run);
			}
			throw new BusinessException(HttpStatus.CONFLICT, CHANGED_MESSAGE);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		AnalysisInput input = prepared.input();
		Map<String, List<UnderstandingStatus>> byConcept = new LinkedHashMap<>();
		input.concepts().forEach(concept -> byConcept.put(concept.key(), new ArrayList<>()));
		int called = 0;
		int succeeded = 0;
		for (LlmClient client : clients) {
			ModelInvoker.Outcome outcome = outcomes.get(client.provider());
			if (outcome == null) {
				modelResultRepository.save(AnalysisModelResult.of(run.getId(), client.provider(), null,
						ModelResultStatus.NOT_CONFIGURED, null, null, null, null, 0, null, 0, now));
				continue;
			}
			called++;
			if (outcome.errorCode() != null) {
				modelResultRepository.save(AnalysisModelResult.of(run.getId(), client.provider(), client.model(),
						ModelResultStatus.FAILED, outcome.errorCode().name(), null, null, null, outcome.attempts(),
						outcome.latencyMs(), 0, now));
				continue;
			}
			String raw = outcome.response().text();
			ModelOutputParser.ParsedOutput parsed;
			try {
				parsed = outputParser.parse(raw, input);
			} catch (ModelOutputParser.InvalidOutputException e) {
				log.warn("{} 모델 답변이 약속한 형식이 아니다: {}", client.provider(), e.getMessage());
				modelResultRepository.save(AnalysisModelResult.of(run.getId(), client.provider(), client.model(),
						ModelResultStatus.INVALID_OUTPUT, "INVALID_OUTPUT", raw, outcome.response().inputTokens(),
						outcome.response().outputTokens(), outcome.attempts(), outcome.latencyMs(), 0, now));
				continue;
			}
			succeeded++;
			AnalysisModelResult result = modelResultRepository.save(AnalysisModelResult.of(run.getId(),
					client.provider(), client.model(), ModelResultStatus.SUCCEEDED, null, raw,
					outcome.response().inputTokens(), outcome.response().outputTokens(), outcome.attempts(),
					outcome.latencyMs(), parsed.droppedEvidenceCount(), now));
			for (ModelOutputParser.ParsedJudgment item : parsed.judgments()) {
				ConceptJudgment judgment = judgmentRepository.save(ConceptJudgment.of(result.getId(),
						item.concept().key(), item.concept().name(), item.reportedStatus(), item.status(),
						item.adjustedReason(), item.reason(), item.needsCheck(), item.recommendedActivity()));
				evidenceRepository.saveAll(item.evidence().stream()
						.map(evidence -> JudgmentEvidence.of(judgment.getId(), evidence.messageId(), evidence.kind(),
								evidence.note()))
						.toList());
				byConcept.get(item.concept().key()).add(item.status());
			}
		}
		for (AnalysisInput.Concept concept : input.concepts()) {
			ConsensusCalculator.Result result = ConsensusCalculator.compare(byConcept.get(concept.key()));
			consensusRepository.save(ConceptConsensus.of(run.getId(), concept.key(), concept.name(), result.outcome(),
					result.status(), result.judgedCount(), result.agreeCount(), called));
		}
		run.finish(succeeded == 0 ? AnalysisRunStatus.FAILED
				: succeeded == called ? AnalysisRunStatus.COMPLETED : AnalysisRunStatus.PARTIAL, now);
	}
}
