package com.ailms.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

// 회원 탈퇴 (frontend/src/api/auth.api.ts deleteAccount). 본인 확인을 위해 지금 비밀번호를 다시 받는다
public record WithdrawRequest(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
