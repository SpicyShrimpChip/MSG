package com.ailms.backend.domain.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가져온 대화 하나. 메시지는 conversation_message, 받은 조각은 conversation_part에 있다.
 * 긴 대화는 조각으로 나뉘어 여러 요청으로 올 수 있어서, 조각을 모두 받았는지(isComplete)를 따로 센다.
 */
@Entity
@Table(name = "conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

	public static final int CLIENT_ID_MAX_LENGTH = 200;
	public static final int TITLE_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	// 이 대화를 마지막으로 보낸 가져오기 작업
	@Column(nullable = false)
	private Long importId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConversationSource source;

	@Column(nullable = false, length = CLIENT_ID_MAX_LENGTH)
	private String clientId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	private LocalDateTime sourceCreatedAt;

	@Column(nullable = false)
	private int partCount;

	@Column(nullable = false)
	private int receivedPartCount;

	@Column(nullable = false)
	private int messageCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static Conversation start(Long userId, Long courseId, Long importId, ConversationSource source,
			String clientId, String title, LocalDateTime sourceCreatedAt, int partCount, LocalDateTime now) {
		Conversation conversation = new Conversation();
		conversation.userId = userId;
		conversation.courseId = courseId;
		conversation.importId = importId;
		conversation.source = source;
		conversation.clientId = clientId;
		conversation.title = title;
		conversation.sourceCreatedAt = sourceCreatedAt;
		conversation.partCount = partCount;
		conversation.createdAt = now;
		conversation.updatedAt = now;
		return conversation;
	}

	public boolean isComplete() {
		return receivedPartCount >= partCount;
	}

	// 새 조각을 받았다. 이 대화는 이번 작업이 끝나야 분석에 쓴다
	public void addPart(Long importId, int messageCount, LocalDateTime now) {
		this.importId = importId;
		this.receivedPartCount++;
		this.messageCount += messageCount;
		this.updatedAt = now;
	}

	// 이미 받은 대화를 끝나지 않은 다른 작업에서 다시 보냈다. 그 작업이 끝나면 분석에 쓸 수 있게 옮긴다
	public void moveTo(Long importId, LocalDateTime now) {
		this.importId = importId;
		this.updatedAt = now;
	}
}
