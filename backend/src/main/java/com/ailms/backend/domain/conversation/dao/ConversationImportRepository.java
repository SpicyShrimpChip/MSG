package com.ailms.backend.domain.conversation.dao;

import com.ailms.backend.domain.conversation.entity.ConversationImport;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationImportRepository extends JpaRepository<ConversationImport, Long> {

	// 같은 작업의 요청이 동시에 와도 완료 판단이 어긋나지 않게 작업 행을 잠근다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ConversationImport> findByUserIdAndClientImportId(Long userId, String clientImportId);

	// 받은 요청 기록(conversation_import_request)은 외래 키(ON DELETE CASCADE)로 DB가 함께 지운다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ConversationImport i where i.userId = :userId")
	int deleteAllByUserId(Long userId);
}
