package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.analysis.dao.AnalysisRunRepository;
import com.ailms.backend.domain.analysis.dao.ConceptConsensusRepository;
import com.ailms.backend.domain.analysis.entity.AnalysisRunStatus;
import com.ailms.backend.domain.analysis.entity.ConceptConsensus;
import com.ailms.backend.domain.conversation.application.PersonalInfoDetector;
import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.quiz.dao.QuizGenerationRepository;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.ailms.backend.domain.quiz.dto.GenerationStatusResponse;
import com.ailms.backend.domain.quiz.entity.QuizAiCall;
import com.ailms.backend.domain.quiz.dto.QuizCreateRequest;
import com.ailms.backend.domain.quiz.dto.QuizResponse;
import com.ailms.backend.domain.quiz.entity.AssessmentKind;
import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizDifficulty;
import com.ailms.backend.domain.quiz.entity.QuizGeneration;
import com.ailms.backend.domain.quiz.entity.QuizGenerationStatus;
import com.ailms.backend.domain.quiz.entity.QuizMode;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.user.application.LearningProfileValidator;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.entity.JobField;
import com.ailms.backend.domain.user.entity.User;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmClients;
import com.ailms.backend.global.llm.LlmProvider;
import com.ailms.backend.global.llm.LlmRequest;
import com.ailms.backend.global.llm.ModelInvoker;
import com.ailms.backend.global.security.ApiErrorResponder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 학습 모드(시험 대비·자기개발·업무 활용)에 맞춰 AI로 퀴즈를 만든다.
 *
 * <p>순서: (1) 요청 검사 → (2) 동의·한도·중복을 확인하고 만들기 기록(RUNNING)을 커밋 → (3) DB 트랜잭션 밖에서 모델 호출
 * → (4) 검증을 통과한 퀴즈만 저장하고 기록을 SUCCEEDED로, 아니면 FAILED로 남기고 오류를 돌려준다.
 *
 * <p>외부로 보내는 것: 과목 이름, 개념(과목 키워드), 사용자가 적은 시험 범위, 직무 분야 이름, 코드가 요약한 개념별 참고 상태.
 * 사용자 대화 원문과 학습자료 본문은 보내지 않는다(자료 본문 추출이 아직 없어 자료를 골라도 본문은 쓰지 않는다).
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
	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String MATERIAL_NOT_FOUND_MESSAGE = "자료를 찾을 수 없어요.";
	static final String MATERIAL_COURSE_MESSAGE = "고른 자료가 이 과목에 연결돼 있지 않아요.";
	static final String MODE_MESSAGE = "학습 모드를 다시 골라 주세요.";
	static final String TYPE_MESSAGE = "문제 유형을 다시 골라 주세요.";
	static final String DIFFICULTY_MESSAGE = "난이도를 다시 골라 주세요.";
	static final String NO_CONCEPTS_MESSAGE = "과목에 키워드를 먼저 넣어 주세요. 키워드가 퀴즈의 개념이 돼요.";
	static final String UNKNOWN_CONCEPT_MESSAGE = "과목 키워드에 없는 개념이 있어요. 과목 키워드에서 골라 주세요.";
	static final String SCOPE_TOO_LONG_MESSAGE = "시험 범위는 " + Quiz.EXAM_SCOPE_MAX_LENGTH + "자까지 적을 수 있어요.";
	static final String SCOPE_PERSONAL_INFO_MESSAGE = "시험 범위에 이메일·전화번호 같은 개인정보는 적지 말아 주세요.";
	static final String GENERATION_NOT_FOUND_MESSAGE = "퀴즈 만들기 요청을 찾을 수 없어요.";
	static final String NO_MODEL_MESSAGE = "연결된 AI 모델이 없어 지금은 퀴즈를 만들 수 없어요.";
	static final String RUNNING_MESSAGE = "퀴즈를 만들고 있어요. 끝난 뒤 다시 시도해 주세요.";
	static final String FAILED_KEY_MESSAGE = "이 요청은 이미 끝났어요(실패 또는 시간 초과). 다시 만들기를 눌러 주세요.";
	static final String MODEL_FAILED_MESSAGE = "AI 모델의 응답을 받지 못해 퀴즈를 만들지 못했어요. 잠시 뒤 다시 시도해 주세요.";
	static final String INVALID_OUTPUT_MESSAGE = "AI가 만든 문제가 형식에 맞지 않아 저장하지 않았어요. 다시 시도해 주세요.";
	static final String TIMED_OUT_MESSAGE = "AI가 정해진 시간 안에 문제를 만들지 못했어요. 잠시 뒤 다시 시도해 주세요.";
	static final String UNEXPECTED_MESSAGE = "퀴즈를 만들다가 문제가 생겨 저장하지 않았어요. 다시 시도해 주세요.";
	static final String MODEL_BUDGET_MESSAGE = "오늘 AI 호출 한도를 다 써서 지금은 퀴즈를 만들 수 없어요. 내일 다시 시도해 주세요.";
	static final String MOCK_EXAM_MODE_MESSAGE = "모의시험은 시험 대비 모드에서만 만들 수 있어요.";
	static final int MAX_CONCEPTS = 10;
	// 자료 본문은 이만큼까지만 보낸다 (비용·개인정보 노출 최소화)
	static final int MAX_MATERIAL_EXCERPT = 12_000;
	static final int MAX_AVOID_QUESTIONS = 20;
	static final String ASSESSMENT_KIND_MESSAGE = "평가 종류를 다시 골라 주세요.";
	static final String ASSESSMENT_MATERIAL_MESSAGE = "사전·사후평가는 과목에 연결된 학습자료를 골라야 해요.";
	static final String NO_PRE_ASSESSMENT_MESSAGE = "이 자료로 먼저 사전평가를 만들어 주세요. 사후평가는 사전평가와 같은 개념으로 만들어요.";
	static final String CONTENT_NEEDS_MATERIAL_MESSAGE = "자료 본문을 쓰려면 학습자료를 골라 주세요.";
	static final String CONTENT_CONSENT_MESSAGE = "자료 본문을 외부 AI로 보내려면 확인에 체크해 주세요.";

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final MaterialRepository materialRepository;
	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizGenerationRepository generationRepository;
	private final AnalysisRunRepository runRepository;
	private final ConceptConsensusRepository consensusRepository;
	private final QuizStatsService statsService;
	private final LlmClients llmClients;
	private final ModelInvoker modelInvoker;
	private final QuizGenerationParser parser;
	private final LearningProfileValidator profileValidator;
	private final PersonalInfoDetector personalInfoDetector;
	private final QuizProperties properties;
	private final QuizAiUsage usage;
	private final MaterialTextExtractor textExtractor;
	private final MaterialContentProperties contentProperties;
	private final com.ailms.backend.domain.material.dao.MaterialConceptRepository materialConceptRepository;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public QuizGenerationService(UserRepository userRepository, CourseRepository courseRepository,
			MaterialRepository materialRepository, QuizRepository quizRepository,
			QuizQuestionRepository questionRepository, QuizGenerationRepository generationRepository,
			AnalysisRunRepository runRepository, ConceptConsensusRepository consensusRepository,
			QuizStatsService statsService, LlmClients llmClients, ModelInvoker modelInvoker,
			QuizGenerationParser parser, LearningProfileValidator profileValidator,
			PersonalInfoDetector personalInfoDetector, QuizProperties properties, QuizAiUsage usage,
			MaterialTextExtractor textExtractor, MaterialContentProperties contentProperties,
			com.ailms.backend.domain.material.dao.MaterialConceptRepository materialConceptRepository,
			ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.materialRepository = materialRepository;
		this.quizRepository = quizRepository;
		this.questionRepository = questionRepository;
		this.generationRepository = generationRepository;
		this.runRepository = runRepository;
		this.consensusRepository = consensusRepository;
		this.statsService = statsService;
		this.llmClients = llmClients;
		this.modelInvoker = modelInvoker;
		this.parser = parser;
		this.profileValidator = profileValidator;
		this.personalInfoDetector = personalInfoDetector;
		this.properties = properties;
		this.usage = usage;
		this.textExtractor = textExtractor;
		this.contentProperties = contentProperties;
		this.materialConceptRepository = materialConceptRepository;
		this.objectMapper = objectMapper;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	// 검사를 마친 요청
	private record Spec(Course course, Long materialId, String materialTitle, QuizMode mode,
			List<QuestionType> types, Map<QuestionType, Integer> plan, QuizDifficulty difficulty, List<String> concepts,
			String examScope, JobField jobField, String jobFieldOther, boolean mockExam, AssessmentKind assessmentKind,
			Long preAssessmentId, List<String> avoidQuestions, String materialExcerpt, String materialContentNote,
			String conceptSource) {

		String jobLabel() {
			if (jobField == null) {
				return null;
			}
			return jobField == JobField.OTHER ? jobFieldOther : jobField.label();
		}
	}

	private record Started(Long generationId, QuizResponse existing, QuizPrompt.Input input, Spec spec,
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
		Spec spec = started.spec();
		Instant deadline = started.deadline();
		LlmRequest llmRequest = new LlmRequest(QuizPrompt.SYSTEM, QuizPrompt.user(objectMapper, started.input()),
				properties.generationMaxOutputTokens());
		Attempt attempt = new Attempt();
		try {
			for (LlmClient client : clients) {
				if (!clock.instant().isBefore(deadline)) {
					attempt.errorCode = "TIMED_OUT";
					break;
				}
				attempt.last = client;
				ModelInvoker.Outcome outcome = modelInvoker.await(
						modelInvoker.invokeAsync(client, llmRequest, ModelInvoker.Lane.QUIZ, deadline), deadline);
				attempt.attempts += Math.max(outcome.attempts(), outcome.timedOut() ? 1 : 0);
				if (outcome.timedOut()) {
					usage.record(userId, QuizAiCall.GENERATION, generationId, null, client, outcome, null);
					attempt.errorCode = "TIMED_OUT";
					break;
				}
				if (outcome.response() == null) {
					usage.record(userId, QuizAiCall.GENERATION, generationId, null, client, outcome, null);
					attempt.errorCode = "MODEL_FAILED";
					continue;
				}
				attempt.inputTokens = outcome.response().inputTokens();
				attempt.outputTokens = outcome.response().outputTokens();
				QuizGenerationParser.ParsedQuiz parsed;
				try {
					parsed = parser.parse(outcome.response().text(), spec.mode(), spec.plan(), spec.concepts());
				} catch (QuizGenerationParser.InvalidOutputException e) {
					log.warn("{} 모델이 만든 퀴즈가 형식에 맞지 않다: {}", client.provider(), e.getMessage());
					usage.record(userId, QuizAiCall.GENERATION, generationId, null, client, outcome, "INVALID_OUTPUT");
					attempt.errorCode = "INVALID_OUTPUT";
					continue;
				}
				usage.record(userId, QuizAiCall.GENERATION, generationId, null, client, outcome, null);
				LlmClient used = client;
				QuizResponse saved = transaction.execute(status -> save(userId, generationId, spec, parsed, used,
						attempt));
				if (saved == null) {
					// 마감이 지났거나 다른 요청이 이 기록을 끝냈다: 늦게 온 결과는 저장하지 않는다
					throw new BusinessException(HttpStatus.GATEWAY_TIMEOUT, TIMED_OUT_MESSAGE);
				}
				return new Created(saved, false);
			}
			throw new BusinessException(failureStatus(attempt.errorCode), failureMessage(attempt.errorCode));
		} finally {
			// 성공했으면 SUCCEEDED라 바뀌지 않는다. 그 밖(실패·시간 초과·예상하지 못한 예외)은 진행 중으로 남기지 않는다
			finishIfRunning(generationId, attempt);
		}
	}

	// 이번 요청의 모델 호출 상태 (실패 기록용)
	private static final class Attempt {
		private int attempts;
		private String errorCode = "UNEXPECTED";
		private LlmClient last;
		private Integer inputTokens;
		private Integer outputTokens;
	}

	private void finishIfRunning(Long generationId, Attempt attempt) {
		try {
			transaction.executeWithoutResult(status -> generationRepository.findById(generationId)
					.filter(generation -> generation.getStatus() == QuizGenerationStatus.RUNNING)
					.ifPresent(generation -> {
						LocalDateTime now = LocalDateTime.now(clock);
						if ("TIMED_OUT".equals(attempt.errorCode)) {
							generation.timeOut(now);
						} else {
							generation.fail(attempt.errorCode,
									attempt.last == null ? null : attempt.last.provider().apiValue(),
									attempt.last == null ? null : attempt.last.model(), attempt.inputTokens,
									attempt.outputTokens, attempt.attempts, now);
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
		Map<LlmProvider, LlmClient> byProvider = llmClients.all().stream()
				.filter(LlmClient::isConfigured)
				.collect(Collectors.toMap(LlmClient::provider, Function.identity()));
		List<LlmClient> ordered = new ArrayList<>();
		List<LlmProvider> order = properties.generationProviders() == null ? List.of(LlmProvider.values())
				: properties.generationProviders();
		order.stream().map(byProvider::get).filter(java.util.Objects::nonNull).distinct().forEach(ordered::add);
		return ordered;
	}

	private Started start(Long userId, QuizCreateRequest request, List<LlmClient> clients) {
		// 사용자 행을 잠가 같은 사용자의 만들기 요청을 차례로 처리한다 (중복 키·동시 실행·한도 검사가 겹치지 않게)
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		LocalDateTime now = LocalDateTime.now(clock);
		expireStale(userId, now);
		String requestKey = request.requestKey().strip();
		var existing = generationRepository.findByUserIdAndRequestKey(userId, requestKey);
		if (existing.isPresent()) {
			return reuse(userId, existing.get());
		}
		Spec spec = spec(userId, request);
		if (!user.isAiQuizConsent()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, CONSENT_REQUIRED_MESSAGE);
		}
		if (clients.isEmpty()) {
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, NO_MODEL_MESSAGE);
		}
		if (!generationRepository.findAllByUserIdAndStatus(userId, QuizGenerationStatus.RUNNING).isEmpty()) {
			throw new BusinessException(HttpStatus.CONFLICT, RUNNING_MESSAGE);
		}
		if (generationRepository.countByUserIdAndCreatedAtAfter(userId, now.minusHours(24))
				>= properties.dailyGenerationLimit()) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "24시간 동안 만들 수 있는 퀴즈 수("
					+ properties.dailyGenerationLimit() + "번)를 다 썼어요. 나중에 다시 시도해 주세요.");
		}
		if (usage.modelCallBudgetExhausted(userId)) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, MODEL_BUDGET_MESSAGE);
		}
		Instant deadline = clock.instant().plus(properties.generationDeadline());
		QuizGeneration generation;
		try {
			generation = generationRepository.saveAndFlush(QuizGeneration.start(userId, spec.course().getId(),
					requestKey, spec.mode(), spec.types(), spec.plan().values().stream().mapToInt(Integer::intValue).sum(),
					spec.difficulty(), now, LocalDateTime.ofInstant(deadline, clock.getZone())));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(HttpStatus.CONFLICT, RUNNING_MESSAGE);
		}
		QuizPrompt.Input input = new QuizPrompt.Input(spec.course().getName(), spec.mode(), spec.difficulty(),
				spec.plan(), spec.concepts(), spec.examScope(), spec.jobLabel(),
				// 대화 분석에서 나온 개념 상태는 대화 분석에도 동의한 경우에만 참고로 보낸다
				hints(userId, spec, user.isAiAnalysisConsent()), spec.mockExam(), spec.assessmentKind(),
				spec.avoidQuestions(), spec.materialExcerpt());
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

	private Spec spec(Long userId, QuizCreateRequest request) {
		Course course = courseRepository.findByIdAndUserId(request.courseId(), userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE));
		Material material = null;
		if (request.materialId() != null) {
			material = materialRepository.findByIdAndUserId(request.materialId(), userId)
					.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MATERIAL_NOT_FOUND_MESSAGE));
			if (!course.getId().equals(material.getCourseId())) {
				throw badRequest(MATERIAL_COURSE_MESSAGE);
			}
		}
		AssessmentKind kind = null;
		if (StringUtils.hasText(request.assessmentKind())) {
			kind = AssessmentKind.fromApiValue(request.assessmentKind())
					.orElseThrow(() -> badRequest(ASSESSMENT_KIND_MESSAGE));
			if (material == null) {
				throw badRequest(ASSESSMENT_MATERIAL_MESSAGE);
			}
		}
		// 사후평가: 같은 자료의 최신 사전평가와 개념·유형·문항 수·난이도·모드를 맞춘다(요청 값은 쓰지 않는다)
		Quiz pre = null;
		List<QuizQuestion> preQuestions = List.of();
		if (kind == AssessmentKind.POST) {
			pre = quizRepository.findFirstByUserIdAndMaterialIdAndAssessmentKindOrderByIdDesc(userId,
					material.getId(), AssessmentKind.PRE)
					.orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, NO_PRE_ASSESSMENT_MESSAGE));
			preQuestions = questionRepository.findAllByQuizIdOrderBySortOrderAsc(pre.getId());
		}
		QuizMode mode = pre != null && pre.getMode() != null ? pre.getMode()
				: QuizMode.fromApiValue(request.mode()).orElseThrow(() -> badRequest(MODE_MESSAGE));
		Set<QuestionType> types = new LinkedHashSet<>();
		if (pre != null && !preQuestions.isEmpty()) {
			preQuestions.forEach(question -> types.add(question.getType()));
		} else {
			for (String value : request.questionTypes()) {
				if (!types.add(QuestionType.fromApiValue(value).orElseThrow(() -> badRequest(TYPE_MESSAGE)))) {
					throw badRequest(TYPE_MESSAGE);
				}
			}
		}
		int count = pre != null && !preQuestions.isEmpty() ? preQuestions.size() : request.questionCount();
		boolean mockExam = Boolean.TRUE.equals(request.mockExam()) && kind == null;
		if (Boolean.TRUE.equals(request.mockExam()) && mode != QuizMode.EXAM_PREP) {
			throw badRequest(MOCK_EXAM_MODE_MESSAGE);
		}
		int max = mockExam ? properties.mockExamMaxQuestionCount() : properties.maxQuestionCount();
		if (count > max) {
			throw badRequest((mockExam ? "모의시험 문제는 " : "문제는 ") + max + "개까지 만들 수 있어요.");
		}
		if (count < types.size()) {
			throw badRequest("고른 유형이 " + types.size() + "가지라 문제 수는 " + types.size() + "개 이상이어야 해요.");
		}
		QuizDifficulty difficulty = pre != null && pre.getDifficulty() != null ? pre.getDifficulty()
				: StringUtils.hasText(request.difficulty())
						? QuizDifficulty.fromApiValue(request.difficulty()).orElseThrow(() -> badRequest(DIFFICULTY_MESSAGE))
						: QuizDifficulty.MEDIUM;
		// 개념 체계: 사후평가는 사전평가 문제의 개념 그대로, 평가는 자료에서 뽑은 개념이 있으면 그것, 아니면 과목 키워드
		String conceptSource = null;
		List<String> concepts;
		if (pre != null && !preQuestions.isEmpty()) {
			concepts = preConcepts(course, preQuestions);
			conceptSource = pre.getConceptSource();
		} else if (kind != null) {
			List<String> fromMaterial = materialConceptRepository
					.findAllByMaterialIdAndUserIdOrderBySortOrderAsc(material.getId(), userId).stream()
					.map(com.ailms.backend.domain.material.entity.MaterialConcept::getName)
					.limit(MAX_CONCEPTS)
					.toList();
			concepts = fromMaterial.isEmpty() ? concepts(course, request.concepts()) : fromMaterial;
			conceptSource = fromMaterial.isEmpty() ? "COURSE" : "MATERIAL";
		} else {
			concepts = concepts(course, request.concepts());
		}
		String examScope = null;
		if (mode == QuizMode.EXAM_PREP && StringUtils.hasText(request.examScope())) {
			examScope = request.examScope().strip();
			if (examScope.length() > Quiz.EXAM_SCOPE_MAX_LENGTH) {
				throw badRequest(SCOPE_TOO_LONG_MESSAGE);
			}
			if (personalInfoDetector.containsPersonalInfoExceptIp(examScope)) {
				throw badRequest(SCOPE_PERSONAL_INFO_MESSAGE);
			}
		}
		JobField jobField = null;
		String jobFieldOther = null;
		if (mode == QuizMode.WORK) {
			if (StringUtils.hasText(request.jobField())) {
				var profile = profileValidator.validate(null, request.jobField(), request.jobFieldOther());
				jobField = profile.jobField();
				jobFieldOther = profile.jobFieldOther();
			} else {
				User user = userRepository.findById(userId).orElseThrow();
				jobField = user.getJobField();
				jobFieldOther = user.getJobFieldOther();
			}
		}
		// 학습자료 본문: 사용자가 이 요청에서 보내기를 고르고 확인한 경우에만 읽는다. 사후평가는 사전평가가 본문을 썼으면 같게
		boolean requested = Boolean.TRUE.equals(request.useMaterialContent());
		if (requested && !contentProperties.enabled()) {
			// 운영 설정·처리방침 확정 전에는 자료 본문을 외부로 보내지 않는다
			throw new BusinessException(HttpStatus.FORBIDDEN, MaterialConceptService.DISABLED_MESSAGE);
		}
		boolean wantContent = contentProperties.enabled()
				&& (requested || (pre != null && pre.isMaterialContentUsed()));
		String excerpt = null;
		String contentNote = pre != null && pre.isMaterialContentUsed() && !contentProperties.enabled() ? "DISABLED"
				: null;
		if (wantContent) {
			if (material == null) {
				throw badRequest(CONTENT_NEEDS_MATERIAL_MESSAGE);
			}
			if (!Boolean.TRUE.equals(request.materialContentConsent())) {
				throw badRequest(CONTENT_CONSENT_MESSAGE);
			}
			MaterialTextExtractor.Extracted extracted = textExtractor.extract(material);
			if (extracted.status() != MaterialTextExtractor.Status.OK) {
				contentNote = extracted.status().name();
			} else if (personalInfoDetector.containsPersonalInfoExceptIp(extracted.text())) {
				// 자료 본문에 개인정보 패턴이 있으면 보내지 않고 과목·개념으로만 만든다
				contentNote = "PERSONAL_INFO";
			} else {
				excerpt = extracted.text();
			}
		}
		return new Spec(course, material == null ? null : material.getId(), material == null ? null : material.getTitle(),
				mode, List.copyOf(types), QuizTypePlan.plan(mode, types, count), difficulty, concepts, examScope,
				jobField, jobFieldOther, mockExam, kind, pre == null ? null : pre.getId(),
				preQuestions.stream().map(QuizQuestion::getContent).limit(MAX_AVOID_QUESTIONS).toList(), excerpt,
				contentNote, conceptSource);
	}

	// 사전평가 문제들이 다룬 개념 (과목 키워드 이름으로, 처음 나온 순서)
	private static List<String> preConcepts(Course course, List<QuizQuestion> questions) {
		Map<String, String> chosenByKey = new LinkedHashMap<>();
		questions.forEach(question -> question.getConcepts()
				.forEach(concept -> chosenByKey.putIfAbsent(concept.getConceptKey(), concept.getConceptName())));
		Set<String> chosen = new LinkedHashSet<>(chosenByKey.values());
		if (chosen.isEmpty()) {
			return concepts(course, null);
		}
		return List.copyOf(chosen);
	}

	// 요청 개념은 과목 키워드 안에서만 (대소문자 무시). 비었으면 과목 키워드 앞에서부터 MAX_CONCEPTS개
	private static List<String> concepts(Course course, List<String> requested) {
		Map<String, String> keywords = new LinkedHashMap<>();
		course.getKeywords().forEach(keyword -> keywords.putIfAbsent(Course.nameKey(keyword), keyword));
		if (keywords.isEmpty()) {
			throw badRequest(NO_CONCEPTS_MESSAGE);
		}
		if (requested == null || requested.stream().noneMatch(StringUtils::hasText)) {
			return keywords.values().stream().limit(MAX_CONCEPTS).toList();
		}
		Set<String> chosen = new LinkedHashSet<>();
		for (String concept : requested) {
			if (!StringUtils.hasText(concept)) {
				continue;
			}
			String name = keywords.get(Course.nameKey(concept.strip()));
			if (name == null) {
				throw badRequest(UNKNOWN_CONCEPT_MESSAGE);
			}
			chosen.add(name);
		}
		if (chosen.size() > MAX_CONCEPTS) {
			throw badRequest("개념은 한 번에 " + MAX_CONCEPTS + "개까지 고를 수 있어요.");
		}
		return List.copyOf(chosen);
	}

	// 개념별 참고 상태: 최근 대화 분석의 다수 의견, 지난 퀴즈 점수, 내 피드백 (모두 코드가 만든 값)
	private List<QuizPrompt.ConceptHint> hints(Long userId, Spec spec, boolean includeAnalysis) {
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
	private QuizResponse save(Long userId, Long generationId, Spec spec, QuizGenerationParser.ParsedQuiz parsed,
			LlmClient client, Attempt attempt) {
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		LocalDateTime now = LocalDateTime.now(clock);
		QuizGeneration generation = generationRepository.findById(generationId).orElse(null);
		if (generation == null || !generation.isActiveAt(now, properties.runningTimeout())) {
			if (generation != null) {
				generation.timeOut(now);
			}
			attempt.errorCode = "TIMED_OUT";
			return null;
		}
		Quiz quiz = quizRepository.save(Quiz.generated(userId, spec.course().getId(), spec.materialId(), title(spec),
				spec.mode(), spec.difficulty(), spec.jobField(), spec.jobFieldOther(), spec.examScope(),
				client.provider().apiValue(), client.model(), parsed.suggestions(), spec.mockExam(), now)
				.asAssessment(spec.assessmentKind(), spec.preAssessmentId())
				.withMaterialContentUsed(spec.materialExcerpt() != null)
				.withConceptSource(spec.conceptSource()));
		List<QuizQuestion> questions = new ArrayList<>();
		for (int i = 0; i < parsed.questions().size(); i++) {
			questions.add(parsed.questions().get(i).toEntity(quiz.getId(), i));
		}
		List<QuizQuestion> saved = questionRepository.saveAll(questions);
		generation.succeed(quiz.getId(), client.provider().apiValue(), client.model(), attempt.inputTokens,
				attempt.outputTokens, attempt.attempts, now);
		return QuizResponse.of(quiz, saved).withMaterialContentNote(spec.materialContentNote());
	}

	// 제목은 코드가 만든다 (출제 보장처럼 읽히는 제목이 생기지 않게)
	private static String title(Spec spec) {
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

	private static BusinessException badRequest(String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, message);
	}
}
