package com.ailms.backend.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// 가져온 대화 목록 한 줄. 메시지 본문은 싣지 않는다
public record ConversationSummaryResponse(
		Long id,
		Long courseId,
		@Schema(allowableValues = {"chatgpt", "claude"}) String source,
		String title,
		@Schema(description = "외부 서비스에서 대화를 시작한 시각. 파일에 없으면 null") LocalDateTime createdAt,
		@Schema(description = "가져온 시각") LocalDateTime importedAt,
		int messageCount,
		@Schema(description = "가져오기 작업이 끝났고 조각을 모두 받아 분석에 쓸 수 있는지") boolean analysisReady) {
}
