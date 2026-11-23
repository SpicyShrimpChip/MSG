package com.msg.backend.global.llm;

import com.msg.backend.global.common.NulCharacters;

/**
 * 모델이 돌려준 글과 사용량. 토큰 수를 알려 주지 않는 제공사면 null이다.
 * 답변 글은 원본(raw_output)과 그로부터 읽은 개념·퀴즈로 DB에 저장되므로 NUL 문자는 여기서 지운다(NulCharacters 참고).
 */
public record LlmResponse(String text, Integer inputTokens, Integer outputTokens) {

	public LlmResponse {
		text = NulCharacters.strip(text);
	}
}
