package com.ailms.backend.domain.conversation.dao;

import com.ailms.backend.domain.conversation.entity.ConversationPart;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationPartRepository extends JpaRepository<ConversationPart, Long> {

	List<ConversationPart> findAllByConversationIdIn(Collection<Long> conversationIds);
}
