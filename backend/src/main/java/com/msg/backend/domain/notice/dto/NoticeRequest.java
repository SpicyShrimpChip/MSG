package com.msg.backend.domain.notice.dto;

import com.msg.backend.domain.notice.entity.Notice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 관리자가 공지를 쓰거나 고칠 때 보내는 내용. 앞뒤 공백은 서비스에서 지운다
public record NoticeRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = Notice.TITLE_MAX_LENGTH, message = "제목은 100자까지 입력할 수 있어요.")
		String title,

		@NotBlank(message = "본문을 입력해 주세요.")
		@Size(max = Notice.CONTENT_MAX_LENGTH, message = "본문은 5000자까지 입력할 수 있어요.")
		String content) {
}
