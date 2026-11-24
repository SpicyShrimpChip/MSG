package com.msg.backend.domain.quiz.application;

import com.msg.backend.domain.analysis.dao.AnalysisRunRepository;
import com.msg.backend.domain.analysis.dao.ConceptConsensusRepository;
import com.msg.backend.domain.analysis.entity.AnalysisRunStatus;
import com.msg.backend.domain.analysis.entity.ConceptConsensus;
import com.msg.backend.domain.course.entity.Course;
import com.msg.backend.domain.quiz.dao.QuizGenerationRepository;
import com.msg.backend.domain.quiz.dao.QuizQuestionRepository;
import com.msg.backend.domain.quiz.dao.QuizRepository;
import com.msg.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.msg.backend.domain.quiz.dto.GenerationStatusResponse;
import com.msg.backend.domain.quiz.entity.QuizAiCall;
import com.msg.backend.domain.quiz.dto.QuizCreateRequest;
import com.msg.backend.domain.quiz.dto.QuizResponse;
import com.msg.backend.domain.quiz.entity.AssessmentKind;
import com.msg.backend.domain.quiz.entity.Quiz;
import com.msg.backend.domain.quiz.entity.QuizGeneration;
import com.msg.backend.domain.quiz.entity.QuizGenerationStatus;
import com.msg.backend.domain.quiz.entity.QuizQuestion;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.llm.AiCallQuota;
import com.msg.backend.global.llm.UserAiUsage;
import com.msg.backend.global.llm.LlmClient;
import com.msg.backend.global.llm.LlmClients;
import com.msg.backend.global.llm.LlmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 학습 모드(시험 대비·자기개발·업무 활용)에 맞춰 AI로 퀴즈를 만든다.
 *
 * <p>순서: (1) 요청 검사(QuizSpecResolver) → (2) 동의·한도·중복을 확인하고 만들기 기록(RUNNING)을 커밋 → (3) DB 트랜잭션 밖에서 모델 호출
 * → (4) 검증을 통과한 퀴즈만 저장하고 기록을 SUCCEEDED로, 아니면 FAILED로 남기고 오류를 돌려준다.
 *
 * <p>외부로 보내는 것: 과목 이름, 개념(과목 키워드), 사용자가 적은 시험 범위, 직무 분야 이름, 코드가 요약한 개념별 참고 상태.
 * 사용자 대화 원문은 보내지 않는다. 학습자료 본문은 app.material-content.enabled가 켜져 있고 요청에서 확인(materialContentConsent)한
 * 경우에만 발췌(app.material-content.max-chars까지)를 보낸다.
 * AI 퀴즈 처리 동의(대화 분석 동의와 별도)를 한 사용자만 쓸 수 있다. 키가 없으면 가짜 문제를 만들지 않고 503.
 *
 * <p>시간: 전체 예산(app.quiz.generation-deadline) 안에서만 모델을 부르고 기다린다. 기록에 마감 시각을 두고, 저장할 때
 * 사용자 행을 잠근 채 기록이 아직 진행 중이고 마감 전인지 확인한다(늦게 온 답·다른 요청이 끝낸 기록은 저장하지 않음).
 * 어떤 이유로 끝나든 finally에서 진행 중 기록을 실패·시간 초과로 끝내고, 서버가 재시작돼 남은 기록은 다음 요청이 마감을 보고 정리한다.
 *
 * <p>모델은 설정 순서(app.quiz.generation-providers)에서 설정된 첫 모델 하나만 부르고, 실패하거나 형식이 맞지 않으면
 * 다음 모델로 넘어간다(같은 모델의 일시 오류 재시도는 ModelInvoker가 남은 시간 안에서만 한다). 호출은 모두 quiz_ai_call에 남긴다.
 */
@Slf4j
@Service
public class QuizGenerationService {

