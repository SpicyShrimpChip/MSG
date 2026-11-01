package com.ailms.backend.domain.analysis.entity;

/**
 * 개념 하나에 대한 모델 의견 비교. 정확도·숙련도가 아니므로 화면에 "정확도 67%"처럼 보여 주지 않는다.
 * 몇 개 모델이 판단했고(judgedCount) 몇 개가 같았는지(agreeCount)를 함께 보여 준다.
 */
public enum ConsensusOutcome {

	/** 판단한 모델(2개 이상)이 모두 같음. 3개 중 3개면 "전원 일치" */
	UNANIMOUS,
	/** 2개 이상이 같고 다른 의견도 있음 (3개 중 2개 = "다수 의견") */
	MAJORITY,
	/** 판단한 모델(2개 이상)이 모두 다름 */
	DISAGREEMENT,
	/** 판단한 모델이 하나뿐이라 비교할 수 없음 */
	SINGLE_MODEL,
	/** 이 개념을 판단한 모델이 없음 */
	NO_RESULT;

	public String apiValue() {
		return ApiValues.of(this);
	}
}
