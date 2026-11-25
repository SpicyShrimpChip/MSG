package com.msg.backend.domain.material.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 사용자 한 명이 저장할 수 있는 학습자료 양 (app.material). 한 사용자가 자료를 계속 올려 서버 디스크를 채우면
 * 서비스 전체가 멈추므로 올리기 전에 확인한다(MaterialService.upload). 파일 하나 크기 상한은 따로
 * (spring.servlet.multipart.max-file-size, Material.MAX_FILE_SIZE).
 *
 * <p>설정 record는 생성자를 하나만 둔다(docs/wiki/backend.md).
 *
 * @param maxTotalSize 내 자료 파일 크기 합계 상한. 0 이하면 상한 없음
 * @param maxCount     내 자료 개수 상한. 0 이하면 상한 없음
 */
@ConfigurationProperties(prefix = "app.material")
public record MaterialQuotaProperties(DataSize maxTotalSize, int maxCount) {

	public MaterialQuotaProperties {
		if (maxTotalSize == null) {
			maxTotalSize = DataSize.ofBytes(0);
		}
	}

	boolean limitsTotalSize() {
		return maxTotalSize.toBytes() > 0;
	}

	boolean limitsCount() {
		return maxCount > 0;
	}
}
