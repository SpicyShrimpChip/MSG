package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizGradingRun;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizGradingRunRepository extends JpaRepository<QuizGradingRun, Long> {

	// 24시간 AI 채점 한도 (처음 채점·다시 채점 모두 센다)
	long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

	List<QuizGradingRun> findAllByUserIdAndStatus(Long userId, QuizGradingRun.Status status);
}
