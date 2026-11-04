package com.ailms.backend.global.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * 모델 답변에서 JSON 객체를 읽는다. 코드 블록 표시(```json)나 앞뒤 글이 붙어 와도 첫 { 부터 마지막 } 까지를 읽는다.
 * 읽지 못하면 빈 Optional (내용은 로그에 남기지 않는다).
 */
public final class LlmJson {

	private LlmJson() {
	}

	public static Optional<JsonNode> readObject(ObjectMapper objectMapper, String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return Optional.empty();
		}
		try {
			JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
			return root != null && root.isObject() ? Optional.of(root) : Optional.empty();
		} catch (JsonProcessingException e) {
			return Optional.empty();
		}
	}

	// 공백을 걷어 낸 글. 글이 아니거나 비었으면 null
	public static String text(JsonNode node) {
		if (node == null || !node.isTextual()) {
			return null;
		}
		String text = node.asText().strip();
		return text.isEmpty() ? null : text;
	}
}
