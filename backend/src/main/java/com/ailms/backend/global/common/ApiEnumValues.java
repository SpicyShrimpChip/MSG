package com.ailms.backend.global.common;

import java.util.Locale;
import java.util.Optional;

/**
 * Enum 값을 API에서 소문자(snake_case)로 주고받는다 (예: SELF_DEVELOPMENT ↔ self_development).
 * 요청 값은 문자열로 받아 이 도우미로 바꾸고, 모르는 값이면 빈 Optional을 돌려 서비스가 사용자용 문구로 400을 낸다.
 */
public final class ApiEnumValues {

	private ApiEnumValues() {
	}

	public static String of(Enum<?> value) {
		return value == null ? null : value.name().toLowerCase(Locale.ROOT);
	}

	public static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
		if (value == null) {
			return Optional.empty();
		}
		String normalized = value.strip().toLowerCase(Locale.ROOT);
		for (E candidate : type.getEnumConstants()) {
			if (of(candidate).equals(normalized)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}
}
