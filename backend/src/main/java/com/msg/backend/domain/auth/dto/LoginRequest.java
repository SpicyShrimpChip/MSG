package com.msg.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Size(max = 254, message = "이메일 또는 비밀번호를 확인해 주세요.")
		String email,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 128, message = "이메일 또는 비밀번호를 확인해 주세요.")
		String password) {
}
