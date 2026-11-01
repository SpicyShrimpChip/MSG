package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.analysis.entity.ConsensusOutcome;
import com.ailms.backend.domain.analysis.entity.UnderstandingStatus;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * 개념 하나에 대한 모델 판단을 비교한다 (코드 규칙). 결과는 의견이 얼마나 같은지일 뿐 정확도·숙련도가 아니다.
 * <ul>
 * <li>판단한 모델이 0개: NO_RESULT / 1개: SINGLE_MODEL (비교하지 않고 그 판단을 그대로 둔다)</li>
 * <li>판단한 모델(2개 이상)이 모두 같음: UNANIMOUS (3개 중 3개면 전원 일치)</li>
 * <li>2개 이상이 같고 다른 의견도 있음: MAJORITY (3개 중 2개면 다수 의견)</li>
 * <li>모두 다름: DISAGREEMENT (일치한 상태 없음)</li>
 * </ul>
 * 실패하거나 이 개념을 빠뜨린 모델은 판단한 모델 수(judgedCount)에 넣지 않는다.
 */
final class ConsensusCalculator {

	record Result(ConsensusOutcome outcome, UnderstandingStatus status, int judgedCount, int agreeCount) {
	}

	private ConsensusCalculator() {
	}

	static Result compare(Collection<UnderstandingStatus> judgments) {
		int judged = judgments.size();
		if (judged == 0) {
			return new Result(ConsensusOutcome.NO_RESULT, null, 0, 0);
		}
		Map<UnderstandingStatus, Integer> counts = new EnumMap<>(UnderstandingStatus.class);
		judgments.forEach(status -> counts.merge(status, 1, Integer::sum));
		UnderstandingStatus top = null;
		int topCount = 0;
		boolean tied = false;
		for (var entry : counts.entrySet()) {
			if (entry.getValue() > topCount) {
				top = entry.getKey();
				topCount = entry.getValue();
				tied = false;
			} else if (entry.getValue() == topCount) {
				tied = true;
			}
		}
		if (judged == 1) {
			return new Result(ConsensusOutcome.SINGLE_MODEL, top, 1, 1);
		}
		if (topCount == judged) {
			return new Result(ConsensusOutcome.UNANIMOUS, top, judged, topCount);
		}
		if (topCount >= 2 && !tied) {
			return new Result(ConsensusOutcome.MAJORITY, top, judged, topCount);
		}
		return new Result(ConsensusOutcome.DISAGREEMENT, null, judged, topCount);
	}
}
