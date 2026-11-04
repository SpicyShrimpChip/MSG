package com.ailms.backend.domain.analysis.dto;

import com.ailms.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 분석 한 번의 결과. ai로 시작하는 필드는 AI가 쓴 글이고, 나머지(상태 비교·순서·개수)는 코드가 정한 값이다.
 * 모델 비교는 정확도나 숙련도 점수가 아니다 (화면에 "정확도 67%"처럼 보여 주지 않는다).
 */
public record AnalysisRunResponse(
		Long id,
		Long courseId,
		@Schema(allowableValues = {"running", "completed", "partial", "failed"},
				description = "completed: 부른 모델 모두 성공 / partial: 일부 성공 / failed: 성공한 모델 없음") String status,
		@Schema(description = "모델에 준 지시문 버전") String promptVersion,
		LocalDateTime createdAt,
		LocalDateTime completedAt,
		Input input,
		List<ModelResult> models,
		@Schema(description = "개념별 결과. studyOrder 순서로 정렬돼 있다") List<ConceptResult> concepts) {

	public record Input(
			int conversationCount,
			@Schema(description = "모델로 보낸 메시지 수 (키워드 앞뒤 메시지만 보낸다)") int messageCount,
			@Schema(description = "길어서 뒤를 잘라 보낸 메시지 수") int truncatedMessageCount) {
	}

	public record ModelResult(
			@Schema(allowableValues = {"gpt", "claude", "gemini"}) String provider,
			String model,
			@Schema(allowableValues = {"succeeded", "failed", "invalid_output", "not_configured"}) String status,
			@Schema(description = "실패·미설정 이유 (사용자용 문구). 성공이면 null") String message,
			int judgedConceptCount,
			@Schema(description = "검증에서 버린 근거 수 (보내지 않은 메시지, 말한 사람이 맞지 않는 근거)") int droppedEvidenceCount) {
	}

	public record ConceptResult(
			String concept,
			Comparison comparison,
			@Schema(description = "모델별 판단 (성공한 모델 중 이 개념을 판단한 것만)") List<Judgment> judgments,
			@Schema(description = "사용자가 남긴 학습 피드백. 없으면 null") FeedbackResponse feedback,
			@Schema(description = "추천 학습 순서 (1부터, 코드 규칙). 더 공부할래요 → 이해 부족 → 부분 이해 → 의견 불일치 → 근거 부족 → 이해") int studyOrder,
			@Schema(description = "이미 알아요를 남겨 확인 퀴즈를 권하는지") boolean recommendConfirmQuiz,
			@Schema(description = "이 개념의 퀴즈 결과 (코드 집계, AI 판단과 따로). 퀴즈 기록이 없으면 null")
			ConceptQuizStatsResponse quiz) {
	}

	public record Comparison(
			@Schema(allowableValues = {"unanimous", "majority", "disagreement", "single_model", "no_result"}) String outcome,
			@Schema(allowableValues = {"understood", "partial", "not_understood", "insufficient_evidence"},
					description = "일치한 상태. 의견 불일치·결과 없음이면 null") String status,
			@Schema(description = "이 개념을 판단한 모델 수") int judgedCount,
			@Schema(description = "그중 같은 판단을 한 모델 수") int agreeCount,
			@Schema(description = "이번 분석에서 부른 모델 수 (미설정 제외)") int calledCount) {
	}

	public record Judgment(
			@Schema(allowableValues = {"gpt", "claude", "gemini"}) String provider,
			String model,
			@Schema(description = "비교에 쓴 상태") String status,
			@Schema(description = "모델이 보고한 상태 (원본)") String reportedStatus,
			@Schema(description = "status가 reportedStatus와 다른 이유 (코드). 같으면 null") String adjustedReason,
			String aiReason,
			String aiNeedsCheck,
			String aiRecommendedActivity,
			List<Evidence> evidence) {
	}

	public record Evidence(
			Long messageId,
			Long conversationId,
			String conversationTitle,
			@Schema(allowableValues = {"user", "assistant"}) String role,
			@Schema(allowableValues = {"user_explanation", "user_solution", "user_question", "ai_explanation"}) String kind,
			String aiNote,
			@Schema(description = "메시지 앞부분 (120자)") String excerpt) {
	}
}
