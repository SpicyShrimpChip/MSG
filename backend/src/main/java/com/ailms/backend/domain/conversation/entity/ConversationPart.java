package com.ailms.backend.domain.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 받은 조각. 조각 없이 온 대화는 1번 조각 하나로 기록한다. 같은 조각을 두 번 저장하지 않는 데 쓴다
@Entity
@Table(name = "conversation_part")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationPart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long conversationId;

	@Column(nullable = false)
	private int partIndex;

	@Column(nullable = false)
	private int messageCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static ConversationPart of(Long conversationId, int partIndex, int messageCount, LocalDateTime now) {
		ConversationPart part = new ConversationPart();
		part.conversationId = conversationId;
		part.partIndex = partIndex;
		part.messageCount = messageCount;
		part.createdAt = now;
		return part;
	}
}
