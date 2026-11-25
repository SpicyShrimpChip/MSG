package com.msg.archfixture.llm;

import com.msg.backend.global.llm.LlmClient;

/**
 * 구조 규칙 자체 검사용 허용 예시. LlmClient를 타입으로 들고 상태만 읽는 것은 막지 않아야 한다
 * (AnalysisService·QuizModelFallback 등이 provider()·isConfigured()를 쓴다).
 */
public class AllowedLlmClientUse {

	String describe(LlmClient client) {
		return client.provider() + ":" + client.model() + ":" + client.isConfigured();
	}
}
