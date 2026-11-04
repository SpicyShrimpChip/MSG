package com.ailms.backend.domain.curriculum.application;

import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.curriculum.dto.CurriculumResponse;
import com.ailms.backend.domain.material.dao.MaterialConceptRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialConcept;
import com.ailms.backend.domain.quiz.application.AssessmentService;
import com.ailms.backend.domain.quiz.application.QuizAssessor;
import com.ailms.backend.domain.quiz.application.QuizProperties;
import com.ailms.backend.domain.quiz.application.QuizStatsService;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dto.AssessmentComparisonResponse;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.study.application.ConceptStudyService;
import com.ailms.backend.domain.study.dto.ConceptStudyResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 과목의 4단계 학습 흐름(사전 테스트 → 핵심 개념 학습 → 개념별 확인 퀴즈 → 사후 평가)을 제출·채점 기록으로 계산한다. AI를 부르지 않는다.
 *
 * <ul>
 * <li>사전·사후 평가: 기존 비교(AssessmentService.compare)를 그대로 쓴다. 과목의 최신 사전평가(제출한 것 우선)와 그 사후평가.</li>
 * <li>개념 목록: 사전평가가 채점됐으면 그 개념(사후평가도 같은 개념), 아니면 과목 자료에서 뽑은 개념, 그것도 없으면 과목 키워드.</li>
 * <li>개념 학습·확인 퀴즈: 자료에서 뽑은 개념은 학습 기록(학습 완료)과 개념 확인 퀴즈 결과로 상태를 정한다(ConceptStudyService).
 * 과목 키워드뿐인 개념(학습 기록을 남길 곳이 없음)은 예전처럼 사전평가 제출 뒤 일반 퀴즈 결과로 본다.</li>
 * <li>먼저 공부할 개념(대상): 사전 테스트 기준 미만·미채점 개념, 없으면 전체.</li>
 * <li>지금 단계: 사전 미제출 → 사전 테스트 / 대상 중 학습 전·복습 필요 개념이 있음 → 개념 학습 / 학습 완료 뒤 확인 퀴즈를 안 푼 개념이 있음
 * → 확인 퀴즈 / 사후 미제출 → 사후 평가 / 모두 끝 → done. 이해 확인·추가 확인 필요(채점 대기 등)는 막지 않는다.</li>
 * </ul>
 * 학습 진행률은 실제 "학습 완료" 기록으로만 센다(페이지 열람·퀴즈 생성은 세지 않음). 사후 평가는 막지 않고 남은 학습을 안내한다. 숙련도 점수가 아니다.
 */
@Component
@RequiredArgsConstructor
class CurriculumFlowBuilder {

	static final String PRE_TEST = "pre_test";
	static final String CONCEPT_STUDY = "concept_study";
	static final String CONCEPT_QUIZ = "concept_quiz";
	static final String POST_TEST = "post_test";
	static final String DONE = "done";
	private static final List<String> ORDER = List.of(PRE_TEST, CONCEPT_STUDY, CONCEPT_QUIZ, POST_TEST);
	private static final Map<String, String> TITLES = Map.of(PRE_TEST, "사전 테스트", CONCEPT_STUDY, "핵심 개념 학습",
			CONCEPT_QUIZ, "개념별 확인 퀴즈", POST_TEST, "사후 평가");

	private final AssessmentService assessmentService;
	private final QuizRepository quizRepository;
	private final MaterialConceptRepository conceptRepository;
	private final QuizStatsService statsService;
	private final QuizProperties properties;
	private final ConceptStudyService studyService;

	// 대상 개념이 지금 해야 할 일
	private enum Need {
		STUDY, QUIZ, OK
	}

