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
 * 가져오기 작업. 사용자가 "보내기"를 한 번 누르면 프론트가 UUID를 하나 만들고,
 * 나눠 보내는 요청마다 그 id와 요청 번호·전체 요청 수를 붙인다. 모든 요청을 받아야 COMPLETED가 된다.
 */
@Entity
@Table(name = "conversation_import")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationImport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 36)
	private String clientImportId;

	@Column(nullable = false)
	private int requestCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConversationImportStatus status;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime completedAt;

	public static ConversationImport start(Long userId, String clientImportId, int requestCount, LocalDateTime now) {
		ConversationImport conversationImport = new ConversationImport();
		conversationImport.userId = userId;
		conversationImport.clientImportId = clientImportId;
		conversationImport.requestCount = requestCount;
		conversationImport.status = ConversationImportStatus.IN_PROGRESS;
		conversationImport.createdAt = now;
		return conversationImport;
	}

	public boolean isCompleted() {
		return status == ConversationImportStatus.COMPLETED;
	}

	// 받은 요청 수가 전체 요청 수에 닿으면 완료한다
	public void markReceived(long receivedRequestCount, LocalDateTime now) {
		if (!isCompleted() && receivedRequestCount >= requestCount) {
			status = ConversationImportStatus.COMPLETED;
			completedAt = now;
		}
	}
}
