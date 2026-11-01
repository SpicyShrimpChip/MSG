package com.ailms.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * GPT (OpenAI Chat Completions API). JSON 객체로만 답하게 response_format을 지정한다.
 * 환경변수: OPENAI_API_KEY, OPENAI_MODEL
 */
@Component
public class OpenAiClient extends HttpLlmClient {

	public OpenAiClient(LlmProperties properties, ObjectMapper objectMapper) {
		super(LlmProvider.GPT, properties.openai(), "https://api.openai.com", properties.timeout(), objectMapper);
	}

	@Override
	protected LlmResponse send(LlmRequest request) {
		Map<String, Object> body = Map.of(
				"model", model(),
				"messages", List.of(
						Map.of("role", "system", "content", request.system()),
						Map.of("role", "user", "content", request.user())),
				"response_format", Map.of("type", "json_object"),
				"max_completion_tokens", request.maxOutputTokens());
		JsonNode response = post("/v1/chat/completions", Map.of("Authorization", "Bearer " + apiKey()), body);
		String text = requireText(response.path("choices").path(0).path("message").get("content"), "답변 글");
		JsonNode usage = response.path("usage");
		return new LlmResponse(text, intOrNull(usage.get("prompt_tokens")), intOrNull(usage.get("completion_tokens")));
	}
}
