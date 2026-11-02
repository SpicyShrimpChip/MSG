package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.Quiz;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 퀴즈는 없는 것과 같게 다룬다
public interface QuizRepository extends JpaRepository<Quiz, Long> {

	List<Quiz> findAllByUserIdOrderByIdDesc(Long userId);

	Optional<Quiz> findByIdAndUserId(Long id, Long userId);
}
