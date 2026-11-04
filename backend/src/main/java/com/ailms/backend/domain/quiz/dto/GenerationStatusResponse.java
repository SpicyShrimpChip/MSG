package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// 퀴즈 만들기 요청 상태 (frontend/src/types/quiz.ts의 QuizGenerationStatus). 새로 고친 화면이 같은 요청의 결과를 찾는 데 쓴다
public record GenerationStatusResponse(
		@Schema(allowableValues = {"running", "succeeded", "failed", "timed_out"}) String status,
		@Schema(description = "만든 퀴즈 (succeeded일 때만)") Long quizId,
		@Schema(description = "실패·시간 초과 이유 (사용자용 문구)") String message,
		@Schema(description = "이 시각까지 끝나지 않으면 시간 초과") LocalDateTime deadlineAt) {
}
