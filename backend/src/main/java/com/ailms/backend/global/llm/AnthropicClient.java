package com.ailms.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Claude (Anthropic Messages API). 응답의 글(text) 블록을 이어 붙인다.
 * 환경변수: ANTHROPIC_API_KEY, ANTHROPIC_MODEL
 */
@Component
public class AnthropicClient extends HttpLlmClient {

	static final String API_VERSION = "2023-06-01";

	public AnthropicClient(LlmProperties properties, ObjectMapper objectMapper) {
		super(LlmProvider.CLAUDE, properties.anthropic(), "https://api.anthropic.com", properties.timeout(),
				objectMapper);
	}

	@Override
	protected LlmResponse send(LlmRequest request) {
		Map<String, Object> body = Map.of(
				"model", model(),
				"max_tokens", request.maxOutputTokens(),
				"system", request.system(),
				"messages", List.of(Map.of("role", "user", "content", request.user())));
		JsonNode response = post("/v1/messages",
				Map.of("x-api-key", apiKey(), "anthropic-version", API_VERSION), body);
		StringBuilder text = new StringBuilder();
		for (JsonNode block : response.path("content")) {
			if ("text".equals(block.path("type").asText())) {
				text.append(block.path("text").asText());
			}
		}
		if (text.isEmpty()) {
			throw new LlmException(LlmException.Code.BAD_RESPONSE, "CLAUDE 응답에 답변 글이 없다");
		}
		JsonNode usage = response.path("usage");
		return new LlmResponse(text.toString(), intOrNull(usage.get("input_tokens")),
				intOrNull(usage.get("output_tokens")));
	}
}
