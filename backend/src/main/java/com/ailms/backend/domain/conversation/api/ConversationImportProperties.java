package com.ailms.backend.domain.conversation.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 대화 가져오기 설정 (app.conversation-import).
 *
 * @param maxRequestSize 요청 한 번의 최대 크기. 프론트는 이보다 작은 크기(MAX_REQUEST_BYTES)로 나눠 보낸다
 */
@ConfigurationProperties(prefix = "app.conversation-import")
public record ConversationImportProperties(DataSize maxRequestSize) {
}
