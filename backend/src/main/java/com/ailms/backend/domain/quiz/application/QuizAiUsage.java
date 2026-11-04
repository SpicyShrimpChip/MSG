package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.quiz.dao.QuizAiCallRepository;
import com.ailms.backend.domain.quiz.entity.QuizAiCall;
import com.ailms.backend.global.llm.LlmClient;
import com.ailms.backend.global.llm.ModelInvoker;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 퀴즈 기능의 모델 호출 기록과 실제 모델 요청 수 상한. 기록은 부르는 쪽 트랜잭션과 따로(새 트랜잭션) 남겨,
 * 뒤에서 저장이 실패해도 비용이 든 호출 기록은 남는다.
 */
@Component
public class QuizAiUsage {

	private final QuizAiCallRepository callRepository;
	private final QuizProperties properties;
	private final TransactionTemplate newTransaction;
	private final Clock clock;

	public QuizAiUsage(QuizAiCallRepository callRepository, QuizProperties properties,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.callRepository = callRepository;
		this.properties = properties;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	// 24시간 실제 모델 요청 수가 상한에 닿았는지 (사용자 행을 잠근 트랜잭션 안에서 부른다)
	public boolean modelCallBudgetExhausted(Long userId) {
		return callRepository.sumAttemptsSince(userId, LocalDateTime.now(clock).minusHours(24))
				>= properties.dailyModelCallLimit();
	}

	public void record(Long userId, String purpose, Long generationId, Long gradingRunId, LlmClient client,
			ModelInvoker.Outcome outcome, String resultOverride) {
		String result = resultOverride != null ? resultOverride
				: outcome.timedOut() ? "TIMED_OUT" : outcome.response() != null ? "SUCCEEDED" : "FAILED";
		newTransaction.executeWithoutResult(status -> callRepository.save(QuizAiCall.of(userId, purpose,
				generationId, gradingRunId, client.provider().apiValue(), client.model(),
				Math.max(outcome.attempts(), outcome.timedOut() ? 1 : 0), result,
				outcome.errorCode() == null ? null : outcome.errorCode().name(),
				outcome.response() == null ? null : outcome.response().inputTokens(),
				outcome.response() == null ? null : outcome.response().outputTokens(), outcome.latencyMs(),
				LocalDateTime.now(clock))));
	}
}
