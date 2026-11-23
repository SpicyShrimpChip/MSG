package com.msg.backend.domain.notice.dto;

import com.msg.backend.domain.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

// frontend/src/types/notice.ts의 Notice와 같은 모양
public record NoticeResponse(
		Long id,
		String title,
		@Schema(type = "string", format = "date", example = "2026-10-24") LocalDate createdAt) {

	public static NoticeResponse from(Notice notice) {
		return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getCreatedAt());
	}
}
