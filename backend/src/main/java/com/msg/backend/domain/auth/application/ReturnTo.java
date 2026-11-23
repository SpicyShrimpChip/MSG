package com.msg.backend.domain.auth.application;

/**
 * 소셜 로그인 뒤 돌아갈 화면 경로. 우리 사이트 안의 경로(/로 시작)만 받고, 아니면 첫 화면(/)으로 바꾼다.
 * 다른 사이트로 보내는 열린 리다이렉트를 막는다. 프론트 safeReturnTo(utils/auth/socialLogin.ts)와 같은 규칙이다.
 */
public final class ReturnTo {

	public static final String HOME = "/";
	public static final int MAX_LENGTH = 200;

	private ReturnTo() {
	}

	public static String sanitize(String raw) {
		if (raw == null || raw.isEmpty() || raw.length() > MAX_LENGTH || raw.charAt(0) != '/') {
			return HOME;
		}
		// //evil.com, /\evil.com 은 브라우저가 다른 사이트 주소로 읽는다
		if (raw.length() > 1 && (raw.charAt(1) == '/' || raw.charAt(1) == '\\')) {
			return HOME;
		}
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\\' || c < 0x20 || c == 0x7f) {
				return HOME;
			}
		}
		return raw;
	}
}
