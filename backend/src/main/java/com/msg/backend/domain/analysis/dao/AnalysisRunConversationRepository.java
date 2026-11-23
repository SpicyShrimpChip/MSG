package com.msg.backend.domain.analysis.dao;

import com.msg.backend.domain.analysis.entity.AnalysisRunConversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunConversationRepository extends JpaRepository<AnalysisRunConversation, Long> {
}
