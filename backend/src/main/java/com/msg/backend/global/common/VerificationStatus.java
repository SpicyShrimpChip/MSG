package com.msg.backend.global.common;

/**
 * AI 결과의 검증 상태 (코드 규칙으로 정한 값). 정확도가 아니다.
 * <ul>
 * <li>원문 인용 검증(문제 출처·요약 문장): 서버가 원문에서 문자열로 찾은 결과다. 인용이 원문에 있다는 것만 확인하며,
 * 그 인용을 바탕으로 쓴 설명·정답 전체가 사실이라고 확정하지 않는다.</li>
 * <li>대화 분석 개념: 모델 의견 비교와 실제 퀴즈 결과를 함께 본 값이다 (ConceptVerification).</li>
 * </ul>
 */
public enum VerificationStatus {
	/** 검증 완료: 인용이 원문과 일치 / 대화 분석이 실제 퀴즈 결과와 같은 방향 */
	VERIFIED,
	/** 일부 검증: 인용 일부만 일치 / AI 판단만 있고 퀴즈로 확인하지 않음 */
	PARTIAL,
	/** 추가 확인 필요: 모델 의견이 갈림, 퀴즈 결과와 반대, 오개념 후보 등 */
	NEEDS_CHECK,
	/** 근거 부족: 인용이 없음 / 사용자 발언 근거가 부족해 판단 보류 */
	INSUFFICIENT,
	/** 검증 실패: 인용을 원문에서 찾지 못함 / 판단한 모델이 없음 */
	FAILED;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}
}
