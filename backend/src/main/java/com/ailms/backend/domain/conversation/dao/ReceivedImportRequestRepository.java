package com.ailms.backend.domain.conversation.dao;

import com.ailms.backend.domain.conversation.entity.ReceivedImportRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivedImportRequestRepository extends JpaRepository<ReceivedImportRequest, Long> {

	Optional<ReceivedImportRequest> findByImportIdAndRequestIndex(Long importId, int requestIndex);

	long countByImportId(Long importId);
}
