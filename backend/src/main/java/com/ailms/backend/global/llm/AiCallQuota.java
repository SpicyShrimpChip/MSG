package com.ailms.backend.global.llm;

import com.ailms.backend.global.error.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전체 하루 AI 호출 상한. {@link ModelInvoker}가 모델을 부르기 직전(다시 부를 때도)마다 한 번씩 받아 간다.
 */
public interface AiCallQuota {

	String EXHAUSTED_MESSAGE = "오늘 서비스 전체 AI 사용량이 다 찼어요. 내일 다시 시도해 주세요.";

	/** AI 기능을 시작하기 전에 확인한다. 오늘 상한에 닿았으면 429 */
	default void ensureRemaining() {
		if (!hasRemaining()) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, EXHAUSTED_MESSAGE);
		}
	}

	/** 호출 한 번을 받아 간다. 오늘 상한에 닿았으면 false (부르지 않는다) */
	boolean tryAcquire();

	/** 오늘 남은 호출이 있는지 (기능을 시작하기 전에 미리 확인, 받아 가지는 않는다) */
	boolean hasRemaining();

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
