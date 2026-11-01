package com.ailms.backend.global.llm;

import java.util.Locale;

/**
 * 학습 분석에 쓰는 AI 제공사. 같은 대화를 제공사마다 독립적으로 분석하게 하고 결과를 코드로 비교한다
 * (루트 CLAUDE.md "GPT·Gemini·Claude 3개 모델이 독립 판단 후 통합"). API에는 소문자(gpt, claude, gemini)로 내보낸다.
 */
public enum LlmProvider {

	GPT,
	CLAUDE,
	GEMINI;

	public String apiValue() {
		return name().toLowerCase(Locale.ROOT);
	}
}
