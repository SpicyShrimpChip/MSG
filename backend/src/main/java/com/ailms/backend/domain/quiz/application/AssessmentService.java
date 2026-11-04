package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.quiz.dao.QuizAnswerRepository;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dao.QuizSubmissionRepository;
import com.ailms.backend.domain.quiz.dto.AssessmentComparisonResponse;
import com.ailms.backend.domain.quiz.dto.AssessmentCreateRequest;
import com.ailms.backend.domain.quiz.dto.QuizAiStatusResponse;
import com.ailms.backend.domain.quiz.dto.QuizCreateRequest;
import com.ailms.backend.domain.quiz.entity.AssessmentKind;
import com.ailms.backend.domain.quiz.entity.GradingStatus;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmClients;
import com.ailms.backend.global.llm.LlmProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 학습자료 기반 사전·사후평가. 평가는 퀴즈 구조(quiz·문제·제출·채점)를 그대로 쓰고 quiz.assessment_kind로 구분한다.
 * 만들기는 QuizGenerationService(같은 동의·한도·시간 예산·검증), 풀기·채점은 기존 퀴즈 API를 쓴다.
 * 비교는 서버 코드가 채점이 끝난 답만으로 센다(숙련도 점수 아님).
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

	static final String BASIS = "최신 사전평가(제출한 것 우선)와 그 사전평가에 이어 만든 최신 사후평가를, 각각 가장 최근 제출로 비교해요."
			+ " 점수는 채점이 끝난 답만 더해요.";
	static final String MATERIAL_NOT_FOUND_MESSAGE = "자료를 찾을 수 없어요.";
	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String NEED_TARGET_MESSAGE = "과목이나 자료를 골라 주세요.";
	static final String NO_COURSE_MESSAGE = "과목에 연결된 자료로만 평가를 만들 수 있어요. 자료를 과목에 연결해 주세요.";
	static final List<String> DEFAULT_TYPES = List.of("multiple_choice", "short_answer");
	static final int DEFAULT_COUNT = 6;

	private final QuizGenerationService generationService;
	private final MaterialRepository materialRepository;
	private final CourseRepository courseRepository;
	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizSubmissionRepository submissionRepository;
	private final QuizAnswerRepository answerRepository;
	private final QuizProperties properties;
	private final LlmClients llmClients;
	private final MaterialContentProperties contentProperties;

	public QuizGenerationService.Created create(Long userId, AssessmentCreateRequest request) {
		Material material = materialRepository.findByIdAndUserId(request.materialId(), userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MATERIAL_NOT_FOUND_MESSAGE));
		if (material.getCourseId() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NO_COURSE_MESSAGE);
		}
		return generationService.create(userId, new QuizCreateRequest(request.requestKey(), material.getCourseId(),
				StringUtils.hasText(request.mode()) ? request.mode() : "self_development",
				request.questionTypes() == null || request.questionTypes().isEmpty() ? DEFAULT_TYPES
						: request.questionTypes(),
				request.questionCount() == null ? DEFAULT_COUNT : request.questionCount(), request.difficulty(), null,
				null, null, null, material.getId(), null, request.kind(), request.useMaterialContent(),
				request.materialContentConsent(), null));
	}

	@Transactional(readOnly = true)
	public AssessmentComparisonResponse compare(Long userId, Long courseId, Long materialId) {
		List<Quiz> pres;
		Material material = null;
		if (materialId != null) {
			material = materialRepository.findByIdAndUserId(materialId, userId)
					.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MATERIAL_NOT_FOUND_MESSAGE));
			pres = quizRepository.findAllByUserIdAndMaterialIdAndAssessmentKindOrderByIdDesc(userId, materialId,
					AssessmentKind.PRE);
		} else if (courseId != null) {
			if (!courseRepository.existsByIdAndUserId(courseId, userId)) {
				throw new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE);
			}
			pres = quizRepository.findAllByUserIdAndCourseIdAndAssessmentKindOrderByIdDesc(userId, courseId,
					AssessmentKind.PRE);
		} else {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NEED_TARGET_MESSAGE);
		}
		Quiz pre = pickSubmittedFirst(userId, pres).orElse(null);
		if (pre == null) {
			return new AssessmentComparisonResponse(BASIS, material == null ? null
					: new AssessmentComparisonResponse.Material(material.getId(), material.getTitle()), null, null,
					List.of(), null, null);
		}
		if (material == null && pre.getMaterialId() != null) {
			material = materialRepository.findByIdAndUserId(pre.getMaterialId(), userId).orElse(null);
		}
		Quiz post = pickSubmittedFirst(userId, quizRepository.findAllByUserIdAndPreAssessmentIdOrderByIdDesc(userId,
				pre.getId())).orElse(null);
		Measured preMeasured = measure(userId, pre);
		Measured postMeasured = post == null ? null : measure(userId, post);
		double minRate = properties.assessment().conceptMinRate();
		boolean preDone = preMeasured.submission() != null;
		boolean postDone = postMeasured != null && postMeasured.submission() != null;
		List<AssessmentComparisonResponse.ConceptDelta> concepts = new ArrayList<>();
		Map<String, QuizAssessor.ConceptStat> preConcepts = preMeasured.byConcept();
		Map<String, QuizAssessor.ConceptStat> postConcepts = postMeasured == null ? Map.of() : postMeasured.byConcept();
		Map<String, String> names = new LinkedHashMap<>();
		preConcepts.values().forEach(stat -> names.put(stat.conceptKey(), stat.name()));
		postConcepts.values().forEach(stat -> names.putIfAbsent(stat.conceptKey(), stat.name()));
		names.forEach((key, name) -> {
			QuizAssessor.ConceptStat before = preDone ? preConcepts.get(key) : null;
			QuizAssessor.ConceptStat after = postDone ? postConcepts.get(key) : null;
			Integer beforeRate = rate(before);
			Integer afterRate = rate(after);
			Integer delta = beforeRate == null || afterRate == null ? null : afterRate - beforeRate;
			concepts.add(new AssessmentComparisonResponse.ConceptDelta(key, name,
					before == null ? null : before.score(), before == null ? null : before.maxScore(),
					after == null ? null : after.score(), after == null ? null : after.maxScore(), delta,
					status(beforeRate, afterRate, delta, postDone, minRate)));
		});
		Integer preRate = preDone && preMeasured.snapshot().maxScore() > 0
				? Math.round(preMeasured.snapshot().score() * 100f / preMeasured.snapshot().maxScore()) : null;
		Integer postRate = postDone && postMeasured.snapshot().maxScore() > 0
				? Math.round(postMeasured.snapshot().score() * 100f / postMeasured.snapshot().maxScore()) : null;
		return new AssessmentComparisonResponse(BASIS, material == null ? null
				: new AssessmentComparisonResponse.Material(material.getId(), material.getTitle()),
				preMeasured.snapshot(), postMeasured == null ? null : postMeasured.snapshot(), concepts,
				preRate == null || postRate == null ? null : postRate - preRate,
				comparability(userId, pre, post, preMeasured, postMeasured));
	}

	// 채점이 끝난 답이 없으면 null
	private static Integer rate(QuizAssessor.ConceptStat stat) {
		return stat == null || stat.maxScore() == 0 ? null : Math.round(stat.score() * 100f / stat.maxScore());
	}

	static String status(Integer before, Integer after, Integer delta, boolean postDone, double minRate) {
		int min = (int) Math.round(minRate * 100);
		if (!postDone) {
			return before == null ? "not_taken" : before < min ? "weak" : "ok";
		}
		if (after == null) {
			return "not_taken";
		}
		if (after < min) {
			return "still_weak";
		}
		if (delta == null || delta == 0) {
			return delta == null ? "ok" : "unchanged";
		}
		return delta > 0 ? "improved" : "declined";
	}

	// 사후평가가 사전평가와 같은 조건인지 코드로 확인하고, 다르거나 채점이 덜 끝났으면 한계를 적는다
	private AssessmentComparisonResponse.Comparability comparability(Long userId, Quiz pre, Quiz post,
			Measured preMeasured, Measured postMeasured) {
		List<String> notes = new ArrayList<>();
		if (post == null) {
			return new AssessmentComparisonResponse.Comparability(true, true, true, true,
					List.of("아직 사후평가가 없어 사전평가 결과만 보여 줘요."));
		}
		List<QuizQuestion> preQuestions = questionRepository.findAllByQuizIdOrderBySortOrderAsc(pre.getId());
		List<QuizQuestion> postQuestions = questionRepository.findAllByQuizIdOrderBySortOrderAsc(post.getId());
		java.util.Set<String> preKeys = conceptKeys(preQuestions);
		java.util.Set<String> postKeys = conceptKeys(postQuestions);
		boolean sameConcepts = preKeys.equals(postKeys);
		boolean sameCount = preQuestions.size() == postQuestions.size();
		boolean sameTypes = typeCounts(preQuestions).equals(typeCounts(postQuestions));
		boolean sameDifficulty = java.util.Objects.equals(pre.getDifficulty(), post.getDifficulty());
		if (!sameConcepts) {
			notes.add("사후평가가 다룬 개념이 사전평가와 달라요. 두 평가에 모두 있는 개념만 비교해 보세요.");
		}
		if (!sameCount || !sameTypes) {
			notes.add("문항 수나 문제 유형 구성이 달라 점수를 그대로 비교하기 어려워요.");
		}
		if (!sameDifficulty) {
			notes.add("난이도가 달라요.");
		}
		int unsettled = preMeasured.snapshot().pendingCount() + preMeasured.snapshot().reviewCount()
				+ (postMeasured == null ? 0 : postMeasured.snapshot().pendingCount() + postMeasured.snapshot().reviewCount());
		if (unsettled > 0) {
			notes.add("채점 대기·확인 필요 답 " + unsettled + "개는 점수에서 빠져 있어 비교가 정확하지 않을 수 있어요.");
		}
		if (pre.isMaterialContentUsed() != post.isMaterialContentUsed()) {
			notes.add("한 평가만 자료 본문을 바탕으로 만들어졌어요.");
		}
		notes.add("문제는 같은 개념으로 다시 만든 다른 문제라 개념별 문항이 적으면 점수가 크게 흔들릴 수 있어요.");
		return new AssessmentComparisonResponse.Comparability(sameConcepts, sameCount, sameTypes, sameDifficulty,
				notes);
	}

	private static java.util.Set<String> conceptKeys(List<QuizQuestion> questions) {
		java.util.Set<String> keys = new java.util.TreeSet<>();
		questions.forEach(question -> question.getConcepts().forEach(concept -> keys.add(concept.getConceptKey())));
		return keys;
	}

	private static Map<com.ailms.backend.domain.quiz.entity.QuestionType, Long> typeCounts(List<QuizQuestion> questions) {
		return questions.stream().collect(Collectors.groupingBy(QuizQuestion::getType, java.util.TreeMap::new,
				Collectors.counting()));
	}

	// 문제 만들기·채점에 쓸 AI (키 값 없이)
	public QuizAiStatusResponse aiStatus() {
		List<LlmClient> configured = llmClients.all().stream().filter(LlmClient::isConfigured).toList();
		Map<LlmProvider, LlmClient> byProvider = configured.stream()
				.collect(Collectors.toMap(LlmClient::provider, Function.identity()));
		List<LlmProvider> order = properties.generationProviders() == null ? List.of(LlmProvider.values())
				: properties.generationProviders();
		QuizAiStatusResponse.Model generation = order.stream().map(byProvider::get)
				.filter(java.util.Objects::nonNull).findFirst()
				.map(client -> new QuizAiStatusResponse.Model(client.provider().apiValue(), client.model()))
				.orElse(null);
		List<QuizAiStatusResponse.Model> grading = configured.stream()
				.map(client -> new QuizAiStatusResponse.Model(client.provider().apiValue(), client.model()))
				.toList();
		String message = configured.isEmpty()
				? "연결된 AI 모델이 없어요(AI 미설정). 문제 만들기와 주관식·서술형 AI 채점을 쓸 수 없고, 객관식 채점은 그대로 돼요."
				: configured.size() == 1 ? generation.provider().toUpperCase() + " 하나로 문제를 만들고 채점해요."
						+ " 서술형은 모델 하나의 판단이라 '추가 확인 필요'로 표시될 수 있어요."
						: generation.provider().toUpperCase() + "로 문제를 만들고, 연결된 모델 " + configured.size()
								+ "개가 따로 채점해 다수 의견을 써요.";
		return new QuizAiStatusResponse(!configured.isEmpty(), generation, grading, message,
				contentProperties.enabled());
	}

	private Optional<Quiz> pickSubmittedFirst(Long userId, List<Quiz> quizzes) {
		return quizzes.stream()
				.filter(quiz -> submissionRepository.findFirstByQuizIdAndUserIdOrderByIdDesc(quiz.getId(), userId)
						.isPresent())
				.findFirst()
				.or(() -> quizzes.stream().findFirst());
	}

	private record Measured(AssessmentComparisonResponse.Snapshot snapshot, QuizSubmission submission,
			Map<String, QuizAssessor.ConceptStat> byConcept) {
	}

	private Measured measure(Long userId, Quiz quiz) {
		List<QuizQuestion> questions = questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId());
		QuizSubmission submission = submissionRepository.findFirstByQuizIdAndUserIdOrderByIdDesc(quiz.getId(), userId)
				.orElse(null);
		if (submission == null) {
			return new Measured(new AssessmentComparisonResponse.Snapshot(quiz.getId(), quiz.getTitle(), null, null,
					questions.size(), 0, 0, 0, 0, 0), null, Map.of());
		}
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
		int gradedCount = (int) graded.stream().filter(g -> g.status() == GradingStatus.GRADED).count();
		Map<String, QuizAssessor.ConceptStat> byConcept = QuizAssessor
				.byConcept(graded, properties.assessment().conceptMinRate()).stream()
				.collect(Collectors.toMap(QuizAssessor.ConceptStat::conceptKey, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));
		return new Measured(new AssessmentComparisonResponse.Snapshot(quiz.getId(), quiz.getTitle(),
				submission.getId(), submission.getSubmittedAt(), questions.size(), gradedCount, submission.getScore(),
				submission.getMaxScore(), submission.getPendingCount(), submission.getReviewCount()), submission,
				byConcept);
	}
}
