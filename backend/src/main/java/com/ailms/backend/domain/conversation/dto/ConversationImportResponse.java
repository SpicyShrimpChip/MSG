package com.ailms.backend.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 대화 가져오기 요청 한 번의 결과 (frontend/src/types/conversation.ts의 ConversationImportResult)
public record ConversationImportResponse(
		@Schema(description = "이 요청에서 저장한 대화(조각) 수. 이미 저장돼 있던 것도 센다") int importedCount,
		@Schema(description = "이 작업의 요청을 모두 받았는지. true가 된 뒤에야 대화를 분석에 쓴다") boolean importCompleted) {
}
