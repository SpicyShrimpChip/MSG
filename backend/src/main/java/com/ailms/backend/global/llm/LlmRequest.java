package com.ailms.backend.global.llm;

/**
 * 모델 한 번 호출. 모든 제공사에 같은 지시문(system)과 입력(user)을 보낸다.
 *
 * @param maxOutputTokens 응답 길이 상한 (비용 상한)
 */
public record LlmRequest(String system, String user, int maxOutputTokens) {
}
