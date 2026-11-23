package com.msg.backend.global.llm;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DB(ai_daily_usage)에 하루 호출 수를 세는 상한. 서버를 다시 켜도, 서버가 여러 대여도 같은 수를 본다.
 * 부르는 쪽 트랜잭션과 따로(REQUIRES_NEW) 세서, 부르는 쪽이 되돌려져도 이미 부른 호출은 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DbAiCallQuota implements AiCallQuota {

	private final AiDailyLimitProperties properties;
	private final AiDailyUsageRepository repository;
	private final PlatformTransactionManager transactionManager;
	private final Clock clock;

	@Override
	public boolean tryAcquire() {
		if (!properties.enabled()) {
			return true;
		}
		LocalDate today = today();
		ensureRow(today);
		Integer updated = newTransaction().execute(status -> repository.incrementIfBelow(today, properties.calls()));
		if (updated == null || updated == 0) {
			log.warn("서비스 전체 하루 AI 호출 상한({}번)에 닿아 모델을 부르지 않았어요", properties.calls());
			return false;
		}
		return true;
	}

	@Override
	public boolean hasRemaining() {
		if (!properties.enabled()) {
			return true;
		}
		return repository.findById(today())
				.map(usage -> usage.getCallCount() < properties.calls())
				.orElse(true);
	}

	// 오늘 줄이 없으면 만든다. 두 요청이 동시에 만들면 하나는 기본 키 충돌로 실패하는데, 줄은 이미 있으니 그대로 넘어간다
	private void ensureRow(LocalDate today) {
		if (repository.existsById(today)) {
			return;
		}
		try {
			newTransaction().executeWithoutResult(status -> repository.saveAndFlush(AiDailyUsage.start(today)));
		} catch (DataIntegrityViolationException e) {
			// 다른 요청이 먼저 만들었다
		}
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(properties.zone()));
	}

	private TransactionTemplate newTransaction() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template;
	}
}