	CurriculumResponse.Flow build(Long userId, Course course, List<Material> materials) {
		AssessmentComparisonResponse comparison = assessmentService.compare(userId, course.getId(), null);
		int minPercent = (int) Math.round(properties.assessment().conceptMinRate() * 100);
		AssessmentComparisonResponse.Snapshot pre = comparison.pre();
		AssessmentComparisonResponse.Snapshot post = comparison.post();
		boolean preDone = pre != null && pre.submissionId() != null;
		boolean postDone = post != null && post.submissionId() != null;

		Map<Long, Material> materialById = materials.stream()
				.collect(Collectors.toMap(Material::getId, Function.identity()));
		CurriculumResponse.MaterialRef assessmentMaterial = comparison.material() != null
				? new CurriculumResponse.MaterialRef(comparison.material().id(), comparison.material().title())
				: materials.stream().max(Comparator.comparing(Material::getId))
						.map(m -> new CurriculumResponse.MaterialRef(m.getId(), m.getTitle())).orElse(null);

		// 개념 키 → 공부할 자료 개념 (사전평가 자료의 개념을 먼저, 그다음 과목의 다른 자료)
		Long preferred = assessmentMaterial == null ? null : assessmentMaterial.id();
		List<MaterialConcept> courseConcepts = conceptRepository.findAllByUserIdOrderByMaterialIdAscSortOrderAsc(userId)
				.stream().filter(c -> materialById.containsKey(c.getMaterialId())).toList();
		Map<String, MaterialConcept> studyByKey = new LinkedHashMap<>();
		courseConcepts.stream().filter(c -> c.getMaterialId().equals(preferred))
				.forEach(c -> studyByKey.putIfAbsent(c.getConceptKey(), c));
		courseConcepts.forEach(c -> studyByKey.putIfAbsent(c.getConceptKey(), c));
		Map<String, ConceptStudyResponse> studies = studyService.forCourse(userId, course.getId(),
				List.copyOf(studyByKey.values()));

		// 개념 목록과 출처
		Map<String, String> names = new LinkedHashMap<>();
		String source;
		if (preDone && !comparison.concepts().isEmpty()) {
			comparison.concepts().forEach(delta -> names.putIfAbsent(delta.conceptId(), delta.name()));
			source = quizRepository.findByIdAndUserId(pre.quizId(), userId).map(Quiz::getConceptSource)
					.map(value -> value.toLowerCase(Locale.ROOT))
					.orElse(names.keySet().stream().allMatch(studyByKey::containsKey) ? "material" : "course");
		} else if (!studyByKey.isEmpty()) {
			studyByKey.values().forEach(c -> names.putIfAbsent(c.getConceptKey(), c.getName()));
			source = "material";
		} else {
			course.getKeywords().forEach(keyword -> names.putIfAbsent(Course.nameKey(keyword), keyword));
			source = "course";
		}

		Map<String, AssessmentComparisonResponse.ConceptDelta> deltas = comparison.concepts().stream()
				.collect(Collectors.toMap(AssessmentComparisonResponse.ConceptDelta::conceptId, Function.identity(),
						(a, b) -> a));
		LocalDateTime since = preDone ? pre.submittedAt() : null;
		Map<String, QuizAssessor.ConceptStat> practice = statsService.practiceByConcept(userId, course.getId(), since);

		List<CurriculumResponse.FlowConcept> concepts = new ArrayList<>();
		names.forEach((key, name) -> {
			AssessmentComparisonResponse.ConceptDelta delta = deltas.get(key);
			Integer preRate = !preDone || delta == null ? null : percent(delta.preScore(), delta.preMaxScore());
			String preStatus = preRate == null ? "not_taken" : preRate < minPercent ? "weak" : "ok";
			QuizAssessor.ConceptStat stat = practice.get(key);
			int graded = stat == null ? 0 : stat.gradedCount();
			int score = stat == null ? 0 : stat.score();
			int max = stat == null ? 0 : stat.maxScore();
			int waiting = stat == null ? 0 : stat.pendingCount() + stat.reviewCount();
			Integer practiceRate = graded == 0 ? null : percent(score, max);
			String practiceStatus = graded == 0 ? (waiting > 0 ? "pending" : "not_started")
					: practiceRate == null || practiceRate < minPercent ? "weak" : "ok";
			MaterialConcept studyConcept = studyByKey.get(key);
			Long materialId = studyConcept != null ? studyConcept.getMaterialId() : preferred;
			Material material = materialId == null ? null : materialById.get(materialId);
			ConceptStudyResponse study = studyConcept == null ? null
					: studies.get(ConceptStudyService.key(studyConcept.getMaterialId(), key));
			concepts.add(new CurriculumResponse.FlowConcept(key, name, materialId,
					material == null ? null : material.getTitle(), studyConcept != null && studyConcept.hasStudyNote(),
					preRate, preStatus, graded, score, max, practiceStatus,
					postDone && delta != null ? percent(delta.postScore(), delta.postMaxScore()) : null,
					postDone && delta != null ? delta.delta() : null,
					postDone && delta != null ? delta.status() : null,
					preDone && !"ok".equals(preStatus), study));
		});

		// 대상: 사전 테스트에서 먼저 공부할 개념, 없으면 전체
		List<CurriculumResponse.FlowConcept> targets = concepts.stream().filter(CurriculumResponse.FlowConcept::priority)
				.toList();
		if (targets.isEmpty()) {
			targets = concepts;
		}
		boolean practiceStarted = concepts.stream()
				.anyMatch(c -> c.practiceGradedCount() > 0 || "pending".equals(c.practiceStatus()));
		List<Need> needs = targets.stream().map(c -> need(c, practiceStarted)).toList();
		long remaining = needs.stream().filter(need -> need != Need.OK).count();

		String current = !preDone ? PRE_TEST
				: needs.contains(Need.STUDY) ? CONCEPT_STUDY
						: needs.contains(Need.QUIZ) ? CONCEPT_QUIZ
								: !postDone ? POST_TEST : DONE;
		int currentIndex = DONE.equals(current) ? ORDER.size() : ORDER.indexOf(current);

		CurriculumResponse.StudyProgress progress = progress(concepts);
		long practiced = targets.stream().filter(c -> c.practiceGradedCount() > 0).count();
		Map<String, String> summaries = Map.of(
				PRE_TEST, preSummary(pre, preDone, concepts, minPercent, assessmentMaterial),
				CONCEPT_STUDY, studySummary(concepts, preDone, progress),
				CONCEPT_QUIZ, quizSummary(targets, practiced, preDone, progress),
				POST_TEST, postSummary(post, postDone, preDone, comparison.overallDelta(), remaining));
		List<CurriculumResponse.FlowStage> stages = new ArrayList<>();
		for (int i = 0; i < ORDER.size(); i++) {
			String key = ORDER.get(i);
			String status = i < currentIndex ? "done" : i == currentIndex ? "current" : "upcoming";
			stages.add(new CurriculumResponse.FlowStage(key, i + 1, TITLES.get(key), status, summaries.get(key)));
		}

		return new CurriculumResponse.Flow(current, stages, source, assessmentMaterial, state(pre, preDone), concepts,
				state(post, postDone), comparison.overallDelta(), progress);
	}

