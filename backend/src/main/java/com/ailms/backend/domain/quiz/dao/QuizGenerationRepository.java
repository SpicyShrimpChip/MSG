package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizGeneration;
import com.ailms.backend.domain.quiz.entity.QuizGenerationStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizGenerationRepository extends JpaRepository<QuizGeneration, Long> {

	Optional<QuizGeneration> findByUserIdAndRequestKey(Long userId, String requestKey);

	// 24시간 동안 만든 요청 수 (호출 한도)
	long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

	// 아직 끝나지 않은 요청 (runningTimeout보다 오래된 것은 끝나지 않은 것으로 보지 않는다)
	boolean existsByUserIdAndStatusAndCreatedAtAfter(Long userId, QuizGenerationStatus status, LocalDateTime after);

	java.util.List<QuizGeneration> findAllByUserIdAndStatus(Long userId, QuizGenerationStatus status);
}
