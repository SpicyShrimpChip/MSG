package com.msg.backend.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 과목의 최근 분석 (runs/latest). 화면에 보여 줄 결과(run)는 모델이 하나 이상 판단한 분석만이고,
 * 그보다 나중에 시작한 분석이 아직 진행 중이거나 결과 없이 끝났으면 lastAttempt로 따로 알린다.
 * 모델이 모두 실패한 분석이 이전 결과를 덮지 않게 하려는 것이다(#209, #214).
 */
public record LatestAnalysisResponse(
		@Schema(description = "모델이 하나 이상 판단한 가장 최근 분석 (completed·partial). 없으면 null") AnalysisRunResponse run,
		@Schema(description = "run보다 나중에 시작한 분석이 진행 중이거나 결과 없이 끝났으면 그 시도. 없으면 null")
		LastAttempt lastAttempt) {

	public LatestAnalysisResponse withRun(AnalysisRunResponse next) {
		return new LatestAnalysisResponse(next, lastAttempt);
	}

	public record LastAttempt(
			Long runId,
			@Schema(allowableValues = {"running", "failed"},
					description = "running: 모델을 기다리는 중 / failed: 판단한 모델 없이 끝남(모두 실패, 끝나지 못함)") String status,
			LocalDateTime startedAt,
			@Schema(description = "끝난 시각. 진행 중이거나 끝나지 못했으면 null") LocalDateTime finishedAt,
			@Schema(description = "결과가 없는 이유 (사용자용 문구). 진행 중이면 null") String message) {
	}
}