	// 자료 개념은 학습 기록·확인 퀴즈로, 과목 키워드뿐인 개념은 예전처럼 일반 퀴즈 결과로 본다
	private static Need need(CurriculumResponse.FlowConcept concept, boolean practiceStarted) {
		if (concept.study() != null) {
			return switch (concept.study().status()) {
				case "not_studied", "review_needed" -> Need.STUDY;
				case "studied" -> Need.QUIZ;
				default -> Need.OK;
			};
		}
		if (concept.practiceGradedCount() > 0) {
			return Need.OK;
		}
		return practiceStarted ? Need.QUIZ : Need.STUDY;
	}

	// 학습 진행률: 분모는 학습 기록을 남길 수 있는 개념(자료 개념), 분자는 학습 완료 기록이 있는 개념
	static CurriculumResponse.StudyProgress progress(List<CurriculumResponse.FlowConcept> concepts) {
		List<ConceptStudyResponse> studies = concepts.stream().map(CurriculumResponse.FlowConcept::study)
				.filter(Objects::nonNull).toList();
		int studied = (int) studies.stream().filter(ConceptStudyResponse::studied).count();
		ConceptStudyResponse last = studies.stream().filter(s -> s.lastStudiedAt() != null)
				.max(Comparator.comparing(ConceptStudyResponse::lastStudiedAt)).orElse(null);
		return new CurriculumResponse.StudyProgress(concepts.size(), studies.size(), studied,
				studies.isEmpty() ? null : Math.round(studied * 1000.0 / studies.size()) / 10.0,
				(int) studies.stream().filter(s -> s.latestCheck() != null).count(),
				count(studies, "understood"), count(studies, "review_needed"), count(studies, "needs_check"),
				last == null ? null : last.lastStudiedAt(), last == null ? null : last.name());
	}

	private static int count(List<ConceptStudyResponse> studies, String status) {
		return (int) studies.stream().filter(s -> status.equals(s.status())).count();
	}

	private static Integer percent(Integer score, Integer max) {
		return score == null || max == null || max == 0 ? null : Math.round(score * 100f / max);
	}

	private static CurriculumResponse.AssessmentState state(AssessmentComparisonResponse.Snapshot snapshot,
			boolean submitted) {
		if (snapshot == null) {
			return new CurriculumResponse.AssessmentState("none", null, null, null, null, 0, null);
		}
		if (!submitted) {
			return new CurriculumResponse.AssessmentState("not_submitted", snapshot.quizId(), null, null, null, 0,
					null);
		}
		return new CurriculumResponse.AssessmentState("submitted", snapshot.quizId(), snapshot.score(),
				snapshot.maxScore(), snapshot.gradedCount() == 0 ? null : percent(snapshot.score(), snapshot.maxScore()),
				snapshot.pendingCount() + snapshot.reviewCount(), snapshot.submittedAt());
	}

