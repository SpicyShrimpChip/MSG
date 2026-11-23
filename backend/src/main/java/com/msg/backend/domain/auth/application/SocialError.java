package com.msg.backend.domain.auth.application;

import java.util.Locale;

/**
 * 소셜 로그인이 로그인 화면으로 돌아갈 때 붙이는 오류 코드(/login?socialError=코드). 문구는 화면이 코드로 고른다.
 */
public enum SocialError {
	// state 쿠키가 없거나 다름 (시간이 지났거나 다른 브라우저에서 돌아옴)
	STATE,
	// 사용자가 카카오 동의 화면에서 취소
	CANCELLED,
	// 이메일 제공에 동의하지 않음
	EMAIL_REQUIRED,
	// 카카오가 확인하지 않은 이메일
	EMAIL_UNVERIFIED,
	// 탈퇴 뒤 30일 재가입 제한
	REJOIN_RESTRICTED,
	// 같은 이메일 계정에 다른 카카오 계정이 이미 연결됨
	LINK_CONFLICT,
	// 카카오 서버 오류·시간 초과 등
	FAILED,
	// 서버에 카카오 키가 없음
	UNAVAILABLE;

	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}
}
