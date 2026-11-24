package com.msg.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 가입 마무리 화면에 보여 줄 카카오 정보 (가입 마무리 쿠키에서 읽는다, DB에는 아직 없다)
public record PendingSocialSignupResponse(
		@Schema(description = "제공사", allowableValues = {"kakao"}) String provider,
		@Schema(description = "카카오가 확인한 이메일. 가입하면 이 이메일로 계정이 생긴다") String email,
		@Schema(description = "카카오 닉네임(선택 동의). 없으면 null. 이름 칸의 처음 값으로 쓴다") String nickname) {
}
