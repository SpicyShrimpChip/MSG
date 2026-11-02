package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

	Optional<QuizSubmission> findFirstByQuizIdAndUserIdOrderByIdDesc(Long quizId, Long userId);

	// 퀴즈마다 가장 최근 제출 하나
	@Query("select s from QuizSubmission s where s.id in ("
			+ "select max(l.id) from QuizSubmission l where l.userId = :userId and l.quizId in :quizIds group by l.quizId)")
	List<QuizSubmission> findLatestByQuizIds(Long userId, Collection<Long> quizIds);
}
