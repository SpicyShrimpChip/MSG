package com.ailms.backend.global.llm;

import lombok.Getter;

/**
 * 모델 호출 실패. 메시지에는 요청·응답 본문(사용자 대화)을 넣지 않는다.
 */
@Getter
public class LlmException extends RuntimeException {

	public enum Code {
		/** 시간 안에 응답이 오지 않음 */
		TIMEOUT(true),
		/** 호출 한도 초과 (429) */
		RATE_LIMITED(true),
		/** 제공사 서버 오류 (5xx) */
		SERVER_ERROR(true),
		/** 키가 틀렸거나 권한이 없음 (401·403) */
		AUTH_FAILED(false),
		/** 요청이 거절됨 (400 등, 모델 이름이 틀린 경우 포함) */
		REJECTED(false),
		/** 응답 형식을 읽을 수 없음 */
		BAD_RESPONSE(false),
		/** 연결 실패 */
		NETWORK(true);

		private final boolean retryable;

		Code(boolean retryable) {
			this.retryable = retryable;
		}

		public boolean retryable() {
			return retryable;
		}
	}

	private final Code code;

	public LlmException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public LlmException(Code code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}
}
