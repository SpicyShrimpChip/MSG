package com.ailms.backend.domain.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대화의 메시지 한 개. 대화 안의 순서는 (partIndex, seq)다.
 * 20,000자가 넘는 메시지는 프론트가 나눠 보내고, 이어지는 글이 다음 메시지에 있으면 continued가 true다.
 */
@Entity
@Table(name = "conversation_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMessage {

	// 프론트 MAX_MESSAGE_LENGTH와 같은 값 (frontend/src/utils/conversation/send.ts)
	public static final int CONTENT_MAX_LENGTH = 20_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long conversationId;

	@Column(nullable = false)
	private int partIndex;

	@Column(nullable = false)
	private int seq;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MessageRole role;

	@Column(nullable = false, length = CONTENT_MAX_LENGTH)
	private String content;

	@Column(nullable = false)
	private boolean continued;

	public static ConversationMessage of(Long conversationId, int partIndex, int seq, MessageRole role,
			String content, boolean continued) {
		ConversationMessage message = new ConversationMessage();
		message.conversationId = conversationId;
		message.partIndex = partIndex;
		message.seq = seq;
		message.role = role;
		message.content = content;
		message.continued = continued;
		return message;
	}
}
