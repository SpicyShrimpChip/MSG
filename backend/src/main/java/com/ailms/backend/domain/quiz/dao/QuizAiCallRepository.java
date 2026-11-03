package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizAiCall;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizAiCallRepository extends JpaRepository<QuizAiCall, Long> {

	// 24시간 동안 실제로 보낸 모델 요청 수 (재시도 포함). 비용 상한
	@Query("select coalesce(sum(c.attempts), 0) from QuizAiCall c where c.userId = :userId and c.createdAt > :after")
	long sumAttemptsSince(Long userId, LocalDateTime after);

	long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, String purpose, LocalDateTime after);
}
