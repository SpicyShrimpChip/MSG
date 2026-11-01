package com.ailms.backend.domain.conversation.entity;

public enum ConversationImportStatus {

	/** 아직 받지 못한 요청이 있다. 이 작업의 대화는 분석에 쓰지 않는다 */
	IN_PROGRESS,
	/** 모든 요청을 받았다 */
	COMPLETED
}
