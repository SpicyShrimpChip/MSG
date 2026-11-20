package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizGradingRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizGradingRunRepository extends JpaRepository<QuizGradingRun, Long> {

	List<QuizGradingRun> findAllByUserIdAndStatus(Long userId, QuizGradingRun.Status status);
}
