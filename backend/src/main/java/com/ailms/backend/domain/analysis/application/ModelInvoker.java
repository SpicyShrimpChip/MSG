package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.LlmException;
import com.ailms.backend.global.llm.LlmProperties;
import com.ailms.backend.global.llm.LlmRequest;
import com.ailms.backend.global.llm.LlmResponse;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모델을 부르고, 시간 초과·한도 초과·서버 오류면 정해진 횟수만큼 다시 부른다.
 * 모델끼리는 서로의 결과를 모른 채 동시에 부른다. 로그에는 제공사·오류 종류만 남기고 대화 내용은 남기지 않는다.
 */
@Slf4j
@Component
class ModelInvoker {

	// 응답을 받았으면 response, 끝내 실패했으면 errorCode가 있다
	record Outcome(LlmResponse response, LlmException.Code errorCode, int attempts, long latencyMs) {
	}

	private final LlmProperties properties;
	private final ExecutorService executor;

	ModelInvoker(LlmProperties properties) {
		this.properties = properties;
		AtomicInteger sequence = new AtomicInteger();
		this.executor = Executors.newFixedThreadPool(6, runnable -> {
			Thread thread = new Thread(runnable, "llm-call-" + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}

	CompletableFuture<Outcome> invokeAsync(LlmClient client, LlmRequest request) {
		return CompletableFuture.supplyAsync(() -> invoke(client, request), executor);
	}

	Outcome invoke(LlmClient client, LlmRequest request) {
		long started = System.nanoTime();
		int maxAttempts = 1 + Math.max(0, properties.maxRetries());
		LlmException.Code lastCode = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				LlmResponse response = client.complete(request);
				return new Outcome(response, null, attempt, elapsedMs(started));
			} catch (LlmException e) {
				lastCode = e.getCode();
				log.warn("{} 모델 호출 실패 ({}), {}/{}번째", client.provider(), e.getCode(), attempt, maxAttempts);
				if (!e.getCode().retryable() || attempt == maxAttempts) {
					return new Outcome(null, lastCode, attempt, elapsedMs(started));
				}
				if (!sleep(properties.retryBackoff().toMillis() * attempt)) {
					return new Outcome(null, lastCode, attempt, elapsedMs(started));
				}
			} catch (RuntimeException e) {
				log.error("{} 모델 호출 중 예상하지 못한 오류 ({})", client.provider(), e.getClass().getSimpleName());
				return new Outcome(null, LlmException.Code.BAD_RESPONSE, attempt, elapsedMs(started));
			}
		}
		return new Outcome(null, lastCode, maxAttempts, elapsedMs(started));
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	private static boolean sleep(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static long elapsedMs(long started) {
		return (System.nanoTime() - started) / 1_000_000;
	}
}
