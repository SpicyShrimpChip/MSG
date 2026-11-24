package com.msg.backend.global.llm;

import com.msg.backend.global.error.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 하루 AI 호출 상한. 서비스 전체 상한과 사용자 한 명의 몫(app.ai-daily-limit)을 함께 본다.
 * {@link ModelInvoker}가 모델을 부르기 직전(다시 부를 때도)마다 사용자 몫 → 서비스 전체 순서로 한 번씩 받아 간다.
 */
public interface AiCallQuota {

	String EXHAUSTED_MESSAGE = "오늘 서비스 전체 AI 사용량이 다 찼어요. 내일 다시 시도해 주세요.";
	String USER_EXHAUSTED_MESSAGE = "오늘 쓸 수 있는 AI 사용량을 다 썼어요. 내일 다시 시도해 주세요.";

	/**
	 * AI 기능을 시작하기 전에 확인한다. 이 사용자에게 calls번(이번 기능이 부를 모델 수)이 남지 않았거나
	 * 서비스 전체 상한에 닿았으면 429. 받아 가지는 않는다
	 */
	default void ensureRemaining(Long userId, int calls) {
		if (!hasUserRemaining(userId, calls)) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, USER_EXHAUSTED_MESSAGE);
		}
		if (!hasRemaining()) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, EXHAUSTED_MESSAGE);
		}
	}

	/** 서비스 전체에서 호출 한 번을 받아 간다. 오늘 상한에 닿았으면 false (부르지 않는다) */
	boolean tryAcquire();

	/** 서비스 전체에 오늘 남은 호출이 있는지 (받아 가지는 않는다) */
	boolean hasRemaining();

	/** 이 사용자의 몫에서 호출 한 번을 받아 간다. 오늘 몫을 다 썼으면 false (부르지 않는다) */
	default boolean tryAcquireForUser(Long userId) {
		return true;
	}

	/** 이 사용자에게 오늘 calls번 이상 남았는지 (받아 가지는 않는다) */
	default boolean hasUserRemaining(Long userId, int calls) {
		return true;
	}

	/** 상한이 없는 것 (단위 테스트용) */
	AiCallQuota UNLIMITED = new AiCallQuota() {
		@Override
		public boolean tryAcquire() {
			return true;
		}

		@Override
		public boolean hasRemaining() {
			return true;
		}
	};
}
