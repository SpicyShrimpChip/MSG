package com.ailms.backend.support;

import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmClients;
import com.ailms.backend.global.llm.LlmProvider;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

// 통합 테스트에서 실제 제공사 대신 쓰는 모델 목록 (IntegrationTest가 @Primary로 끼운다)
public class FakeLlmClients extends LlmClients {

	private final Map<LlmProvider, FakeLlmClient> fakes;

	public FakeLlmClients() {
		this(create());
	}

	private FakeLlmClients(Map<LlmProvider, FakeLlmClient> fakes) {
		super(fakes.values().stream().map(fake -> (LlmClient) fake).toList());
		this.fakes = fakes;
	}

	private static Map<LlmProvider, FakeLlmClient> create() {
		Map<LlmProvider, FakeLlmClient> fakes = new EnumMap<>(LlmProvider.class);
		Arrays.stream(LlmProvider.values()).forEach(provider -> fakes.put(provider, new FakeLlmClient(provider)));
		return fakes;
	}

	public FakeLlmClient get(LlmProvider provider) {
		return fakes.get(provider);
	}

	public void resetAll() {
		fakes.values().forEach(FakeLlmClient::reset);
	}
}