	static final String CONSENT_REQUIRED_MESSAGE = "AI로 퀴즈를 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.";
	static final String GENERATION_NOT_FOUND_MESSAGE = "퀴즈 만들기 요청을 찾을 수 없어요.";
	static final String NO_MODEL_MESSAGE = "연결된 AI 모델이 없어 지금은 퀴즈를 만들 수 없어요.";
	static final String RUNNING_MESSAGE = "퀴즈를 만들고 있어요. 끝난 뒤 다시 시도해 주세요.";
	static final String FAILED_KEY_MESSAGE = "이 요청은 이미 끝났어요(실패 또는 시간 초과). 다시 만들기를 눌러 주세요.";
	static final String MODEL_FAILED_MESSAGE = "AI 모델의 응답을 받지 못해 퀴즈를 만들지 못했어요. 잠시 뒤 다시 시도해 주세요.";
	static final String INVALID_OUTPUT_MESSAGE = "AI가 만든 문제가 형식에 맞지 않아 저장하지 않았어요. 다시 시도해 주세요.";
	static final String TIMED_OUT_MESSAGE = "AI가 정해진 시간 안에 문제를 만들지 못했어요. 잠시 뒤 다시 시도해 주세요.";
	static final String UNEXPECTED_MESSAGE = "퀴즈를 만들다가 문제가 생겨 저장하지 않았어요. 다시 시도해 주세요.";
	static final String MODEL_BUDGET_MESSAGE = "오늘 AI 호출 한도를 다 써서 지금은 퀴즈를 만들 수 없어요. 내일 다시 시도해 주세요.";

