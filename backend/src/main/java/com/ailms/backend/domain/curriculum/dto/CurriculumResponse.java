package com.ailms.backend.domain.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 과목의 맞춤 학습 순서 (frontend/src/types/curriculum.ts의 Curriculum). 순서·단계·이유(reasons)는 서버 코드 규칙이고
 * 숙련도 점수가 아니다. aiSuggestions만 AI가 퀴즈를 만들 때 제안한 개념이라 실제 결과와 따로 둔다.
 */
public record CurriculumResponse(
		Long courseId,
		String courseName,
		@Schema(description = "내 학습 목적 (추천 퀴즈 모드를 정한다)") String learningPurpose,
		@Schema(description = "추천 퀴즈 모드: exam_prep / self_development / work") String recommendedMode,
		List<Step> steps,
		@Schema(description = "이 과목에 올린 내 자료 (복습용). 본문은 읽지 않았다") List<MaterialRef> materials,
		@Schema(description = "AI가 퀴즈를 만들 때 제안한 개념 (실제 학습 결과가 아님)") List<AiSuggestion> aiSuggestions,
		@Schema(description = "학습 흐름: 사전 테스트 → 핵심 개념 학습 → 개념별 확인 퀴즈 → 사후 평가 (코드 규칙)") Flow flow) {

	/**
	 * 4단계 학습 흐름. 단계 상태·지금 단계는 서버 코드가 제출·채점 기록으로 정한다(AI를 부르지 않는다). 사용자는 어느 단계로든 갈 수 있고,
	 * currentStep은 "지금 하면 좋은 단계"다. 개념 학습은 읽었는지 기록하지 않으므로, 첫 확인 퀴즈를 풀기 전까지를 개념 학습 단계로 본다.
	 */
	public record Flow(
			@Schema(allowableValues = {"pre_test", "concept_study", "concept_quiz", "post_test", "done"})
			String currentStep,
			@Schema(description = "단계 4개 (순서대로)") List<FlowStage> stages,
			@Schema(allowableValues = {"material", "course"},
					description = "개념 출처: material(자료에서 뽑은 개념) / course(과목 키워드)") String conceptSource,
			@Schema(description = "사전·사후 평가에 쓸 자료 (사전평가가 있으면 그 자료, 없으면 과목의 최근 자료). 자료가 없으면 null")
			MaterialRef assessmentMaterial,
			@Schema(description = "사전 테스트") AssessmentState preTest,
			@Schema(description = "개념별 학습·확인 퀴즈 상태 (사전평가 개념 순서, 없으면 자료 개념·과목 키워드 순서)")
			List<FlowConcept> concepts,
			@Schema(description = "사후 평가 (사전평가와 같은 개념·유형·문항 수·난이도)") AssessmentState postTest,
			@Schema(description = "전체 향상 폭 (%p, 사후 - 사전). 둘 중 하나라도 채점 결과가 없으면 null") Integer overallDelta) {
	}

	public record FlowStage(
			@Schema(allowableValues = {"pre_test", "concept_study", "concept_quiz", "post_test"}) String key,
			int order,
			String title,
			@Schema(allowableValues = {"done", "current", "upcoming"}) String status,
			@Schema(description = "이 단계의 지금 상태 (코드가 센 사실)") String summary) {
	}

	public record AssessmentState(
			@Schema(allowableValues = {"none", "not_submitted", "submitted"},
					description = "none: 아직 만들지 않음 / not_submitted: 만들었지만 안 풂 / submitted: 풀어서 제출함") String status,
			Long quizId,
			@Schema(description = "채점이 끝난 답의 득점 (제출 전이면 null)") Integer score,
			Integer maxScore,
			@Schema(description = "득점률 % (채점 결과가 없으면 null)") Integer rate,
			int pendingCount,
			LocalDateTime submittedAt) {
	}

	public record FlowConcept(
			String conceptId,
			String name,
			@Schema(description = "이 개념을 공부할 자료 (자료에서 뽑은 개념이면 그 자료). 없으면 null") Long materialId,
			String materialTitle,
			@Schema(description = "자료 상세 '핵심 개념' 탭에 자세한 설명(studyNote)이 있는지") boolean hasStudyNote,
			@Schema(description = "사전 테스트 득점률 %. 사전 테스트를 안 봤거나 채점 결과가 없으면 null") Integer preRate,
			@Schema(allowableValues = {"weak", "ok", "not_taken"},
					description = "사전 테스트 기준 상태 (기준 정답률 미만이면 weak). 사전 테스트가 없으면 not_taken") String preStatus,
			@Schema(description = "사전 테스트 뒤 푼 확인 퀴즈에서 채점이 끝난 문제 수") int practiceGradedCount,
			int practiceScore,
			int practiceMaxScore,
			@Schema(allowableValues = {"not_started", "pending", "weak", "ok"},
					description = "확인 퀴즈 상태: not_started 아직 안 풂 / pending 채점 대기 / weak 기준 미만 / ok 기준 이상")
			String practiceStatus,
			@Schema(description = "사후 평가 득점률 %. 없으면 null") Integer postRate,
			@Schema(description = "향상 폭 (%p). 한쪽이라도 없으면 null") Integer delta,
			@Schema(description = "사후 평가 뒤 비교 상태 (improved 등, AssessmentComparison과 같음). 사후 평가 전이면 null")
			String postStatus,
			@Schema(description = "사전 테스트에서 부족했거나 아직 확인하지 않아 먼저 공부하면 좋은 개념인지") boolean priority) {
	}

	public record Step(
			int order,
			String conceptId,
			String concept,
			@Schema(allowableValues = {"priority_study", "review", "analysis_weak", "needs_check", "not_assessed",
					"ready"}) String stage,
			@Schema(description = "이 순서인 이유 (코드가 센 근거 문장)") List<String> reasons,
			Evidence evidence,
			List<Action> actions) {
	}

	public record Evidence(
			@Schema(description = "퀴즈: 다룬 문제 수") int questionCount,
			int gradedCount,
			int correctCount,
			int score,
			int maxScore,
			int pendingCount,
			int reviewCount,
			@Schema(description = "대화 분석 다수 의견 (understood 등). 분석이 없으면 null") String analysisStatus,
			@Schema(description = "대화 분석 비교 결과 (unanimous 등). 없으면 null") String analysisOutcome,
			@Schema(description = "내 피드백: already_know / want_to_study / null") String feedback,
			@Schema(description = "이미 알아요 확인 결과. 없으면 null") String confirmation) {
	}

	public record Action(
			@Schema(allowableValues = {"review_quiz", "confirm_quiz", "first_quiz", "study_material", "next_step"})
			String type,
			String label,
			@Schema(description = "퀴즈를 만들 때 쓸 모드 (퀴즈 행동만)") String quizMode) {
	}

	public record MaterialRef(Long id, String title) {
	}

	public record AiSuggestion(String kind, String concept, String aiReason) {
	}
}
