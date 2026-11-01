package com.ailms.backend.support;

import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmProvider;
import com.ailms.backend.global.llm.LlmRequest;
import com.ailms.backend.global.llm.LlmResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 테스트 전용 가짜 모델. 정해 둔 답을 돌려주고 받은 요청을 기록한다. 서비스 코드(src/main)에는 없다.
 * 기본은 미설정(configured=false)이라 테스트가 직접 켠다.
 */
public class FakeLlmClient implements LlmClient {

	private final LlmProvider provider;
	private final List<LlmRequest> requests = new CopyOnWriteArrayList<>();
	private volatile boolean configured;
	private volatile Function<LlmRequest, LlmResponse> behavior;

	public FakeLlmClient(LlmProvider provider) {
		this.provider = provider;
		reset();
	}

	public void reset() {
		configured = false;
		requests.clear();
		behavior = request -> {
			throw new AssertionError(provider + " 가짜 모델의 답을 정하지 않았다");
		};
	}

	// 켜고 매번 같은 글을 돌려준다
	public FakeLlmClient answer(String text) {
		return respond(request -> new LlmResponse(text, 100, 50));
	}

	public FakeLlmClient respond(Function<LlmRequest, LlmResponse> behavior) {
		this.configured = true;
		this.behavior = behavior;
		return this;
	}

	public List<LlmRequest> requests() {
		return requests;
	}

	@Override
	public LlmProvider provider() {
		return provider;
	}

	@Override
	public String model() {
		return configured ? "fake-" + provider.apiValue() : null;
	}

	@Override
	public boolean isConfigured() {
		return configured;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		requests.add(request);
		return behavior.apply(request);
	}
}
