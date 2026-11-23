package com.msg.backend.domain.notice.api;

import com.msg.backend.domain.notice.application.NoticeService;
import com.msg.backend.domain.notice.dto.NoticeDetailResponse;
import com.msg.backend.domain.notice.dto.NoticeRequest;
import com.msg.backend.global.common.ApiResponse;
import com.msg.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 은 SecurityConfig에서 관리자만 열린다. 작성자는 요청 본문이 아니라 토큰의 사용자로 정한다
@Tag(name = "공지사항 관리 (관리자)")
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

	private final NoticeService noticeService;

	@Operation(summary = "공지 작성", description = "제목 100자·본문 5000자까지. 앞뒤 공백은 지운다.")
	@PostMapping
	public ResponseEntity<ApiResponse<NoticeDetailResponse>> write(
			@AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody NoticeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(noticeService.write(authUser.userId(), request)));
	}

	@Operation(summary = "공지 수정", description = "수정하면 updatedAt이 채워진다. 없으면 404.")
	@PutMapping("/{id}")
	public ApiResponse<NoticeDetailResponse> edit(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
		return ApiResponse.success(noticeService.edit(id, request));
	}

	@Operation(summary = "공지 삭제", description = "완전히 지운다. 없으면 404.")
	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		noticeService.delete(id);
		return ApiResponse.success(null);
	}
}
