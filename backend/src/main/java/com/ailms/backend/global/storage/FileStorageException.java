package com.ailms.backend.global.storage;

/**
 * 파일 저장소 오류. 메시지에는 key 같은 내부 정보가 들어가므로 응답에 싣지 않고 로그에만 남긴다
 * (전역 예외 처리가 500과 일반 안내 문구로 바꾼다).
 */
public class FileStorageException extends RuntimeException {

	public FileStorageException(String message) {
		super(message);
	}

	public FileStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
