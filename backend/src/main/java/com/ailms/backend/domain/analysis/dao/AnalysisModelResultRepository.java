package com.ailms.backend.domain.analysis.dao;

import com.ailms.backend.domain.analysis.entity.AnalysisModelResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisModelResultRepository extends JpaRepository<AnalysisModelResult, Long> {

	List<AnalysisModelResult> findAllByRunId(Long runId);
}
