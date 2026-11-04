package com.ailms.backend.domain.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
		@Schema(description = "AI가 퀴즈를 만들 때 제안한 개념 (실제 학습 결과가 아님)") List<AiSuggestion> aiSuggestions) {

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
