package com.msg.backend.domain.analysis.dao;

import com.msg.backend.domain.analysis.entity.ConceptMastery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptMasteryRepository extends JpaRepository<ConceptMastery, Long> {

	List<ConceptMastery> findAllByUserIdAndCourseIdOrderByIdAsc(Long userId, Long courseId);
}
