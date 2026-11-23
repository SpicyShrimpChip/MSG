package com.msg.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴 (frontend/src/api/auth.api.ts deleteAccount). 본인 확인을 다시 받는다.
 * 비밀번호가 있는 계정은 password, 카카오로만 가입해 비밀번호가 없는 계정(UserResponse.hasPassword=false)은 confirmText에 "탈퇴".
 */
public record WithdrawRequest(
		@Schema(description = "지금 비밀번호. 비밀번호가 있는 계정만") @Size(max = 128, message = "비밀번호가 맞지 않아요. 다시 확인해 주세요.") String password,
		@Schema(description = "확인 문구 \"탈퇴\". 비밀번호가 없는 계정만") @Size(max = 10, message = "확인 문구로 \"탈퇴\"를 입력해 주세요.") String confirmText) {
}
