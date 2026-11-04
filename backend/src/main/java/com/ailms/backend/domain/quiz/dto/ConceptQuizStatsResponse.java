package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 과목의 개념 하나에 대한 퀴즈 결과 (퀴즈마다 가장 최근 제출만 센다). 서버 코드가 센 값이고 숙련도 점수가 아니다.
 * 학습 대화 분석(AI 판단)·사용자 피드백과 따로 두고 나란히 보여 준다.
 */
public record ConceptQuizStatsResponse(
		@Schema(description = "학습 분석의 개념 키와 같은 값") String conceptId,
		String name,
		@Schema(description = "이 개념을 다룬 문제 수") int questionCount,
		@Schema(description = "그중 채점이 끝난 문제 수") int gradedCount,
		@Schema(description = "채점이 끝나고 만점을 받은 문제 수 (대기·확인 필요는 틀린 것으로 세지 않는다)") int correctCount,
		int score,
		int maxScore,
		int pendingCount,
		int reviewCount,
		@Schema(description = "득점률이 기준보다 낮은 보완 개념인지") boolean weak,
		@Schema(description = "내 피드백: already_know / want_to_study / null") String feedback,
		@Schema(description = "이미 알아요를 남긴 뒤 푼 문제로 확인한 결과: confirmed / not_confirmed / needs_check / not_taken. "
				+ "피드백이 already_know가 아니면 null") String confirmation,
		@Schema(description = "이 개념을 다룬 가장 최근 제출 시각. 없으면 null") java.time.LocalDateTime lastSubmittedAt) {
}
