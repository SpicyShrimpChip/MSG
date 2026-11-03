package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.conversation.application.PersonalInfoDetector;
import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.quiz.application.QuizGrader.Submitted;
import com.ailms.backend.domain.quiz.dao.QuizAnswerRepository;
import com.ailms.backend.domain.quiz.dao.QuizGradingRunRepository;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dao.QuizSubmissionRepository;
import com.ailms.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.ailms.backend.domain.quiz.dto.QuizResponse;
import com.ailms.backend.domain.quiz.dto.QuizResultResponse;
import com.ailms.backend.domain.quiz.dto.QuizSubmitRequest;
import com.ailms.backend.domain.quiz.dto.QuizSummaryResponse;
import com.ailms.backend.domain.quiz.entity.GradingMethod;
import com.ailms.backend.domain.quiz.entity.GradingStatus;
import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import com.ailms.backend.domain.quiz.entity.QuizGradingRun;
import com.ailms.backend.domain.quiz.entity.QuizAnswer.GradeOutcome;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.entity.JobField;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.security.ApiErrorResponder;
import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmClients;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 내 퀴즈 풀기와 채점. 사용자 id는 항상 토큰의 값만 받고, 남의 퀴즈는 없는 퀴즈와 같게 404로 응답한다.
 * 풀기 전 응답에는 정답·허용 답·모범 답안이 없다. 같은 퀴즈를 여러 번 풀면 제출 기록이 쌓인다.
 *
 * <p>채점: 객관식은 QuizGrader(코드), 주관식은 허용 답 일치(ShortAnswerMatcher, 코드) 후 일치하지 않는 답만 AI 의미 판단,
 * 서술형은 AI가 채점 기준별 수준을 고르고 점수는 코드가 더한다(AnswerGradingService). AI를 부르는 동안 DB 트랜잭션을 잡지 않는다.
 * AI 채점을 할 수 없으면(모델 없음·동의 없음·한도 초과·호출 실패) 채점 완료로 치지 않고 "채점 대기"로 저장하며,
 * 나중에 regrade로 다시 채점할 수 있다. 학습자 답은 AI로 보내기 전에 개인정보 패턴을 검사해 있으면 422로 돌려보낸다.
 */
@Slf4j
@Service
public class QuizService {

	static final String NOT_FOUND_MESSAGE = "퀴즈를 찾을 수 없어요.";
	static final String SUBMISSION_NOT_FOUND_MESSAGE = "제출 기록을 찾을 수 없어요.";
	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String GRADING_RUNNING_MESSAGE = "이 제출을 채점하고 있어요. 끝난 뒤 다시 확인해 주세요.";
	static final String REGRADE_CONSENT_MESSAGE = "AI로 다시 채점하려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.";
	static final String REGRADE_NO_MODEL_MESSAGE = "연결된 AI 모델이 없어 지금은 다시 채점할 수 없어요.";
	static final String PERSONAL_INFO_MESSAGE = "답에 이메일·전화번호 같은 개인정보가 있어요. 지우고 다시 제출해 주세요.";

	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizSubmissionRepository submissionRepository;
	private final QuizAnswerRepository answerRepository;
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final AnswerGradingService gradingService;
	private final QuizStatsService statsService;
	private final QuizGradingRunRepository gradingRunRepository;
	private final QuizAiUsage usage;
	private final LlmClients llmClients;
	private final PersonalInfoDetector personalInfoDetector;
	private final QuizProperties properties;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public QuizService(QuizRepository quizRepository, QuizQuestionRepository questionRepository,
			QuizSubmissionRepository submissionRepository, QuizAnswerRepository answerRepository,
			CourseRepository courseRepository, UserRepository userRepository, AnswerGradingService gradingService,
			QuizStatsService statsService, QuizGradingRunRepository gradingRunRepository, QuizAiUsage usage,
			LlmClients llmClients, PersonalInfoDetector personalInfoDetector,
			QuizProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
		this.quizRepository = quizRepository;
		this.questionRepository = questionRepository;
		this.submissionRepository = submissionRepository;
		this.answerRepository = answerRepository;
		this.courseRepository = courseRepository;
		this.userRepository = userRepository;
		this.gradingService = gradingService;
		this.statsService = statsService;
		this.gradingRunRepository = gradingRunRepository;
		this.usage = usage;
		this.llmClients = llmClients;
		this.personalInfoDetector = personalInfoDetector;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	// 최근에 만든 퀴즈부터
	@Transactional(readOnly = true)
	public List<QuizSummaryResponse> getQuizzes(Long userId) {
		List<Quiz> quizzes = quizRepository.findAllByUserIdOrderByIdDesc(userId);
		if (quizzes.isEmpty()) {
			return List.of();
		}
		List<Long> ids = quizzes.stream().map(Quiz::getId).toList();
		Map<Long, Integer> questionCounts = new HashMap<>();
		for (Object[] row : questionRepository.countByQuizIds(ids)) {
			questionCounts.put((Long) row[0], ((Number) row[1]).intValue());
		}
		Map<Long, QuizSubmission> latest = submissionRepository.findLatestByQuizIds(userId, ids).stream()
				.collect(Collectors.toMap(QuizSubmission::getQuizId, Function.identity()));
		return quizzes.stream()
				.map(quiz -> QuizSummaryResponse.of(quiz, questionCounts.getOrDefault(quiz.getId(), 0),
						latest.get(quiz.getId())))
				.toList();
	}

	@Transactional(readOnly = true)
	public QuizResponse getQuiz(Long userId, Long id) {
		Quiz quiz = find(userId, id);
		return QuizResponse.of(quiz, questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId()));
	}

