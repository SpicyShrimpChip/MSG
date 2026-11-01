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

// 가져오기 작업에서 이미 받은 요청. 같은 요청을 다시 받으면 저장하지 않고 이 기록으로 같은 결과를 돌려준다
@Entity
@Table(name = "conversation_import_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivedImportRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long importId;

	@Column(nullable = false)
	private int requestIndex;

	@Column(nullable = false)
	private int conversationCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static ReceivedImportRequest of(Long importId, int requestIndex, int conversationCount,
			LocalDateTime now) {
		ReceivedImportRequest request = new ReceivedImportRequest();
		request.importId = importId;
		request.requestIndex = requestIndex;
		request.conversationCount = conversationCount;
		request.createdAt = now;
		return request;
	}
}