	private final UserRepository userRepository;
	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizGenerationRepository generationRepository;
	private final AnalysisRunRepository runRepository;
	private final ConceptConsensusRepository consensusRepository;
	private final QuizStatsService statsService;
	private final QuizSpecResolver specResolver;
	private final LlmClients llmClients;
	private final QuizModelFallback modelFallback;
	private final AiCallQuota aiCallQuota;
	private final UserAiUsage userAiUsage;
	private final QuizGenerationParser parser;
	private final QuizProperties properties;
	private final QuizAiUsage usage;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public QuizGenerationService(UserRepository userRepository, QuizRepository quizRepository,
			QuizQuestionRepository questionRepository, QuizGenerationRepository generationRepository,
			AnalysisRunRepository runRepository, ConceptConsensusRepository consensusRepository,
			QuizStatsService statsService, QuizSpecResolver specResolver, LlmClients llmClients,
			QuizModelFallback modelFallback, AiCallQuota aiCallQuota, UserAiUsage userAiUsage,
			QuizGenerationParser parser,
			QuizProperties properties, QuizAiUsage usage, ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.userRepository = userRepository;
		this.quizRepository = quizRepository;
		this.questionRepository = questionRepository;
		this.generationRepository = generationRepository;
		this.runRepository = runRepository;
		this.consensusRepository = consensusRepository;
		this.statsService = statsService;
		this.specResolver = specResolver;
		this.llmClients = llmClients;
		this.modelFallback = modelFallback;
		this.aiCallQuota = aiCallQuota;
		this.userAiUsage = userAiUsage;
		this.parser = parser;
		this.properties = properties;
		this.usage = usage;
		this.objectMapper = objectMapper;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	private record Started(Long generationId, QuizResponse existing, QuizPrompt.Input input, QuizSpec spec,
			Instant deadline) {
	}

	public record Created(QuizResponse quiz, boolean reused) {
	}

	public Created create(Long userId, QuizCreateRequest request) {
		List<LlmClient> clients = orderedClients();
		Started started = transaction.execute(status -> start(userId, request, clients));
		if (started.existing() != null) {
			return new Created(started.existing(), true);
		}
		Long generationId = started.generationId();
		QuizSpec spec = started.spec();
		Instant deadline = started.deadline();
		LlmRequest llmRequest = new LlmRequest(QuizPrompt.SYSTEM, QuizPrompt.user(objectMapper, started.input()),
				properties.generationMaxOutputTokens());
		// 실패 기록용 호출 상태. 예상하지 못한 예외로 끝나면 UNEXPECTED로 남는다
		QuizModelFallback.Tracker attempt = new QuizModelFallback.Tracker("UNEXPECTED");
		try {
			var success = modelFallback.run(clients,
					new QuizModelFallback.Call(userId, QuizAiCall.GENERATION, generationId, llmRequest, deadline, true),
					raw -> parse(raw, spec, attempt), attempt);
			if (success.isEmpty()) {
				throw new BusinessException(failureStatus(attempt.errorCode()), failureMessage(attempt.errorCode()));
			}
			QuizResponse saved = transaction.execute(status -> save(userId, generationId, spec, success.get().value(),
					success.get().client(), attempt));
			if (saved == null) {
				// 마감이 지났거나 다른 요청이 이 기록을 끝냈다: 늦게 온 결과는 저장하지 않는다
				throw new BusinessException(HttpStatus.GATEWAY_TIMEOUT, TIMED_OUT_MESSAGE);
			}
			return new Created(saved, false);
		} finally {
			// 성공했으면 SUCCEEDED라 바뀌지 않는다. 그 밖(실패·시간 초과·예상하지 못한 예외)은 진행 중으로 남기지 않는다
			finishIfRunning(generationId, attempt);
		}
	}

	// 모델이 만든 퀴즈를 검사한다. 형식이 맞지 않으면 빈 값 (다음 모델로 넘어간다)
	private Optional<QuizGenerationParser.ParsedQuiz> parse(String raw, QuizSpec spec, QuizModelFallback.Tracker attempt) {
		try {
			return Optional.of(parser.parse(raw, spec.mode(), spec.plan(), spec.concepts(), expectation(spec)));
		} catch (QuizGenerationParser.InvalidOutputException e) {
			log.warn("{} 모델이 만든 퀴즈가 형식에 맞지 않다: {}", attempt.last().provider(), e.getMessage());
			return Optional.empty();
		}
	}

	// 인지 수준과 보낸 자료 발췌를 함께 검증한다 (QuizGenerationParser)
	private static QuizGenerationParser.Expectation expectation(QuizSpec spec) {
		int count = spec.plan().values().stream().mapToInt(Integer::intValue).sum();
		return new QuizGenerationParser.Expectation(spec.bloomTargets(),
				BloomPlan.requiredDistinct(spec.bloomTargets(), count), spec.materialExcerpt());
	}

	private void finishIfRunning(Long generationId, QuizModelFallback.Tracker attempt) {
		try {
			transaction.executeWithoutResult(status -> generationRepository.findById(generationId)
					.filter(generation -> generation.getStatus() == QuizGenerationStatus.RUNNING)
					.ifPresent(generation -> {
						LocalDateTime now = LocalDateTime.now(clock);
						if (QuizModelFallback.TIMED_OUT.equals(attempt.errorCode())) {
							generation.timeOut(now);
						} else {
							LlmClient last = attempt.last();
							generation.fail(attempt.errorCode(), last == null ? null : last.provider().apiValue(),
									last == null ? null : last.model(), attempt.inputTokens(), attempt.outputTokens(),
									attempt.attempts(), now);
						}
					}));
		} catch (RuntimeException e) {
			// 기록을 끝내지 못해도 마감 시각이 지나면 끝난 것으로 본다 (start의 정리)
			log.error("퀴즈 만들기 기록을 끝내지 못했다 (generation {})", generationId, e);
		}
	}

	private static HttpStatus failureStatus(String errorCode) {
		return "TIMED_OUT".equals(errorCode) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
	}

	static String failureMessage(String errorCode) {
		if (errorCode == null) {
			return null;
		}
		return switch (errorCode) {
			case "INVALID_OUTPUT" -> INVALID_OUTPUT_MESSAGE;
			case "TIMED_OUT" -> TIMED_OUT_MESSAGE;
			case "MODEL_FAILED" -> MODEL_FAILED_MESSAGE;
			default -> UNEXPECTED_MESSAGE;
		};
	}

	// 설정 순서대로, 설정된 모델만
	private List<LlmClient> orderedClients() {
		return llmClients.configuredInOrder(properties.generationProviders());
	}

	private Started start(Long userId, QuizCreateRequest request, List<LlmClient> clients) {
		// 사용자 행을 잠가 같은 사용자의 만들기 요청을 차례로 처리한다 (중복 키·동시 실행·한도 검사가 겹치지 않게)
		User user = userRepository.lockLoginUser(userId);
		LocalDateTime now = LocalDateTime.now(clock);
		expireStale(userId, now);
		String requestKey = request.requestKey().strip();
		var existing = generationRepository.findByUserIdAndRequestKey(userId, requestKey);
		if (existing.isPresent()) {
			return reuse(userId, existing.get());
		}
		QuizSpec spec = specResolver.resolve(userId, request);
		if (!user.isAiQuizConsent()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, CONSENT_REQUIRED_MESSAGE);
		}
		if (clients.isEmpty()) {
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, NO_MODEL_MESSAGE);
		}
		if (!generationRepository.findAllByUserIdAndStatus(userId, QuizGenerationStatus.RUNNING).isEmpty()) {
			throw new BusinessException(HttpStatus.CONFLICT, RUNNING_MESSAGE);
		}
		if (userAiUsage.countLast24Hours(userId, UserAiUsage.Kind.QUIZ_GENERATION)
				>= properties.dailyGenerationLimit()) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "24시간 동안 만들 수 있는 퀴즈 수("
					+ properties.dailyGenerationLimit() + "번)를 다 썼어요. 나중에 다시 시도해 주세요.");
		}
		if (usage.modelCallBudgetExhausted(userId)) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, MODEL_BUDGET_MESSAGE);
		}
		aiCallQuota.ensureRemaining();
		Instant deadline = clock.instant().plus(properties.generationDeadline());
		QuizGeneration generation;
		try {
			generation = generationRepository.saveAndFlush(QuizGeneration.start(userId, spec.course().getId(),
					requestKey, spec.mode(), spec.types(), spec.plan().values().stream().mapToInt(Integer::intValue).sum(),
					spec.difficulty(), now, LocalDateTime.ofInstant(deadline, clock.getZone())));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(HttpStatus.CONFLICT, RUNNING_MESSAGE);
		}
		userAiUsage.record(userId, UserAiUsage.Kind.QUIZ_GENERATION);
		QuizPrompt.Input input = new QuizPrompt.Input(spec.course().getName(), spec.mode(), spec.difficulty(),
				spec.plan(), spec.concepts(), spec.examScope(), spec.jobLabel(),
				// 대화 분석에서 나온 개념 상태는 대화 분석에도 동의한 경우에만 참고로 보낸다
				hints(userId, spec, user.isAiAnalysisConsent()), spec.mockExam(), spec.assessmentKind(),
				spec.avoidQuestions(), spec.materialExcerpt(), spec.bloomTargets(),
				BloomPlan.requiredDistinct(spec.bloomTargets(),
						spec.plan().values().stream().mapToInt(Integer::intValue).sum()));
		return new Started(generation.getId(), null, input, spec, deadline);
	}

	// 마감이 지난 진행 중 기록을 끝낸다 (서버가 재시작돼 남은 기록 포함). 사용자 행을 잠근 트랜잭션 안에서 부른다
	private void expireStale(Long userId, LocalDateTime now) {
		generationRepository.findAllByUserIdAndStatus(userId, QuizGenerationStatus.RUNNING).stream()
				.filter(generation -> !generation.isActiveAt(now, properties.runningTimeout()))
				.forEach(generation -> generation.timeOut(now));
	}

	/**
	 * 만들기 요청 상태 (화면을 새로 고치거나 다른 화면에 다녀와도 같은 요청의 결과를 찾을 수 있게). 남의 요청·없는 키는 404.
	 */
	public GenerationStatusResponse status(Long userId, String requestKey) {
		return transaction.execute(status -> {
			QuizGeneration generation = generationRepository.findByUserIdAndRequestKey(userId, requestKey.strip())
					.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, GENERATION_NOT_FOUND_MESSAGE));
			LocalDateTime now = LocalDateTime.now(clock);
			QuizGenerationStatus current = generation.getStatus() == QuizGenerationStatus.RUNNING
					&& !generation.isActiveAt(now, properties.runningTimeout()) ? QuizGenerationStatus.TIMED_OUT
							: generation.getStatus();
			String code = current == QuizGenerationStatus.TIMED_OUT ? "TIMED_OUT" : generation.getErrorCode();
			return new GenerationStatusResponse(current.apiValue(),
					current == QuizGenerationStatus.SUCCEEDED ? generation.getQuizId() : null,
					current == QuizGenerationStatus.FAILED || current == QuizGenerationStatus.TIMED_OUT
							? failureMessage(code) : null,
					generation.getDeadlineAt());
		});
	}

	// 같은 요청 키: 이미 만든 퀴즈가 있으면 그대로 돌려주고, 진행 중·실패면 409
	private Started reuse(Long userId, QuizGeneration generation) {
		if (generation.getStatus() == QuizGenerationStatus.SUCCEEDED && generation.getQuizId() != null) {
			var quiz = quizRepository.findByIdAndUserId(generation.getQuizId(), userId);
			if (quiz.isPresent()) {
				return new Started(generation.getId(), QuizResponse.of(quiz.get(),
						questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.get().getId())), null, null, null);
			}
		}
		// 진행 중이면 409 (마감이 지난 기록은 start에서 이미 끝냈다)
		throw new BusinessException(HttpStatus.CONFLICT,
				generation.getStatus() == QuizGenerationStatus.RUNNING ? RUNNING_MESSAGE : FAILED_KEY_MESSAGE);
	}

	// 개념별 참고 상태: 최근 대화 분석의 다수 의견, 지난 퀴즈 점수, 내 피드백 (모두 코드가 만든 값)
	private List<QuizPrompt.ConceptHint> hints(Long userId, QuizSpec spec, boolean includeAnalysis) {
		Map<String, ConceptConsensus> consensus = !includeAnalysis ? Map.of() : runRepository
				.findFirstByUserIdAndCourseIdAndStatusInOrderByIdDesc(userId, spec.course().getId(),
						Set.of(AnalysisRunStatus.COMPLETED, AnalysisRunStatus.PARTIAL))
				.map(run -> consensusRepository.findAllByRunIdOrderByIdAsc(run.getId()).stream()
						.collect(Collectors.toMap(ConceptConsensus::getConceptKey, Function.identity(), (a, b) -> a)))
				.orElse(Map.of());
		Map<String, ConceptQuizStatsResponse> stats = statsService.byCourse(userId, spec.course()).stream()
				.collect(Collectors.toMap(ConceptQuizStatsResponse::conceptId, Function.identity(), (a, b) -> a));
		List<QuizPrompt.ConceptHint> hints = new ArrayList<>();
		for (String concept : spec.concepts()) {
			String key = Course.nameKey(concept);
			ConceptConsensus agreed = consensus.get(key);
			ConceptQuizStatsResponse stat = stats.get(key);
			hints.add(new QuizPrompt.ConceptHint(concept,
					agreed == null || agreed.getStatus() == null ? null : agreed.getStatus().apiValue(),
					stat == null || stat.maxScore() == 0 ? null : stat.score(),
					stat == null || stat.maxScore() == 0 ? null : stat.maxScore(),
					stat == null ? null : stat.feedback()));
		}
		return hints;
	}

	// 이 요청이 아직 진행 중이고 마감 전일 때만 저장한다. 아니면 null (늦게 온 결과·다른 요청이 끝낸 기록)
	private QuizResponse save(Long userId, Long generationId, QuizSpec spec, QuizGenerationParser.ParsedQuiz parsed,
			LlmClient client, QuizModelFallback.Tracker attempt) {
		userRepository.lockLoginUser(userId);
		LocalDateTime now = LocalDateTime.now(clock);
		QuizGeneration generation = generationRepository.findById(generationId).orElse(null);
		if (generation == null || !generation.isActiveAt(now, properties.runningTimeout())) {
			if (generation != null) {
				generation.timeOut(now);
			}
			attempt.timeOut();
			return null;
		}
		Quiz quiz = quizRepository.save(Quiz.generated(userId, spec.course().getId(), spec.materialId(), title(spec),
				spec.mode(), spec.difficulty(), spec.jobField(), spec.jobFieldOther(), spec.examScope(),
				client.provider().apiValue(), client.model(), parsed.suggestions(), spec.mockExam(), now)
				.asAssessment(spec.assessmentKind(), spec.preAssessmentId())
				.withMaterialContentUsed(spec.materialExcerpt() != null)
				.withConceptSource(spec.conceptSource())
				.asConceptCheck(spec.checkConceptKey(), spec.checkConceptName())
				.withLearningDesign(BloomPlan.join(spec.bloomTargets()), QuizPrompt.VERSION)
				.withMaterialScope(spec.materialExcerpt() == null ? List.of() : spec.materialScope(),
						spec.materialExcerpt() == null ? List.of() : spec.missingConcepts()));
		List<QuizQuestion> questions = new ArrayList<>();
		for (int i = 0; i < parsed.questions().size(); i++) {
			questions.add(parsed.questions().get(i).toEntity(quiz.getId(), i));
		}
		List<QuizQuestion> saved = questionRepository.saveAll(questions);
		generation.succeed(quiz.getId(), client.provider().apiValue(), client.model(), attempt.inputTokens(),
				attempt.outputTokens(), attempt.attempts(), now);
		return QuizResponse.of(quiz, saved).withMaterialContentNote(spec.materialContentNote());
	}

	// 제목은 코드가 만든다 (출제 보장처럼 읽히는 제목이 생기지 않게)
	private static String title(QuizSpec spec) {
		if (spec.checkConceptName() != null) {
			String title = spec.checkConceptName() + " 확인 퀴즈";
			return title.length() <= Quiz.TITLE_MAX_LENGTH ? title : title.substring(0, Quiz.TITLE_MAX_LENGTH);
		}
		if (spec.assessmentKind() != null) {
			String title = spec.materialTitle() + " · " + (spec.assessmentKind() == AssessmentKind.PRE ? "사전평가" : "사후평가");
			return title.length() <= Quiz.TITLE_MAX_LENGTH ? title : title.substring(0, Quiz.TITLE_MAX_LENGTH);
		}
		String label = switch (spec.mode()) {
			case EXAM_PREP -> spec.mockExam() ? "모의시험" : "시험 대비 예상 문제";
			case SELF_DEVELOPMENT -> "자기개발 이해 점검";
			case WORK -> "업무 활용 상황 문제";
		};
		String title = spec.course().getName() + " · " + label;
		return title.length() <= Quiz.TITLE_MAX_LENGTH ? title : title.substring(0, Quiz.TITLE_MAX_LENGTH);
	}
}
