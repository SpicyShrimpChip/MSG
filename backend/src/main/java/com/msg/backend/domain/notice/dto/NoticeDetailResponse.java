package com.msg.backend.domain.notice.dto;

import com.msg.backend.domain.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 공지 상세. 작성한 관리자 정보는 내보내지 않는다. content는 글자로만 보여 준다 (HTML로 해석하지 않는다)
public record NoticeDetailResponse(
		Long id,
		String title,
		String content,
		@Schema(type = "string", format = "date", example = "2026-10-24") LocalDate createdAt,
		@Schema(description = "수정한 적이 없으면 null") LocalDateTime updatedAt) {

	public static NoticeDetailResponse from(Notice notice) {
		String content = notice.getContent() == null ? "" : notice.getContent();
		return new NoticeDetailResponse(
				notice.getId(), notice.getTitle(), content, notice.getCreatedAt(), notice.getUpdatedAt());
	}
}
