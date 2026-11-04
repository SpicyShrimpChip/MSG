package com.ailms.backend.global.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailms.backend.support.FakeLlmClient;
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
	private final ModelInvoker invoker = new ModelInvoker(properties, Clock.systemUTC());

	@AfterEach
	void tearDown() {
		invoker.shutdown();
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
				invoker.invokeAsync(slow, new LlmRequest("s", "u", 10), ModelInvoker.Lane.QUIZ, deadline), deadline);

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

		ModelInvoker.Outcome near = invoker.invoke(failing, new LlmRequest("s", "u", 10),
				Instant.now().plusSeconds(5));
		assertThat(near.attempts()).isEqualTo(1);
		assertThat(calls).hasValue(1);

		ModelInvoker.Outcome far = invoker.invoke(failing, new LlmRequest("s", "u", 10), Instant.now().plusSeconds(120));
		assertThat(far.attempts()).isEqualTo(4);
	}

	@Test
	void 마감이_이미_지났으면_부르지_않는다() {
		AtomicInteger calls = new AtomicInteger();
		FakeLlmClient client = new FakeLlmClient(LlmProvider.GPT).respond(request -> {
			calls.incrementAndGet();
			return new LlmResponse("{}", 1, 1);
		});

		ModelInvoker.Outcome outcome = invoker.invoke(client, new LlmRequest("s", "u", 10), Instant.now().minusSeconds(1));

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
