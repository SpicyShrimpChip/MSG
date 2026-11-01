package com.ailms.backend.domain.analysis.dao;

import com.ailms.backend.domain.analysis.entity.ConceptJudgment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptJudgmentRepository extends JpaRepository<ConceptJudgment, Long> {

	List<ConceptJudgment> findAllByModelResultIdIn(Collection<Long> modelResultIds);
}
