package com.ailms.backend.domain.conversation.dao;

import com.ailms.backend.domain.conversation.entity.Conversation;
import com.ailms.backend.domain.conversation.entity.ConversationSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 대화는 없는 것과 같게 다룬다
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	// 최신 대화가 위로: 가져온 시각 내림차순, 같으면 id 큰 순
	List<Conversation> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	Optional<Conversation> findByIdAndUserId(Long id, Long userId);

	List<Conversation> findAllByUserIdAndSourceAndClientIdIn(Long userId, ConversationSource source,
			Collection<String> clientIds);

	/**
	 * AI 분석에 쓸 수 있는 대화: 마지막으로 보낸 가져오기 작업이 끝났고 조각을 모두 받은 대화.
	 * 분석 기능을 만들 때 이 조건으로만 대화를 읽는다.
	 */
	@Query("select c from Conversation c, ConversationImport i where i.id = c.importId and c.userId = :userId"
			+ " and i.status = com.ailms.backend.domain.conversation.entity.ConversationImportStatus.COMPLETED"
			+ " and c.receivedPartCount >= c.partCount order by c.id")
	List<Conversation> findAnalyzable(Long userId);
}
