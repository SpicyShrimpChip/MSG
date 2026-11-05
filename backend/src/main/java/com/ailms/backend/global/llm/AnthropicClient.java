package com.ailms.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Claude (Anthropic Messages API). 응답의 글(text) 블록을 이어 붙인다.
 * 환경변수: ANTHROPIC_API_KEY, ANTHROPIC_MODEL
 *
 * <p>프롬프트 캐싱: 지시문(system)은 기능마다 고정된 글이라 끝에 cache_control을 붙인다. 같은 지시문으로
 * 5분 안에 다시 부르면 그 부분은 캐시에서 읽어 입력 요금이 크게 할인된다(처음 쓸 때는 조금 비싸다).
 * 매번 바뀌는 입력(user)은 지시문 뒤에 두어 캐시를 깨지 않는다. 지시문이 모델별 최소 길이보다 짧으면
 * 오류 없이 캐시만 되지 않는다. GPT·Gemini는 같은 앞부분을 자동으로 캐시해 코드가 필요 없다.
 */
@Slf4j
@Component
public class AnthropicClient extends HttpLlmClient {

	static final String API_VERSION = "2023-06-01";

	public AnthropicClient(LlmProperties properties, ObjectMapper objectMapper) {
		super(LlmProvider.CLAUDE, properties.anthropic(), "https://api.anthropic.com", properties.timeout(),
				objectMapper);
	}

	@Override
	protected LlmResponse send(LlmRequest request) {
		Map<String, Object> system = Map.of(
				"type", "text",
				"text", request.system(),
				"cache_control", Map.of("type", "ephemeral"));
		Map<String, Object> body = Map.of(
				"model", model(),
				"max_tokens", request.maxOutputTokens(),
				"system", List.of(system),
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
		Integer cacheWrite = intOrNull(usage.get("cache_creation_input_tokens"));
		Integer cacheRead = intOrNull(usage.get("cache_read_input_tokens"));
		if (cacheWrite != null || cacheRead != null) {
			// 토큰 수만 남긴다 (프롬프트·대화 본문은 로그에 넣지 않는다)
			log.debug("CLAUDE 프롬프트 캐시 쓰기 {} 읽기 {}", cacheWrite, cacheRead);
		}
		return new LlmResponse(text.toString(), totalInputTokens(usage), intOrNull(usage.get("output_tokens")));
	}

	// Claude의 input_tokens는 캐시 밖 부분만 센다. GPT·Gemini처럼 입력 전체로 맞추려고 캐시 쓰기·읽기를 더한다
	private static Integer totalInputTokens(JsonNode usage) {
		Integer uncached = intOrNull(usage.get("input_tokens"));
		if (uncached == null) {
			return null;
		}
		Integer write = intOrNull(usage.get("cache_creation_input_tokens"));
		Integer read = intOrNull(usage.get("cache_read_input_tokens"));
		return uncached + (write == null ? 0 : write) + (read == null ? 0 : read);
	}
}
