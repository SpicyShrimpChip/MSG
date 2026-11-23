package com.msg.backend.domain.notice.api;

import com.msg.backend.domain.notice.application.NoticeService;
import com.msg.backend.domain.notice.dto.NoticeDetailResponse;
import com.msg.backend.domain.notice.dto.NoticeResponse;
import com.msg.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공지사항")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

	private final NoticeService noticeService;

	@Operation(summary = "공지 목록", description = "최신 공지가 위로 온다 (작성일 내림차순, 같으면 id 큰 순).")
	@GetMapping
	public ApiResponse<List<NoticeResponse>> getNotices() {
		return ApiResponse.success(noticeService.getNotices());
	}

	@Operation(summary = "공지 상세", description = "본문까지 돌려준다. 본문은 글자로만 보여 주고 HTML로 해석하지 않는다. 없으면 404.")
	@GetMapping("/{id}")
	public ApiResponse<NoticeDetailResponse> getNotice(@PathVariable Long id) {
		return ApiResponse.success(noticeService.getNotice(id));
	}
}
