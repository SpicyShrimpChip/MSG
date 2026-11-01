package com.ailms.backend.global.storage;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장소 설정 (app.storage).
 *
 * @param type                 저장소 종류. 지금은 local만 있다
 * @param local                로컬 디스크 저장소 설정
 * @param cleanupRetryInterval 지우지 못한 파일(file_deletion_task)을 다시 지우는 간격
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, Local local, Duration cleanupRetryInterval) {

	/** @param root 파일을 저장할 폴더. 상대 경로는 서버를 실행한 폴더 기준이다 */
	public record Local(Path root) {
	}
}
