package com.ailms.backend.domain.conversation.api;

import com.ailms.backend.domain.conversation.application.ConversationService;
import com.ailms.backend.domain.conversation.dto.ConversationImportRequest;
import com.ailms.backend.domain.conversation.dto.ConversationImportResponse;
import com.ailms.backend.domain.conversation.dto.ConversationSummaryResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 대화만 다룬다. 남의 과목·대화 id로 요청하면 404
@Tag(name = "대화 가져오기")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

	private final ConversationService conversationService;

	@Operation(summary = "대화 가져오기", description = "프론트가 개인정보를 가린 대화를 과목 하나씩 받는다. "
			+ "요청은 2MB·대화 50개·메시지당 20,000자·제목 500자까지. AI 대화 분석에 동의하지 않았으면 403. "
			+ "개인정보가 남은 대화가 있으면 요청 전체를 저장하지 않고 422와 data.rejected를 돌려준다. "
			+ "같은 importId·requestIndex를 다시 보내면 저장하지 않고 같은 결과를 돌려준다. "
			+ "importCompleted가 true가 된 뒤에야 대화를 분석에 쓴다.")
	@PostMapping
	public ResponseEntity<ApiResponse<ConversationImportResponse>> importConversations(
			@AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody ConversationImportRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(conversationService.importConversations(authUser.userId(), request)));
	}

	@Operation(summary = "가져온 대화 목록", description = "최근에 가져온 대화가 위로 온다. 메시지 본문은 싣지 않는다.")
	@GetMapping
	public ApiResponse<List<ConversationSummaryResponse>> getConversations(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.success(conversationService.getConversations(authUser.userId()));
	}

	@Operation(summary = "가져온 대화 삭제", description = "대화와 메시지를 함께 지운다. 되돌릴 수 없다.")
	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
		conversationService.delete(authUser.userId(), id);
		return ApiResponse.success(null);
	}
}
