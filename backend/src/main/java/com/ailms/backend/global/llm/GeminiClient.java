package com.ailms.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Gemini (Google Gemini API generateContent). JSON으로만 답하게 responseMimeType을 지정한다.
 * 키는 주소가 아니라 헤더(x-goog-api-key)로 보낸다 (주소는 서버·프록시 로그에 남을 수 있다).
 * 환경변수: GEMINI_API_KEY, GEMINI_MODEL
 */
@Component
public class GeminiClient extends HttpLlmClient {

	public GeminiClient(LlmProperties properties, ObjectMapper objectMapper) {
		super(LlmProvider.GEMINI, properties.gemini(), "https://generativelanguage.googleapis.com",
				properties.timeout(), objectMapper);
	}

	@Override
	protected LlmResponse send(LlmRequest request) {
		Map<String, Object> body = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", request.system()))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.user())))),
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"maxOutputTokens", request.maxOutputTokens()));
		String uri = "/v1beta/models/" + UriUtils.encodePathSegment(model(), "UTF-8") + ":generateContent";
		JsonNode response = post(uri, Map.of("x-goog-api-key", apiKey()), body);
		StringBuilder text = new StringBuilder();
		for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
			text.append(part.path("text").asText());
		}
		if (text.isEmpty()) {
			throw new LlmException(LlmException.Code.BAD_RESPONSE, "GEMINI 응답에 답변 글이 없다");
		}
		JsonNode usage = response.path("usageMetadata");
		return new LlmResponse(text.toString(), intOrNull(usage.get("promptTokenCount")),
				intOrNull(usage.get("candidatesTokenCount")));
	}
}
