package com.ailms.backend.domain.curriculum.application;

import com.ailms.backend.domain.analysis.dao.AnalysisRunRepository;
import com.ailms.backend.domain.analysis.dao.ConceptConsensusRepository;
import com.ailms.backend.domain.analysis.entity.AnalysisRunStatus;
import com.ailms.backend.domain.analysis.entity.ConceptConsensus;
import com.ailms.backend.domain.analysis.entity.ConsensusOutcome;
import com.ailms.backend.domain.analysis.entity.UnderstandingStatus;
import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.curriculum.dto.CurriculumResponse;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.quiz.application.QuizProperties;
import com.ailms.backend.domain.quiz.application.QuizStatsService;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizSuggestion;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.entity.LearningPurpose;
import com.ailms.backend.global.error.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 과목의 맞춤 학습 순서와 복습 추천 (코드 규칙, AI를 부르지 않는다). 근거는 세 가지를 섞지 않고 나란히 쓴다:
 * 퀴즈 결과(QuizStatsService, 채점이 끝난 답만), 최근 대화 분석의 다수 의견, 내 피드백.
 *
 * <p>단계와 순서: (1) 더 공부할래요 → (2) 퀴즈 보완(득점률이 기준 미만) → (3) 대화 분석 이해 부족·부분 이해·의견 불일치(퀴즈 근거 없음)
 * → (4) 추가 확인 필요(채점 대기·확인 필요, 문항 부족, 이미 알아요 미확인) → (5) 아직 근거 없음 → (6) 다음 단계 준비.
 * 같은 단계는 과목 키워드 순서. 숙련도 점수는 미결이라 만들지 않는다. 피드백은 순서에만 반영하고 AI 판단·퀴즈 결과를 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {

	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final int MAX_AI_SUGGESTIONS = 6;
	// 이만큼은 채점이 끝나야 "다음 단계 준비"라고 말한다
	static final int READY_MIN_GRADED = 2;

	enum Stage {
		PRIORITY_STUDY, REVIEW, ANALYSIS_WEAK, NEEDS_CHECK, NOT_ASSESSED, READY;

		String apiValue() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final MaterialRepository materialRepository;
	private final QuizRepository quizRepository;
	private final AnalysisRunRepository runRepository;
	private final ConceptConsensusRepository consensusRepository;
	private final QuizStatsService statsService;
	private final QuizProperties properties;
	private final CurriculumFlowBuilder flowBuilder;

	public CurriculumResponse get(Long userId, Long courseId) {
		Course course = courseRepository.findByIdAndUserId(courseId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE));
		LearningPurpose purpose = userRepository.findById(userId).map(user -> user.getLearningPurpose())
				.orElse(LearningPurpose.UNDECIDED);
		String mode = switch (purpose) {
			case EXAM_PREP -> "exam_prep";
			case WORK -> "work";
			default -> "self_development";
		};
		Map<String, ConceptConsensus> consensus = runRepository
				.findFirstByUserIdAndCourseIdAndStatusInOrderByIdDesc(userId, courseId,
						Set.of(AnalysisRunStatus.COMPLETED, AnalysisRunStatus.PARTIAL))
				.map(run -> consensusRepository.findAllByRunIdOrderByIdAsc(run.getId()).stream()
						.collect(Collectors.toMap(ConceptConsensus::getConceptKey, Function.identity(), (a, b) -> a)))
				.orElse(Map.of());
		List<ConceptQuizStatsResponse> stats = statsService.byCourse(userId, course);
		List<Material> courseMaterials = materialRepository.findAllByUserIdAndCourseId(userId, courseId);
		List<CurriculumResponse.MaterialRef> materials = courseMaterials.stream()
				.map(material -> new CurriculumResponse.MaterialRef(material.getId(), material.getTitle()))
				.toList();

		List<Planned> planned = new ArrayList<>();
		for (int i = 0; i < stats.size(); i++) {
			planned.add(plan(i, stats.get(i), consensus.get(stats.get(i).conceptId()), mode, !materials.isEmpty()));
		}
		planned.sort(Comparator.comparing(Planned::stage).thenComparingInt(Planned::keywordOrder));
		List<CurriculumResponse.Step> steps = new ArrayList<>();
		for (int i = 0; i < planned.size(); i++) {
			Planned item = planned.get(i);
			steps.add(new CurriculumResponse.Step(i + 1, item.stat().conceptId(), item.stat().name(),
					item.stage().apiValue(), item.reasons(), item.evidence(), item.actions()));
		}
		return new CurriculumResponse(course.getId(), course.getName(), purpose.apiValue(), mode, steps, materials,
				aiSuggestions(userId, courseId), flowBuilder.build(userId, course, courseMaterials));
	}

	private record Planned(Stage stage, int keywordOrder, ConceptQuizStatsResponse stat, List<String> reasons,
			CurriculumResponse.Evidence evidence, List<CurriculumResponse.Action> actions) {
	}

	private Planned plan(int keywordOrder, ConceptQuizStatsResponse stat, ConceptConsensus agreed, String mode,
			boolean hasMaterials) {
		double minRate = properties.assessment().conceptMinRate();
		double readyRate = properties.assessment().readyRate();
		List<String> reasons = new ArrayList<>();
		boolean wantToStudy = "want_to_study".equals(stat.feedback());
		boolean alreadyKnow = "already_know".equals(stat.feedback());
		boolean hasQuiz = stat.gradedCount() > 0;
		double rate = stat.maxScore() == 0 ? 0 : (double) stat.score() / stat.maxScore();
		boolean unsettled = stat.pendingCount() > 0 || stat.reviewCount() > 0;
		boolean analysisWeak = agreed != null && (agreed.getOutcome() == ConsensusOutcome.DISAGREEMENT
				|| agreed.getStatus() == UnderstandingStatus.NOT_UNDERSTOOD
				|| agreed.getStatus() == UnderstandingStatus.PARTIAL);

		if (hasQuiz) {
			reasons.add("퀴즈에서 채점이 끝난 " + stat.gradedCount() + "문제의 득점은 " + stat.score() + "/" + stat.maxScore()
					+ "점(" + Math.round(rate * 100) + "%)이에요.");
		}
		if (unsettled) {
			reasons.add("채점을 기다리거나 확인이 필요한 답이 " + (stat.pendingCount() + stat.reviewCount())
					+ "개 있어 그 답은 근거에서 뺐어요.");
		}
		if (agreed != null) {
			reasons.add(analysisSentence(agreed));
		}

		Stage stage;
		if (wantToStudy) {
			stage = Stage.PRIORITY_STUDY;
			reasons.add(0, "'더 공부할래요'를 골라 맨 앞에 두었어요.");
		} else if (hasQuiz && rate < minRate) {
			stage = Stage.REVIEW;
			reasons.add("득점률이 보완 기준(" + Math.round(minRate * 100) + "%)보다 낮아요.");
		} else if (!hasQuiz && analysisWeak) {
			stage = Stage.ANALYSIS_WEAK;
			reasons.add("아직 퀴즈 근거가 없어 대화 분석 결과로 순서를 정했어요. 퀴즈로 확인해 보세요.");
		} else if (unsettled || (hasQuiz && stat.gradedCount() < READY_MIN_GRADED)
				|| (alreadyKnow && !"confirmed".equals(stat.confirmation()))) {
			stage = Stage.NEEDS_CHECK;
			if (alreadyKnow && !"confirmed".equals(stat.confirmation())) {
				reasons.add("'이미 알아요'를 골랐지만 확인 퀴즈 결과가 아직 충분하지 않아요.");
			} else if (hasQuiz && stat.gradedCount() < READY_MIN_GRADED) {
				reasons.add("채점이 끝난 문제가 " + READY_MIN_GRADED + "개보다 적어 판단하기 일러요.");
			}
		} else if (!hasQuiz && agreed == null) {
			stage = Stage.NOT_ASSESSED;
			reasons.add("아직 이 개념을 확인한 퀴즈나 대화 분석이 없어요.");
		} else if (hasQuiz && rate >= readyRate) {
			stage = Stage.READY;
			reasons.add("득점률이 다음 단계 기준(" + Math.round(readyRate * 100) + "%) 이상이에요.");
		} else if (!hasQuiz) {
			// 대화 분석은 이해로 봤지만 퀴즈 근거가 없다: 대화만으로 단정하지 않는다
			stage = Stage.NEEDS_CHECK;
			reasons.add("대화 분석만으로는 단정하지 않아요. 퀴즈로 확인해 보세요.");
		} else {
			stage = Stage.NEEDS_CHECK;
			reasons.add("득점률이 보완 기준 이상이지만 다음 단계 기준(" + Math.round(readyRate * 100) + "%)에는 못 미쳐요.");
		}
		if (wantToStudy && hasQuiz && rate >= readyRate && !unsettled && stat.gradedCount() >= READY_MIN_GRADED) {
			reasons.add("퀴즈 결과는 충분해요. 원하면 다음 단계로 넘어가도 돼요.");
		}

		List<CurriculumResponse.Action> actions = new ArrayList<>();
		switch (stage) {
			case PRIORITY_STUDY, REVIEW, ANALYSIS_WEAK -> {
				if (hasMaterials) {
					actions.add(new CurriculumResponse.Action("study_material", "과목 자료 다시 보기", null));
				}
				actions.add(new CurriculumResponse.Action("review_quiz", "복습 퀴즈 풀기", mode));
			}
			case NEEDS_CHECK -> actions.add(new CurriculumResponse.Action("confirm_quiz", "확인 퀴즈 풀기",
					"self_development"));
			case NOT_ASSESSED -> actions.add(new CurriculumResponse.Action("first_quiz", "첫 퀴즈 풀기", mode));
			case READY -> actions.add(new CurriculumResponse.Action("next_step", "다음 단계 개념 보기", null));
		}
		if (stage == Stage.PRIORITY_STUDY && hasQuiz && rate >= readyRate) {
			actions.add(new CurriculumResponse.Action("next_step", "다음 단계 개념 보기", null));
		}
		CurriculumResponse.Evidence evidence = new CurriculumResponse.Evidence(stat.questionCount(),
				stat.gradedCount(), stat.correctCount(), stat.score(), stat.maxScore(), stat.pendingCount(),
				stat.reviewCount(), agreed == null || agreed.getStatus() == null ? null : agreed.getStatus().apiValue(),
				agreed == null ? null : agreed.getOutcome().apiValue(), stat.feedback(), stat.confirmation());
		return new Planned(stage, keywordOrder, stat, reasons, evidence, actions);
	}

	private static String analysisSentence(ConceptConsensus agreed) {
		if (agreed.getOutcome() == ConsensusOutcome.DISAGREEMENT) {
			return "대화 분석에서 모델 " + agreed.getJudgedCount() + "개의 의견이 서로 달랐어요.";
		}
		if (agreed.getStatus() == null) {
			return "대화 분석 결과가 없어요.";
		}
		String label = switch (agreed.getStatus()) {
			case UNDERSTOOD -> "이해";
			case PARTIAL -> "부분 이해";
			case NOT_UNDERSTOOD -> "이해 부족";
			case INSUFFICIENT_EVIDENCE -> "판단 근거 부족";
		};
		return "대화 분석에서 모델 " + agreed.getJudgedCount() + "개 중 " + agreed.getAgreeCount() + "개가 '" + label
				+ "'으로 봤어요.";
	}

	// 최근 퀴즈부터 AI 제안을 모은다 (같은 개념은 한 번만)
	private List<CurriculumResponse.AiSuggestion> aiSuggestions(Long userId, Long courseId) {
		List<CurriculumResponse.AiSuggestion> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Quiz quiz : quizRepository.findAllByUserIdAndCourseIdOrderByIdDesc(userId, courseId)) {
			for (QuizSuggestion suggestion : quiz.getSuggestions()) {
				if (result.size() < MAX_AI_SUGGESTIONS && seen.add(suggestion.getConcept().toLowerCase(Locale.ROOT))) {
					result.add(new CurriculumResponse.AiSuggestion(suggestion.getKind().apiValue(),
							suggestion.getConcept(), suggestion.getReason()));
				}
			}
		}
		return result;
	}
}
