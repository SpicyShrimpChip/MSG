package com.ailms.backend.domain.analysis.entity;

import java.util.Locale;
import java.util.Optional;

// 분석 상태 값들은 API에 소문자(snake_case)로 주고받는다 (예: NOT_UNDERSTOOD ↔ not_understood)
final class ApiValues {

	private ApiValues() {
	}

	static String of(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
		if (value == null) {
			return Optional.empty();
		}
		for (E candidate : type.getEnumConstants()) {
			if (of(candidate).equals(value.strip().toLowerCase(Locale.ROOT))) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}
}
