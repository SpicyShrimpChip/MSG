package com.ailms.backend.domain.analysis.dao;

import com.ailms.backend.domain.analysis.entity.ConceptFeedback;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 모든 조회에 소유자(userId) 조건을 함께 건다
public interface ConceptFeedbackRepository extends JpaRepository<ConceptFeedback, Long> {

	List<ConceptFeedback> findAllByUserIdAndCourseIdOrderByIdAsc(Long userId, Long courseId);

	Optional<ConceptFeedback> findByUserIdAndCourseIdAndConceptKey(Long userId, Long courseId, String conceptKey);

	Optional<ConceptFeedback> findByIdAndUserId(Long id, Long userId);
}
