package com.ailms.backend.domain.study.dao;

import com.ailms.backend.domain.study.entity.ConceptStudyProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptStudyProgressRepository extends JpaRepository<ConceptStudyProgress, Long> {

	Optional<ConceptStudyProgress> findByUserIdAndMaterialIdAndConceptKey(Long userId, Long materialId,
			String conceptKey);

	List<ConceptStudyProgress> findAllByUserIdAndMaterialId(Long userId, Long materialId);

	List<ConceptStudyProgress> findAllByUserIdAndCourseId(Long userId, Long courseId);
}
