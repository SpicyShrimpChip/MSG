package com.ailms.backend.domain.conversation.dao;

import com.ailms.backend.domain.conversation.entity.ConversationMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

	// 여러 대화의 메시지를 대화 안의 순서대로
	List<ConversationMessage> findAllByConversationIdInOrderByConversationIdAscPartIndexAscSeqAsc(
			Collection<Long> conversationIds);
}
