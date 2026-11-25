package com.msg.backend.global.llm;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DB에 하루 호출 수를 세는 상한. 서비스 전체는 ai_daily_usage, 사용자별은 user_ai_call_daily(V4)에 센다.
 * 서버를 다시 켜도, 서버가 여러 대여도 같은 수를 본다.
 * 부르는 쪽 트랜잭션과 따로(REQUIRES_NEW) 세서, 부르는 쪽이 되돌려져도 이미 부른 호출은 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DbAiCallQuota implements AiCallQuota {

	private final AiDailyLimitProperties properties;
	private final AiDailyUsageRepository repository;
	private final UserAiCallDailyRepository userRepository;
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

	@Override
	public boolean tryAcquireForUser(Long userId) {
		if (!properties.userEnabled() || userId == null) {
			return true;
		}
		LocalDate today = today();
		ensureUserRow(userId, today);
		Integer updated = newTransaction()
				.execute(status -> userRepository.incrementIfBelow(userId, today, properties.userCalls()));
		if (updated == null || updated == 0) {
			log.info("사용자 {}가 하루 AI 호출 몫({}번)을 다 써서 모델을 부르지 않았어요", userId, properties.userCalls());
			return false;
		}
		return true;
	}

	@Override
	public boolean hasUserRemaining(Long userId, int calls) {
		if (!properties.userEnabled() || userId == null) {
			return true;
		}
		int used = userRepository.findCallCount(userId, today()).orElse(0);
		return properties.userCalls() - used >= Math.max(1, calls);
	}

	// 어제보다 앞선 사용자별 줄을 지운다 (오늘 줄은 시간대가 달라도 남도록 하루 여유를 둔다)
	@Scheduled(fixedDelayString = "${app.ai-daily-limit.user-usage-cleanup-interval:6h}",
			initialDelayString = "${app.ai-daily-limit.user-usage-cleanup-interval:6h}")
	@Transactional
	public int purgeOldUserRows() {
		int deleted = userRepository.deleteBefore(today().minusDays(1));
		if (deleted > 0) {
			log.info("지난 사용자별 하루 AI 호출 수 {}줄을 지웠다", deleted);
		}
		return deleted;
	}

	// 오늘 줄이 없으면 만든다
	private void ensureRow(LocalDate today) {
		if (!repository.existsById(today)) {
			createRow(today);
		}
	}

	private void ensureUserRow(Long userId, LocalDate today) {
		if (!userRepository.existsById(new UserAiCallDaily.Key(userId, today))) {
			createUserRow(userId, today);
		}
	}

	// 줄을 만든다. 없는 줄을 본 뒤 다른 요청이 먼저 만들고 수를 올렸을 수 있다.
	// 그때는 기본 키 충돌로 실패하고(이미 있는 줄은 그대로) 넘어간다
	void createRow(LocalDate today) {
		try {
			newTransaction().executeWithoutResult(status -> repository.saveAndFlush(AiDailyUsage.start(today)));
		} catch (DataIntegrityViolationException e) {
			// 다른 요청이 먼저 만들었다
		}
	}

	void createUserRow(Long userId, LocalDate today) {
		try {
			newTransaction().executeWithoutResult(
					status -> userRepository.saveAndFlush(UserAiCallDaily.start(userId, today)));
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
