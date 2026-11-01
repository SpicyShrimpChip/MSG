package com.ailms.backend.global.common;

/**
 * 모든 API의 공통 응답 형식: { success, message, data }.
 * message는 프론트가 사용자에게 그대로 보여 주므로 쉬운 한국어로 쓴다 (frontend/src/types/api.ts와 같은 모양).
 */
public record ApiResponse<T>(boolean success, String message, T data) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "", data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, message, data);
	}

	public static ApiResponse<Void> error(String message) {
		return new ApiResponse<>(false, message, null);
	}

	// 실패했지만 무엇이 잘못됐는지 목록 등을 함께 알려야 할 때
	public static <T> ApiResponse<T> error(String message, T data) {
		return new ApiResponse<>(false, message, data);
	}
}
