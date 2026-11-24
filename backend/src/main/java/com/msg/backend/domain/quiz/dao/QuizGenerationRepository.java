package com.msg.backend.domain.quiz.dao;

import com.msg.backend.domain.quiz.entity.QuizGeneration;
import com.msg.backend.domain.quiz.entity.QuizGenerationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizGenerationRepository extends JpaRepository<QuizGeneration, Long> {

	Optional<QuizGeneration> findByUserIdAndRequestKey(Long userId, String requestKey);

	java.util.List<QuizGeneration> findAllByUserIdAndStatus(Long userId, QuizGenerationStatus status);
}
