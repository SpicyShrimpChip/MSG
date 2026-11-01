package com.ailms.backend.global.llm;

/**
 * 모델이 돌려준 글과 사용량. 토큰 수를 알려 주지 않는 제공사면 null이다.
 */
public record LlmResponse(String text, Integer inputTokens, Integer outputTokens) {
}
