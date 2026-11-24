package com.msg.backend.domain.quiz.application;

import com.msg.backend.global.llm.LlmClient;
import com.msg.backend.global.llm.LlmRequest;
import com.msg.backend.global.llm.ModelInvoker;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 퀴즈 기능(퀴즈 만들기·자료 핵심 개념 뽑기·개념 설명)에서 모델을 설정 순서대로 하나씩 부른다.
 *
 * <p>마감 전에만 부르고 기다린다. 응답을 받지 못하거나 검사(parse)를 통과하지 못하면 다음 모델로 넘어가고, 마감이 지나면 멈춘다.
 * 검사를 통과한 첫 결과를 돌려주며, 저장은 부르는 쪽이 한다. 호출은 모두 quiz_ai_call에 남긴다(QuizAiUsage).
 * 같은 모델의 일시 오류 재시도와 서비스 전체 하루 호출 상한은 ModelInvoker가 맡는다.
 */
@Component
public class QuizModelFallback {

	public static final String TIMED_OUT = "TIMED_OUT";
	public static final String MODEL_FAILED = "MODEL_FAILED";
	public static final String INVALID_OUTPUT = "INVALID_OUTPUT";

	private final ModelInvoker modelInvoker;
	private final QuizAiUsage usage;
	private final Clock clock;

	public QuizModelFallback(ModelInvoker modelInvoker, QuizAiUsage usage, Clock clock) {
		this.modelInvoker = modelInvoker;
		this.usage = usage;
		this.clock = clock;
	}

	/**
	 * 호출 기록에 남길 용도와 요청. generationId는 퀴즈 만들기에서만 쓴다. stopOnTimeout이 true면 한 모델이 시간 초과일 때 바로 멈추고(퀴즈 만들기),
	 * false면 다음 모델로 넘어가되 마감이 지났으면 반복 맨 앞의 마감 확인에서 멈춘다(개념 뽑기·설명).
	 */
	public record Call(Long userId, String purpose, Long generationId, LlmRequest request, Instant deadline,
			boolean stopOnTimeout) {
	}

	/** 검사를 통과한 결과와 그 결과를 낸 모델 */
	public record Success<T>(T value, LlmClient client) {
	}

	/**
	 * 이번 요청의 모델 호출 상태. 부르는 쪽이 만들어 넘기므로, 중간에 예상하지 못한 예외가 나도 그때까지의 상태로 실패 기록을 남길 수 있다.
	 */
	public static final class Tracker {
		private int attempts;
		private String errorCode;
		private LlmClient last;
		private Integer inputTokens;
		private Integer outputTokens;
		private boolean anyResponse;

		public Tracker(String initialErrorCode) {
			this.errorCode = initialErrorCode;
		}

		public int attempts() {
			return attempts;
		}

		public String errorCode() {
			return errorCode;
		}

		public LlmClient last() {
			return last;
		}

		public Integer inputTokens() {
			return inputTokens;
		}

		public Integer outputTokens() {
			return outputTokens;
		}

		/** 응답을 하나라도 받았는지 (형식이 맞지 않았어도) */
		public boolean anyResponse() {
			return anyResponse;
		}

		/** 저장할 때 마감이 지났거나 다른 요청이 기록을 끝낸 경우 */
		public void timeOut() {
			this.errorCode = TIMED_OUT;
		}
	}

	/**
	 * 설정 순서대로 부른다. parse는 모델 답을 검사해 통과하면 값을, 아니면 빈 값을 돌려준다(빈 값은 INVALID_OUTPUT으로 기록).
	 * 모두 실패하면 빈 값이고, 이유는 tracker.errorCode()에 있다.
	 */
	public <T> Optional<Success<T>> run(List<LlmClient> clients, Call call, Function<String, Optional<T>> parse,
			Tracker tracker) {
		for (LlmClient client : clients) {
			if (!clock.instant().isBefore(call.deadline())) {
				tracker.errorCode = TIMED_OUT;
				break;
			}
			tracker.last = client;
			ModelInvoker.Outcome outcome = modelInvoker.await(
					modelInvoker.invokeAsync(call.userId(), client, call.request(), ModelInvoker.Lane.QUIZ,
							call.deadline()),
					call.deadline());
			tracker.attempts += Math.max(outcome.attempts(), outcome.timedOut() ? 1 : 0);
			if (outcome.timedOut()) {
				record(call, client, outcome, null);
				tracker.errorCode = TIMED_OUT;
				if (call.stopOnTimeout()) {
					break;
				}
				continue;
			}
			if (outcome.response() == null) {
				record(call, client, outcome, null);
				tracker.errorCode = MODEL_FAILED;
				continue;
			}
			tracker.anyResponse = true;
			tracker.inputTokens = outcome.response().inputTokens();
			tracker.outputTokens = outcome.response().outputTokens();
			Optional<T> parsed = parse.apply(outcome.response().text());
			if (parsed.isEmpty()) {
				record(call, client, outcome, INVALID_OUTPUT);
				tracker.errorCode = INVALID_OUTPUT;
				continue;
			}
			record(call, client, outcome, null);
			return Optional.of(new Success<>(parsed.get(), client));
		}
		return Optional.empty();
	}

	private void record(Call call, LlmClient client, ModelInvoker.Outcome outcome, String resultOverride) {
		usage.record(call.userId(), call.purpose(), call.generationId(), null, client, outcome, resultOverride);
	}
}
