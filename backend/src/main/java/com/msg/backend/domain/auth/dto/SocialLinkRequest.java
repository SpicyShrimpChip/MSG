package com.msg.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 카카오를 같은 이메일의 비밀번호 계정에 연결할 때 그 계정의 비밀번호 (검증 문구는 LoginRequest와 같다)
public record SocialLinkRequest(
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 128, message = "이메일 또는 비밀번호를 확인해 주세요.")
		String password) {
}
