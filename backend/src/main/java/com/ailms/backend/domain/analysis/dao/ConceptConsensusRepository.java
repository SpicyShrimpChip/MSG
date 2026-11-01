package com.ailms.backend.domain.analysis.dao;

import com.ailms.backend.domain.analysis.entity.ConceptConsensus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptConsensusRepository extends JpaRepository<ConceptConsensus, Long> {

	List<ConceptConsensus> findAllByRunIdOrderByIdAsc(Long runId);
}
