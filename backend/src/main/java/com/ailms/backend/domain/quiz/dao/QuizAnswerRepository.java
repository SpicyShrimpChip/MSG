package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

	List<QuizAnswer> findAllBySubmissionId(Long submissionId);

	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"rubricScores"})
	List<QuizAnswer> findAllBySubmissionIdIn(java.util.Collection<Long> submissionIds);
}
