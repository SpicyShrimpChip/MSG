package com.ailms.backend.domain.study.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 개념 하나의 학습 상태 (frontend/src/types/study.ts의 ConceptStudy). 학습 기록(학습 완료)과 확인 퀴즈 결과는 따로 담고,
 * status만 둘을 합친 코드 규칙 결과다(ConceptStudyRules). 숙련도 점수가 아니다.
 */
public record ConceptStudyResponse(
		Long materialId,
		Long courseId,
		@Schema(description = "개념 키 (자료 핵심 개념의 conceptId)") String conceptId,
		String name,
		@Schema(allowableValues = {"not_studied", "studied", "understood", "review_needed", "needs_check"},
				description = "학습 전 / 학습 완료(확인 퀴즈 전) / 이해 확인 / 복습 필요 / 추가 확인 필요") String status,
		@Schema(description = "학습 완료 기록이 있는지 (공부했다는 기록일 뿐 이해했다는 뜻은 아니다)") boolean studied,
		LocalDateTime firstCompletedAt,
		LocalDateTime lastStudiedAt,
		@Schema(description = "학습 완료 횟수 (복습 뒤 다시 완료하면 늘어난다). 기록이 없으면 0") int studyCount,
		@Schema(description = "가장 최근에 제출한 확인 퀴즈 결과. 없으면 null") CheckAttempt latestCheck,
		@Schema(description = "제출한 확인 퀴즈 이력 (최신 순, 5개까지). 지난 결과도 지우지 않는다") List<CheckAttempt> checkHistory,
		@Schema(description = "만들었지만 아직 제출하지 않은 가장 최근 확인 퀴즈 id. 없으면 null") Long openCheckQuizId,
		@Schema(description = "상태를 설명하는 문장 (코드가 센 사실)") String reason) {

	public record CheckAttempt(
			Long quizId,
			LocalDateTime submittedAt,
			@Schema(description = "채점이 끝난 문제 수") int gradedCount,
			int score,
			int maxScore,
			@Schema(description = "득점률 % (채점이 끝난 답만). 없으면 null") Integer rate,
			@Schema(description = "채점 대기·확인 필요 답 수 (틀린 답으로 세지 않음)") int waitingCount,
			@Schema(allowableValues = {"understood", "review_needed", "needs_check"}) String result) {
	}
}
