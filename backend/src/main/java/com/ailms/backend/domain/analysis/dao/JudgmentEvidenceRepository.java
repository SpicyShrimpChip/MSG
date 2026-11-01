package com.ailms.backend.domain.analysis.dao;

import com.ailms.backend.domain.analysis.entity.JudgmentEvidence;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JudgmentEvidenceRepository extends JpaRepository<JudgmentEvidence, Long> {

	List<JudgmentEvidence> findAllByJudgmentIdInOrderByIdAsc(Collection<Long> judgmentIds);
}
