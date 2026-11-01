package com.ailms.backend.domain.material.entity;

import java.util.Locale;

/**
 * 자료 처리 상태. 파일 저장과 AI 분석을 구분한다. API에는 소문자(uploaded 등)로 내보낸다
 * (frontend/src/types/material.ts의 MaterialStatus).
 */
public enum MaterialStatus {

	/** 파일을 저장했고 AI 분석은 아직 시작하지 않았다. 지금은 올린 자료가 모두 이 상태다 */
	UPLOADED,
	/** 텍스트 추출이나 AI 분석을 실제로 하고 있다 */
	PROCESSING,
	/** 처리가 끝났다 */
	READY,
	/** 처리 중 오류가 났다 (예: 글자를 뽑을 수 없는 스캔 파일) */
	FAILED;

	public String apiValue() {
		return name().toLowerCase(Locale.ROOT);
	}
}
