package com.msg.backend.domain.material.dao;

import com.msg.backend.domain.material.entity.MaterialSummary;
import com.msg.backend.domain.quiz.entity.QuizMode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialSummaryRepository extends JpaRepository<MaterialSummary, Long> {

	Optional<MaterialSummary> findByMaterialIdAndUserIdAndPurpose(Long materialId, Long userId, QuizMode purpose);
}
