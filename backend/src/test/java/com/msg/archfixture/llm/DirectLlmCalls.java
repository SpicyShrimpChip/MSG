package com.msg.archfixture.llm;

import com.msg.backend.global.llm.LlmClient;
import com.msg.backend.global.llm.LlmRequest;
import com.msg.backend.global.llm.LlmResponse;
import com.msg.backend.global.llm.OpenAiClient;
import java.util.function.Function;

/**
 * 구조 규칙 자체 검사용 위반 예시(ArchitectureRulesSelfTest). ModelInvoker를 거치지 않고 모델을 부른다.
 * 서비스 패키지(com.msg.backend) 밖이라 스프링이 읽지 않고, 본 규칙 검사(ArchitectureTest)에도 들어가지 않는다.
 */
public class DirectLlmCalls {

	// 인터페이스로 직접 부름
	LlmResponse viaInterface(LlmClient client) {
		return client.complete(new LlmRequest("system", "user", 10));
	}

	// 구현 클래스로 직접 부름
	LlmResponse viaImplementation(OpenAiClient client) {
		return client.complete(new LlmRequest("system", "user", 10));
	}

	// 메서드 참조로 넘김
	Function<LlmRequest, LlmResponse> viaMethodReference(LlmClient client) {
		return client::complete;
	}
}
