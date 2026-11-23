package com.msg.backend.domain.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 비밀번호 규칙. 프론트 frontend/src/utils/password.ts의 4조건과 같다 (8자 이상, 영문, 숫자, 특수문자).
 * 브라우저 검사는 우회할 수 있으므로 서버에서 따로 검사한다.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;
	// BCrypt는 72바이트까지만 쓰므로 그보다 긴 비밀번호는 받지 않는다 (뒷부분이 무시되는 것을 막기 위해)
	public static final int MAX_BYTES = 72;

	private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
	private static final Pattern DIGIT = Pattern.compile("\\d");
	// 키보드의 ASCII 기호만 특수문자로 인정한다 (프론트와 같은 범위)
	private static final Pattern SPECIAL = Pattern.compile("[!-/:-@\\[-`{-~]");

	private PasswordPolicy() {
	}

	// 지키지 않은 첫 번째 규칙의 사용자용 문구. 모두 지키면 빈 값
	public static Optional<String> findViolation(String password) {
		if (password == null || password.length() < MIN_LENGTH) {
			return Optional.of("비밀번호는 8자 이상이어야 해요.");
		}
		if (!LETTER.matcher(password).find()) {
			return Optional.of("비밀번호에 영문을 포함해 주세요.");
		}
		if (!DIGIT.matcher(password).find()) {
			return Optional.of("비밀번호에 숫자를 포함해 주세요.");
		}
		if (!SPECIAL.matcher(password).find()) {
			return Optional.of("비밀번호에 특수문자(!, @, # 등)를 포함해 주세요.");
		}
		if (exceedsMaxBytes(password)) {
			return Optional.of("비밀번호가 너무 길어요. 조금 더 짧게 정해 주세요.");
		}
		return Optional.empty();
	}

	public static boolean exceedsMaxBytes(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}
}
