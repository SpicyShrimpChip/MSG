package com.ailms.backend.global.llm;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모델을 부르고, 시간 초과·한도 초과·서버 오류면 정해진 횟수만큼 다시 부른다.
 * 모델끼리는 서로의 결과를 모른 채 동시에 부른다. 로그에는 제공사·오류 종류만 남기고 대화 내용은 남기지 않는다.
 *
 * <p>학습 분석과 퀴즈(만들기·채점)는 스레드 묶음(Lane)을 따로 써서 한쪽이 몰려도 다른 쪽이 기다리지 않는다.
 * 마감 시각(deadline)을 주면 남은 시간이 없을 때 다시 시도하지 않고, {@link #await}는 마감까지만 기다린다.
 * 마감 뒤 늦게 끝난 호출의 결과는 버려진다(부르는 쪽이 받지 않는다).
 */
@Slf4j
@Component
public class ModelInvoker {

	// 다시 시도하려면 마감까지 이만큼은 남아 있어야 한다 (응답을 받을 가능성이 거의 없는 재시도로 비용을 쓰지 않게)
	static final Duration MIN_REMAINING_FOR_RETRY = Duration.ofSeconds(10);

	public enum Lane {
		ANALYSIS, QUIZ
	}

	// 응답을 받았으면 response, 끝내 실패했으면 errorCode가 있다. 마감까지 끝나지 않았으면 timedOut
	public record Outcome(LlmResponse response, LlmException.Code errorCode, int attempts, long latencyMs,
			boolean timedOut) {

		public Outcome(LlmResponse response, LlmException.Code errorCode, int attempts, long latencyMs) {
			this(response, errorCode, attempts, latencyMs, false);
		}
	}

	private final LlmProperties properties;
	private final Clock clock;
	private final ExecutorService analysisExecutor;
	private final ExecutorService quizExecutor;

	ModelInvoker(LlmProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.analysisExecutor = pool("llm-analysis-");
		this.quizExecutor = pool("llm-quiz-");
	}

	private static ExecutorService pool(String prefix) {
		AtomicInteger sequence = new AtomicInteger();
		return Executors.newFixedThreadPool(6, runnable -> {
			Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}

	// 학습 분석 (마감 없음, 예전과 같다)
	public CompletableFuture<Outcome> invokeAsync(LlmClient client, LlmRequest request) {
		return invokeAsync(client, request, Lane.ANALYSIS, null);
	}

	public CompletableFuture<Outcome> invokeAsync(LlmClient client, LlmRequest request, Lane lane, Instant deadline) {
		return CompletableFuture.supplyAsync(() -> invoke(client, request, deadline),
				lane == Lane.QUIZ ? quizExecutor : analysisExecutor);
	}

	/**
	 * 마감까지만 기다린다. 마감이 지나면 timedOut 결과를 돌려주고, 그 호출이 나중에 끝나도 결과는 쓰지 않는다.
	 * 이미 보낸 요청은 HTTP 시간 초과(app.llm.timeout)까지 계속될 수 있으니 attempts는 최소 1로 본다(비용 기록용).
	 */
	public Outcome await(CompletableFuture<Outcome> call, Instant deadline) {
		long started = System.nanoTime();
		try {
			long remaining = Duration.between(clock.instant(), deadline).toMillis();
			return remaining <= 0 ? timedOut(started) : call.get(remaining, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			return timedOut(started);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return timedOut(started);
		} catch (ExecutionException e) {
			return new Outcome(null, LlmException.Code.BAD_RESPONSE, 1, elapsedMs(started));
		}
	}

	public Outcome invoke(LlmClient client, LlmRequest request) {
		return invoke(client, request, null);
	}

	public Outcome invoke(LlmClient client, LlmRequest request, Instant deadline) {
		long started = System.nanoTime();
		if (deadline != null && !clock.instant().isBefore(deadline)) {
			return new Outcome(null, LlmException.Code.TIMEOUT, 0, 0, true);
		}
		int maxAttempts = 1 + Math.max(0, properties.maxRetries());
		LlmException.Code lastCode = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				LlmResponse response = client.complete(request);
				return new Outcome(response, null, attempt, elapsedMs(started));
			} catch (LlmException e) {
				lastCode = e.getCode();
				log.warn("{} 모델 호출 실패 ({}), {}/{}번째", client.provider(), e.getCode(), attempt, maxAttempts);
				long backoff = properties.retryBackoff().toMillis() * attempt;
				if (!e.getCode().retryable() || attempt == maxAttempts || !timeLeft(deadline, backoff)) {
					return new Outcome(null, lastCode, attempt, elapsedMs(started));
				}
				if (!sleep(backoff)) {
					return new Outcome(null, lastCode, attempt, elapsedMs(started));
				}
			} catch (RuntimeException e) {
				log.error("{} 모델 호출 중 예상하지 못한 오류 ({})", client.provider(), e.getClass().getSimpleName());
				return new Outcome(null, LlmException.Code.BAD_RESPONSE, attempt, elapsedMs(started));
			}
		}
		return new Outcome(null, lastCode, maxAttempts, elapsedMs(started));
	}

	private boolean timeLeft(Instant deadline, long backoffMillis) {
		return deadline == null || clock.instant().plusMillis(backoffMillis).plus(MIN_REMAINING_FOR_RETRY)
				.isBefore(deadline);
	}

	private static Outcome timedOut(long started) {
		return new Outcome(null, LlmException.Code.TIMEOUT, 1, elapsedMs(started), true);
	}

	@PreDestroy
	void shutdown() {
		analysisExecutor.shutdownNow();
		quizExecutor.shutdownNow();
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