	private static String preSummary(AssessmentComparisonResponse.Snapshot pre, boolean preDone,
			List<CurriculumResponse.FlowConcept> concepts, int minPercent, CurriculumResponse.MaterialRef material) {
		if (pre == null) {
			return material == null ? "이 과목에 올린 자료가 없어요. 자료를 올리면 그 자료로 사전 테스트를 볼 수 있어요."
					: "아직 사전 테스트를 보지 않았어요. 공부하기 전에 지금 이해 수준을 먼저 확인해요.";
		}
		if (!preDone) {
			return "사전 테스트를 만들었지만 아직 풀지 않았어요.";
		}
		long weak = concepts.stream().filter(c -> "weak".equals(c.preStatus())).count();
		String result = "사전 테스트 " + pre.score() + "/" + pre.maxScore() + "점. 기준(" + minPercent + "%)보다 낮은 개념 "
				+ weak + "개.";
		int waiting = pre.pendingCount() + pre.reviewCount();
		return waiting > 0 ? result + " 채점 대기·확인 필요 " + waiting + "문제는 점수에서 뺐어요." : result;
	}

	private static String studySummary(List<CurriculumResponse.FlowConcept> concepts, boolean preDone,
			CurriculumResponse.StudyProgress progress) {
		if (concepts.isEmpty()) {
			return "공부할 개념이 없어요. 과목 키워드를 넣거나 자료에서 핵심 개념을 뽑아 주세요.";
		}
		List<String> priority = concepts.stream().filter(CurriculumResponse.FlowConcept::priority)
				.map(CurriculumResponse.FlowConcept::name).toList();
		String head = preDone && !priority.isEmpty()
				? "먼저 공부할 개념 " + priority.size() + "개: " + String.join(", ", priority) + "."
				: "개념 " + concepts.size() + "개를 자료로 공부해요.";
		if (progress.studiableConcepts() == 0) {
			return head + " 자료에서 핵심 개념을 뽑으면 개념마다 학습 완료를 기록할 수 있어요.";
		}
		String result = "총 " + progress.studiableConcepts() + "개 개념 중 " + progress.studiedConcepts() + "개 학습 완료 ("
				+ progress.percent() + "%). " + head;
		if (progress.reviewNeededConcepts() > 0) {
			result += " 복습이 필요한 개념 " + progress.reviewNeededConcepts() + "개.";
		}
		int keywordOnly = progress.totalConcepts() - progress.studiableConcepts();
		return keywordOnly > 0 ? result + " 자료 개념이 아닌 과목 키워드 " + keywordOnly + "개는 학습 기록 없이 퀴즈로만 확인해요." : result;
	}

	private static String quizSummary(List<CurriculumResponse.FlowConcept> targets, long practiced, boolean preDone,
			CurriculumResponse.StudyProgress progress) {
		if (targets.isEmpty()) {
			return "확인 퀴즈를 낼 개념이 없어요.";
		}
		if (progress.studiableConcepts() > 0) {
			return "확인 퀴즈를 푼 개념 " + progress.checkedConcepts() + "/" + progress.studiableConcepts() + "개. 이해 확인 "
					+ progress.understoodConcepts() + "개 · 복습 필요 " + progress.reviewNeededConcepts() + "개 · 추가 확인 필요 "
					+ progress.needsCheckConcepts() + "개.";
		}
		List<String> weak = targets.stream().filter(c -> "weak".equals(c.practiceStatus()))
				.map(CurriculumResponse.FlowConcept::name).toList();
		String head = (preDone ? "사전 테스트 뒤 " : "") + "확인 퀴즈를 푼 개념 " + practiced + "/" + targets.size() + "개.";
		return weak.isEmpty() ? head : head + " 아직 기준보다 낮은 개념: " + String.join(", ", weak) + ".";
	}

	private static String postSummary(AssessmentComparisonResponse.Snapshot post, boolean postDone, boolean preDone,
			Integer overallDelta, long remaining) {
		if (!preDone) {
			return "사전 테스트를 본 뒤에 같은 개념·문항 수·난이도로 사후 평가를 봐요.";
		}
		String left = remaining > 0 ? " 아직 학습하거나 확인하지 않은 개념이 " + remaining + "개 있지만 사후 평가는 볼 수 있어요." : "";
		if (post == null) {
			return "아직 사후 평가를 보지 않았어요. 사전 테스트와 같은 기준으로 만들어 비교해요." + left;
		}
		if (!postDone) {
			return "사후 평가를 만들었지만 아직 풀지 않았어요." + left;
		}
		String result = "사후 평가 " + post.score() + "/" + post.maxScore() + "점.";
		return overallDelta == null ? result
				: result + " 사전 테스트보다 " + (overallDelta >= 0 ? "+" : "") + overallDelta + "%p.";
	}
}
