package com.msg.backend.domain.curriculum.application;

import com.msg.backend.domain.analysis.entity.ConceptConsensus;
import com.msg.backend.domain.analysis.entity.ConsensusOutcome;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;

/**
 * 대화 분석의 다수 의견을 커리큘럼 문장으로 옮긴다. 추천 이유(CurriculumService)와 먼저 공부할 이유(CurriculumFlowBuilder)가
 * 같은 낱말을 쓰게 한 곳에 둔다. 의견 불일치 문장은 두 화면이 다르게 써서 각자 둔다.
 */
final class ConsensusText {

	private ConsensusText() {
	}

	// 예: "모델 3개 중 2개가 '부분 이해'으로 봤어요" (마침표 없음). 의견 불일치이거나 상태가 없으면 null
	static String majority(ConceptConsensus agreed) {
		if (agreed.getOutcome() == ConsensusOutcome.DISAGREEMENT || agreed.getStatus() == null) {
			return null;
		}
		return "모델 " + agreed.getJudgedCount() + "개 중 " + agreed.getAgreeCount() + "개가 '" + label(agreed.getStatus())
				+ "'으로 봤어요";
	}

	static String label(UnderstandingStatus status) {
		return switch (status) {
			case UNDERSTOOD -> "이해";
			case PARTIAL -> "부분 이해";
			case NOT_UNDERSTOOD -> "이해 부족";
			case INSUFFICIENT_EVIDENCE -> "판단 근거 부족";
		};
	}
}
