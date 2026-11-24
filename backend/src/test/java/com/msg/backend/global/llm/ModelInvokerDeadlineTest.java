package com.msg.backend.global.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.support.FakeLlmClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 시간 예산: 마감까지만 기다리고, 남은 시간이 없으면 다시 시도하지 않는다
class ModelInvokerDeadlineTest {

	private final LlmProperties properties = new LlmProperties(Duration.ofSeconds(60), 3, Duration.ofMillis(1), null,
			null, null);
	private final ModelInvoker invoker = new ModelInvoker(properties, Clock.systemUTC(), AiCallQuota.UNLIMITED);

	@AfterEach
	void tearDown() {
		invoker.shutdown();
	}

	@Test
	void 하루_상한에_닿으면_모델을_부르지_않고_DAILY_LIMIT을_돌려준다() {
		AtomicInteger calls = new AtomicInteger();
		FakeLlmClient client = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			calls.incrementAndGet();
			return new LlmResponse("{}", 1, 1);
		});
		ModelInvoker limited = new ModelInvoker(properties, Clock.systemUTC(), quotaAllowing(0));
		try {
			ModelInvoker.Outcome outcome = limited.invoke(null, client, new LlmRequest("s", "u", 10), null);

			assertThat(outcome.errorCode()).isEqualTo(LlmException.Code.DAILY_LIMIT);
			assertThat(outcome.attempts()).isZero();
			assertThat(calls).hasValue(0);
		} finally {
			limited.shutdown();
		}
	}

	@Test
	void 다시_부를_때도_상한에서_받아_가고_닿으면_멈춘다() {
		AtomicInteger calls = new AtomicInteger();
		FakeLlmClient failing = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			calls.incrementAndGet();
			throw new LlmException(LlmException.Code.SERVER_ERROR, "실패");
		});
		ModelInvoker limited = new ModelInvoker(properties, Clock.systemUTC(), quotaAllowing(2));
		try {
			ModelInvoker.Outcome outcome = limited.invoke(null, failing, new LlmRequest("s", "u", 10), null);

			assertThat(calls).hasValue(2);
			assertThat(outcome.attempts()).isEqualTo(2);
			assertThat(outcome.errorCode()).isEqualTo(LlmException.Code.DAILY_LIMIT);
		} finally {
			limited.shutdown();
		}
	}

	@Test
	void 사용자_몫을_다_쓰면_부르지_않고_서비스_전체_상한도_줄이지_않는다() {
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger serviceAcquired = new AtomicInteger();
		FakeLlmClient failing = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			calls.incrementAndGet();
			throw new LlmException(LlmException.Code.SERVER_ERROR, "실패");
		});
		AtomicInteger userLeft = new AtomicInteger(1);
		AiCallQuota quota = new AiCallQuota() {
			@Override
			public boolean tryAcquire() {
				serviceAcquired.incrementAndGet();
				return true;
			}

			@Override
			public boolean hasRemaining() {
				return true;
			}

			@Override
			public boolean tryAcquireForUser(Long userId) {
				return userId == 7L && userLeft.getAndDecrement() > 0;
			}
		};
		ModelInvoker limited = new ModelInvoker(properties, Clock.systemUTC(), quota);
		try {
			// 첫 호출은 몫 안이라 부르고, 다시 부를 때 몫이 없어 멈춘다
			ModelInvoker.Outcome outcome = limited.invoke(7L, failing, new LlmRequest("s", "u", 10), null);

			assertThat(outcome.errorCode()).isEqualTo(LlmException.Code.USER_DAILY_LIMIT);
			assertThat(outcome.attempts()).isEqualTo(1);
			assertThat(calls).hasValue(1);
			assertThat(serviceAcquired).hasValue(1);
		} finally {
			limited.shutdown();
		}
	}

	// n번까지만 받아 가게 해 주는 상한
	private static AiCallQuota quotaAllowing(int n) {
		AtomicInteger left = new AtomicInteger(n);
		return new AiCallQuota() {
			@Override
			public boolean tryAcquire() {
				return left.getAndDecrement() > 0;
			}

			@Override
			public boolean hasRemaining() {
				return left.get() > 0;
			}
		};
	}

	@Test
	void 마감이_지났어도_이미_끝난_호출의_결과는_쓴다() {
		// 여러 모델을 차례로 기다릴 때: 앞 모델을 기다리다 마감이 지났지만 이 모델은 그 전에 답했다
		java.util.concurrent.CompletableFuture<ModelInvoker.Outcome> done = java.util.concurrent.CompletableFuture
				.completedFuture(new ModelInvoker.Outcome(new LlmResponse("{}", 1, 1), null, 1, 10));

		ModelInvoker.Outcome outcome = invoker.await(done, Instant.now().minusSeconds(1));

		assertThat(outcome.timedOut()).isFalse();
		assertThat(outcome.response()).isNotNull();
	}

	@Test
	void 마감이_지나면_기다리지_않고_시간_초과로_돌려준다() {
		FakeLlmClient slow = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			sleep(2_000);
			return new LlmResponse("{}", 1, 1);
		});
		Instant deadline = Instant.now().plusMillis(200);

		long started = System.nanoTime();
		ModelInvoker.Outcome outcome = invoker.await(
				invoker.invokeAsync(null, slow, new LlmRequest("s", "u", 10), ModelInvoker.Lane.QUIZ, deadline), deadline);

		assertThat(outcome.timedOut()).isTrue();
		assertThat(outcome.response()).isNull();
		assertThat(outcome.attempts()).isEqualTo(1);
		assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1_500);
	}

	@Test
	void 남은_시간이_다시_시도할_만큼_없으면_재시도하지_않는다() {
		AtomicInteger calls = new AtomicInteger();
		FakeLlmClient failing = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			calls.incrementAndGet();
			throw new LlmException(LlmException.Code.SERVER_ERROR, "실패");
		});

		ModelInvoker.Outcome near = invoker.invoke(null, failing, new LlmRequest("s", "u", 10),
				Instant.now().plusSeconds(5));
		assertThat(near.attempts()).isEqualTo(1);
		assertThat(calls).hasValue(1);

		ModelInvoker.Outcome far = invoker.invoke(null, failing, new LlmRequest("s", "u", 10), Instant.now().plusSeconds(120));
		assertThat(far.attempts()).isEqualTo(4);
	}

	@Test
	void 마감이_이미_지났으면_부르지_않는다() {
		AtomicInteger calls = new AtomicInteger();
		FakeLlmClient client = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			calls.incrementAndGet();
			return new LlmResponse("{}", 1, 1);
		});

		ModelInvoker.Outcome outcome = invoker.invoke(null, client, new LlmRequest("s", "u", 10), Instant.now().minusSeconds(1));

		assertThat(outcome.timedOut()).isTrue();
		assertThat(calls).hasValue(0);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
