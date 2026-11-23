package com.msg.backend.domain.auth.application;

// 소셜 로그인 실패. 콜백은 이 코드로 로그인 화면에 돌려보낸다
public class SocialLoginException extends RuntimeException {

	private final SocialError error;

	public SocialLoginException(SocialError error) {
		super(error.code());
		this.error = error;
	}

	public SocialError error() {
		return error;
	}
}