	public QuizResultResponse submit(Long userId, Long id, QuizSubmitRequest request) {
		Quiz quiz = transaction.execute(status -> find(userId, id));
		List<QuizQuestion> questions = transaction
				.execute(status -> questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId()));
		List<Submitted> submitted = QuizGrader.collect(questions, request.answers());
		Map<Long, GradeOutcome> outcomes = new HashMap<>();
		List<AnswerGradingService.Item> needAi = new ArrayList<>();
		for (Submitted answer : submitted) {
			QuizQuestion question = answer.question();
			if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
				outcomes.put(question.getId(), GradeOutcome.code(answer.multipleChoiceCorrect()));
			} else if (answer.isBlank()) {
				outcomes.put(question.getId(), noAnswer(question));
			} else if (question.getType() == QuestionType.SHORT_ANSWER
					&& ShortAnswerMatcher.matches(answer.text(), question.getAcceptedAnswers())) {
				outcomes.put(question.getId(), GradeOutcome.matched(true));
			} else {
				needAi.add(new AnswerGradingService.Item(question, answer.text()));
			}
		}
		// AI로 보낼 수 없는 이유(동의·모델 없음)가 있으면 답은 보내지 않으므로 개인정보 검사도 하지 않는다
		String blocked = needAi.isEmpty() ? null : blockedReason(userId);
		if (!needAi.isEmpty() && blocked == null) {
			rejectPersonalInfo(needAi);
		}
		// (1) 제출과 답을 먼저 저장한다. AI가 채점할 답은 대기(PENDING)로 두고, 보낼 수 있으면 채점 실행을 예약한다
		Reserved reserved = transaction.execute(status -> {
			String note = blocked;
			QuizGradingRun run = null;
			if (!needAi.isEmpty() && note == null) {
				lockUser(userId);
				note = limitReason(userId);
			}
			for (AnswerGradingService.Item item : needAi) {
				outcomes.put(item.question().getId(), GradeOutcome.pending(note == null ? GradingNotes.GRADING : note,
						item.question().maxScore(), 0));
			}
			List<GradeOutcome> ordered = submitted.stream().map(s -> outcomes.get(s.question().getId())).toList();
			Totals totals = Totals.of(ordered);
			QuizSubmission submission = submissionRepository.save(QuizSubmission.of(quiz.getId(), userId,
					totals.correct(), submitted.size(), totals.score(), totals.maxScore(), totals.pending(),
					totals.review(), LocalDateTime.now(clock)));
			answerRepository.saveAll(submitted.stream()
					.map(s -> QuizAnswer.from(submission.getId(), s.question().getId(), s.choiceIndex(), s.text(),
							outcomes.get(s.question().getId())))
					.toList());
			if (!needAi.isEmpty() && note == null) {
				run = reserveRun(userId, submission.getId(), QuizGradingRun.Kind.INITIAL, needAi.size());
			}
			return new Reserved(submission.getId(), run == null ? null : run.getId(),
					run == null ? null : run.getDeadlineAt());
		});
		// (2) 예약했으면 DB 트랜잭션 밖에서 모델을 부르고, (3) 마감 전이면 결과를 반영한다
		if (reserved.runId() != null) {
			executeRun(userId, quiz, reserved, needAi);
		}
		return transaction.execute(status -> result(userId, quiz.getId(),
				submissionRepository.findById(reserved.submissionId()).orElseThrow()));
	}

	// 아직 안 풀었으면 null
	@Transactional(readOnly = true)
	public QuizResultResponse getLatestResult(Long userId, Long id) {
		Quiz quiz = find(userId, id);
		return submissionRepository.findFirstByQuizIdAndUserIdOrderByIdDesc(quiz.getId(), userId)
				.map(submission -> result(userId, quiz.getId(), submission))
				.orElse(null);
	}

	/**
	 * 채점 대기(PENDING)인 답만 다시 AI로 채점한다. 이미 채점이 끝났거나 확인 필요인 답은 바꾸지 않는다.
	 * 처음 채점과 같은 한도(24시간 실행 수·모델 요청 수)를 쓰고, 모델을 부르기 전에 실행을 예약한다(사용자 행 잠금 안에서
	 * 한도 확인과 예약을 함께 하므로 동시 요청으로 한도를 넘지 못한다). 같은 제출의 채점이 진행 중이면 409,
	 * 한도를 넘으면 429이고 답과 결과는 그대로 둔다. 동의가 없으면 403, 모델이 없으면 503.
	 */
	public QuizResultResponse regrade(Long userId, Long quizId, Long submissionId) {
		Quiz quiz = transaction.execute(status -> find(userId, quizId));
		List<QuizQuestion> questions = transaction
				.execute(status -> questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId()));
		Map<Long, QuizQuestion> byId = questions.stream()
				.collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
		List<AnswerGradingService.Item> pending = new ArrayList<>();
		Reserved reserved = transaction.execute(status -> {
			lockUser(userId);
			findSubmission(userId, quiz.getId(), submissionId);
			LocalDateTime now = LocalDateTime.now(clock);
			expireStaleRuns(userId, now);
			if (gradingRunRepository.findAllByUserIdAndStatus(userId, QuizGradingRun.Status.RUNNING).stream()
					.anyMatch(run -> run.getSubmissionId().equals(submissionId))) {
				throw new BusinessException(HttpStatus.CONFLICT, GRADING_RUNNING_MESSAGE);
			}
			List<QuizAnswer> answers = answerRepository.findAllBySubmissionId(submissionId).stream()
					.filter(answer -> answer.getGradingStatus() == GradingStatus.PENDING && answer.getTextAnswer() != null
							&& byId.containsKey(answer.getQuestionId()))
					.toList();
			if (answers.isEmpty()) {
				return new Reserved(submissionId, null, null);
			}
			String blocked = blockedReason(userId);
			if (GradingNotes.NO_CONSENT.equals(blocked)) {
				throw new BusinessException(HttpStatus.FORBIDDEN, REGRADE_CONSENT_MESSAGE);
			}
			if (blocked != null) {
				throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, REGRADE_NO_MODEL_MESSAGE);
			}
			String limit = limitReason(userId);
			if (limit != null) {
				throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, GradingNotes.message(limit));
			}
			answers.forEach(answer -> pending.add(
					new AnswerGradingService.Item(byId.get(answer.getQuestionId()), answer.getTextAnswer())));
			rejectPersonalInfo(pending);
			QuizGradingRun run = reserveRun(userId, submissionId, QuizGradingRun.Kind.REGRADE, answers.size());
			// 지금 채점하는 답으로 표시한다 (마감이 지나도 남아 있으면 다음 다시 채점 대상)
			answers.forEach(answer -> answer.apply(GradeOutcome.pending(GradingNotes.GRADING, answer.getMaxScore(), 0)));
			return new Reserved(submissionId, run.getId(), run.getDeadlineAt());
		});
		if (reserved.runId() != null) {
			executeRun(userId, quiz, reserved, pending);
		}
		return transaction.execute(status -> result(userId, quiz.getId(), findSubmission(userId, quiz.getId(),
				submissionId)));
	}

	private record Reserved(Long submissionId, Long runId, LocalDateTime deadlineAt) {
	}

	/**
	 * 예약한 실행으로 모델을 부르고 결과를 반영한다. 반영은 실행이 아직 진행 중이고 마감 전일 때만 하고(늦은 결과는 버림),
	 * 어떤 이유로 끝나든 실행을 진행 중으로 남기지 않는다. 반영하지 못한 답은 MODEL_FAILED 대기로 남겨 다시 채점할 수 있다.
	 */
	private void executeRun(Long userId, Quiz quiz, Reserved reserved, List<AnswerGradingService.Item> items) {
		Map<Long, GradeOutcome> outcomes = Map.of();
		try {
			List<LlmClient> clients = llmClients.all().stream().filter(LlmClient::isConfigured).toList();
			outcomes = gradingService.grade(clients, quiz.getMode(), jobLabel(quiz), items,
					properties.gradingMaxOutputTokens(), userId, reserved.runId(),
					reserved.deadlineAt().atZone(clock.getZone()).toInstant());
		} catch (RuntimeException e) {
			log.error("AI 채점 중 예상하지 못한 오류 (run {})", reserved.runId(), e);
		}
		Map<Long, GradeOutcome> results = outcomes;
		transaction.executeWithoutResult(status -> {
			lockUser(userId);
			QuizGradingRun run = gradingRunRepository.findById(reserved.runId()).orElse(null);
			LocalDateTime now = LocalDateTime.now(clock);
			boolean active = run != null && run.isActiveAt(now);
			List<QuizAnswer> answers = answerRepository.findAllBySubmissionId(reserved.submissionId());
			for (QuizAnswer answer : answers) {
				if (answer.getGradingStatus() != GradingStatus.PENDING
						|| !GradingNotes.GRADING.equals(answer.getGradingNote())) {
					continue;
				}
				GradeOutcome outcome = active ? results.get(answer.getQuestionId()) : null;
				answer.apply(outcome != null ? outcome
						: GradeOutcome.pending(GradingNotes.MODEL_FAILED, answer.getMaxScore(), 0));
			}
			Totals totals = Totals.ofAnswers(answers);
			submissionRepository.findById(reserved.submissionId()).ifPresent(submission -> submission
					.updateTotals(totals.correct(), totals.score(), totals.maxScore(), totals.pending(),
							totals.review()));
			if (run != null) {
				run.finish(!active ? QuizGradingRun.Status.TIMED_OUT
						: results.values().stream().allMatch(o -> o.status() == GradingStatus.PENDING)
								? QuizGradingRun.Status.FAILED : QuizGradingRun.Status.SUCCEEDED, now);
			}
		});
	}

	// 사용자 행을 잠근 트랜잭션 안에서: 24시간 실행 수 → 실제 모델 요청 수 순서로 본다. 넘었으면 이유 코드
	private String limitReason(Long userId) {
		if (gradingRunRepository.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusHours(24))
				>= properties.dailyGradingLimit()) {
			return GradingNotes.LIMIT_EXCEEDED;
		}
		return usage.modelCallBudgetExhausted(userId) ? GradingNotes.BUDGET_EXCEEDED : null;
	}

	private QuizGradingRun reserveRun(Long userId, Long submissionId, QuizGradingRun.Kind kind, int answerCount) {
		LocalDateTime now = LocalDateTime.now(clock);
		return gradingRunRepository.save(QuizGradingRun.start(userId, submissionId, kind, answerCount, now,
				now.plus(properties.gradingDeadline())));
	}

	// 마감이 지난 진행 중 실행을 끝내고, 그 실행이 채점하던 답을 다시 채점할 수 있게 둔다 (서버가 재시작돼 남은 실행 포함)
	private void expireStaleRuns(Long userId, LocalDateTime now) {
		for (QuizGradingRun run : gradingRunRepository.findAllByUserIdAndStatus(userId, QuizGradingRun.Status.RUNNING)) {
			if (!run.isActiveAt(now) && run.finish(QuizGradingRun.Status.TIMED_OUT, now)) {
				List<QuizAnswer> answers = answerRepository.findAllBySubmissionId(run.getSubmissionId());
				answers.stream()
						.filter(answer -> answer.getGradingStatus() == GradingStatus.PENDING
								&& GradingNotes.GRADING.equals(answer.getGradingNote()))
						.forEach(answer -> answer.apply(
								GradeOutcome.pending(GradingNotes.MODEL_FAILED, answer.getMaxScore(), 0)));
			}
		}
	}

	private void lockUser(Long userId) {
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
	}

	private static String jobLabel(Quiz quiz) {
		return quiz.getJobField() == null ? null
				: quiz.getJobField() == JobField.OTHER ? quiz.getJobFieldOther() : quiz.getJobField().label();
	}

	// 과목의 개념별 퀴즈 결과 (학습 분석 대시보드와 나란히 쓴다)
	@Transactional(readOnly = true)
	public List<ConceptQuizStatsResponse> conceptStats(Long userId, Long courseId) {
		Course course = courseRepository.findByIdAndUserId(courseId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE));
		return statsService.byCourse(userId, course);
	}

	// AI 채점을 할 수 없는 이유 (가짜 채점을 만들지 않고 대기로 둔다). 보낼 수 있으면 null
	private String blockedReason(Long userId) {
		boolean consent = userRepository.findById(userId).map(user -> user.isAiQuizConsent()).orElse(false);
		if (!consent) {
			return GradingNotes.NO_CONSENT;
		}
		return llmClients.all().stream().anyMatch(LlmClient::isConfigured) ? null : GradingNotes.NOT_CONFIGURED;
	}

	private void rejectPersonalInfo(List<AnswerGradingService.Item> items) {
		List<Long> questionIds = items.stream()
				.filter(item -> personalInfoDetector.containsPersonalInfoExceptIp(item.answer()))
				.map(item -> item.question().getId())
				.toList();
		if (!questionIds.isEmpty()) {
			throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, PERSONAL_INFO_MESSAGE,
					Map.of("questionIds", questionIds));
		}
	}

	private static GradeOutcome noAnswer(QuizQuestion question) {
		return new GradeOutcome(GradingStatus.GRADED,
				question.getType() == QuestionType.ESSAY ? GradingMethod.CODE : GradingMethod.MATCH,
				GradingNotes.NO_ANSWER, 0, question.maxScore(), 0, 0, null, null, null, null, List.of());
	}

	private QuizResultResponse result(Long userId, Long quizId, QuizSubmission submission) {
		Quiz quiz = find(userId, quizId);
		List<QuizQuestion> questions = questionRepository.findAllByQuizIdOrderBySortOrderAsc(quizId);
		Map<Long, QuizAnswer> answers = answerRepository.findAllBySubmissionId(submission.getId()).stream()
				.collect(Collectors.toMap(QuizAnswer::getQuestionId, Function.identity(), (a, b) -> a));
		List<QuizAssessor.Graded> graded = questions.stream()
				.map(question -> {
					QuizAnswer answer = answers.get(question.getId());
					return answer == null
							? new QuizAssessor.Graded(question, GradingStatus.GRADED, 0, question.maxScore(), null)
							: new QuizAssessor.Graded(question, answer.getGradingStatus(), answer.getScore(),
									answer.getMaxScore(), answer.getGradingNote());
				})
				.toList();
		return QuizResultResponse.of(quiz, submission, questions, answers,
				QuizAssessor.assess(graded, properties.assessment()));
	}

	private Quiz find(Long userId, Long id) {
		return quizRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}

	private QuizSubmission findSubmission(Long userId, Long quizId, Long submissionId) {
		return submissionRepository.findById(submissionId)
				.filter(s -> s.getUserId().equals(userId) && s.getQuizId().equals(quizId))
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, SUBMISSION_NOT_FOUND_MESSAGE));
	}

	// 제출 합계 (채점이 끝난 답만 점수에 넣는다)
	private record Totals(int correct, int score, int maxScore, int pending, int review) {

		static Totals of(List<GradeOutcome> outcomes) {
			int correct = 0;
			int score = 0;
			int maxScore = 0;
			int pending = 0;
			int review = 0;
			for (GradeOutcome outcome : outcomes) {
				switch (outcome.status()) {
					case GRADED -> {
						score += outcome.score();
						maxScore += outcome.maxScore();
						correct += outcome.score() == outcome.maxScore() ? 1 : 0;
					}
					case PENDING -> pending++;
					case NEEDS_REVIEW -> review++;
				}
			}
			return new Totals(correct, score, maxScore, pending, review);
		}

		static Totals ofAnswers(List<QuizAnswer> answers) {
			return of(answers.stream()
					.map(a -> new GradeOutcome(a.getGradingStatus(), a.getGradingMethod(), a.getGradingNote(),
							a.getScore(), a.getMaxScore(), 0, 0, null, null, null, null, List.of()))
					.toList());
		}
	}
}
