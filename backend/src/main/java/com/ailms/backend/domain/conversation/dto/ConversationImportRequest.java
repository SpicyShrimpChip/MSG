package com.ailms.backend.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 대화 가져오기 요청 한 번 (frontend/src/types/conversation.ts의 ConversationImport).
 * 프론트는 고른 대화를 과목별·크기별로 나눠 여러 요청으로 보내고, 요청마다 같은 importId와 번호를 붙인다.
 */
public record ConversationImportRequest(
		@Schema(description = "가져오기 작업 id. 보내기를 한 번 누를 때 프론트가 만든 UUID")
		@NotNull(message = "가져오기 작업 정보가 없어요.")
		@Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
				message = "가져오기 작업 정보가 올바르지 않아요.")
		String importId,

		@Schema(description = "이 요청의 번호 (1부터)")
		@NotNull(message = "가져오기 작업 정보가 없어요.")
		@Min(value = 1, message = "가져오기 작업 정보가 올바르지 않아요.")
		Integer requestIndex,

		@Schema(description = "이 작업의 전체 요청 수")
		@NotNull(message = "가져오기 작업 정보가 없어요.")
		@Min(value = 1, message = "가져오기 작업 정보가 올바르지 않아요.")
		@Max(value = MAX_REQUEST_COUNT, message = "한 번에 보낼 수 있는 양을 넘었어요. 대화를 나눠서 보내 주세요.")
		Integer requestCount,

		@NotNull(message = "과목을 골라 주세요.")
		Long courseId,

		@NotNull(message = "보낼 대화를 골라 주세요.")
		@Size(min = 1, max = MAX_CONVERSATIONS, message = "대화는 한 번에 1~50개까지 보낼 수 있어요.")
		List<@NotNull(message = "보낼 대화를 골라 주세요.") @Valid ImportedConversationRequest> conversations) {

	// 프론트 MAX_CONVERSATIONS_PER_REQUEST와 같은 값 (frontend/src/utils/conversation/send.ts)
	public static final int MAX_CONVERSATIONS = 50;
	// 작업 하나의 요청 수 상한. 50개씩 1,000번이면 대화 5만 개라 정상 사용에서는 닿지 않는다
	public static final int MAX_REQUEST_COUNT = 1_000;
}
