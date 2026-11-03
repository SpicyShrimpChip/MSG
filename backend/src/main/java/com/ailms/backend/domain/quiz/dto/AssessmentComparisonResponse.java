package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사전평가 vs 사후평가 비교 (frontend/src/types/assessment.ts의 AssessmentComparison).
 * 기준: 최신 사전평가(제출한 것 우선) vs 그 사전평가에 이어 만든 최신 사후평가(제출한 것 우선). 각 평가는 가장 최근 제출로 센다.
 * 점수는 채점이 끝난 답만 더한 서버 계산 값이고 숙련도 점수가 아니다.
 */
public record AssessmentComparisonResponse(
		@Schema(description = "비교 기준 설명") String basis,
		Material material,
		@Schema(description = "사전평가. 없으면 null") Snapshot pre,
		@Schema(description = "사후평가. 없으면 null") Snapshot post,
		@Schema(description = "개념별 비교 (사전평가 개념 순서)") List<ConceptDelta> concepts,
		@Schema(description = "전체 향상 폭 (사후 득점률 - 사전 득점률, %p). 둘 중 하나라도 채점 결과가 없으면 null") Integer overallDelta,
		@Schema(description = "비교할 때 알아야 할 한계 (코드가 확인한 사실)") Comparability comparability) {

	public record Comparability(
			@Schema(description = "사후평가가 사전평가와 같은 개념을 다루는지") boolean sameConcepts,
			boolean sameQuestionCount,
			boolean sameQuestionTypes,
			boolean sameDifficulty,
			@Schema(description = "문항 수·채점 대기 등 비교 한계 문장") List<String> notes) {
	}

	public record Material(Long id, String title) {
	}

	public record Snapshot(
			Long quizId,
			String title,
			@Schema(description = "가장 최근 제출. 아직 안 풀었으면 null") Long submissionId,
			LocalDateTime submittedAt,
			int questionCount,
			int gradedCount,
			int score,
			int maxScore,
			int pendingCount,
			int reviewCount) {
	}

	public record ConceptDelta(
			String conceptId,
			String name,
			Integer preScore,
			Integer preMaxScore,
			Integer postScore,
			Integer postMaxScore,
			@Schema(description = "향상 폭 (%p). 한쪽이라도 채점 결과가 없으면 null") Integer delta,
			@Schema(allowableValues = {"improved", "declined", "unchanged", "still_weak", "weak", "ok", "not_taken"},
					description = "improved·declined·unchanged: 사후가 기준(60%) 이상일 때 변화 / still_weak: 사후도 기준 미만 / "
							+ "weak·ok: 사후평가 전, 사전 기준 / not_taken: 채점 결과 없음") String status) {
	}
}
