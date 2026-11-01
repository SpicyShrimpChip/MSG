package com.ailms.backend.global.llm;

/**
 * 제공사 하나의 모델을 부르는 방법. 구현은 제공사마다 하나씩 있고(OpenAiClient, AnthropicClient, GeminiClient),
 * API 키·모델 이름은 환경변수로만 받는다. 키나 모델이 없으면 isConfigured()가 false이고 호출하지 않는다.
 */
public interface LlmClient {

	LlmProvider provider();

	/** 설정된 모델 이름. 미설정이면 null */
	String model();

	boolean isConfigured();

	/**
	 * 한 번 부른다 (다시 시도는 부르는 쪽이 정한다).
	 *
	 * @throws LlmException 시간 초과, 한도 초과, 인증 실패, 응답 형식 오류 등
	 */
	LlmResponse complete(LlmRequest request);
}
