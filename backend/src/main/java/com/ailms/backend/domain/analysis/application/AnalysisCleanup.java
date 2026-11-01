package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.analysis.dao.AnalysisRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화가 사라질 때 그 대화로 만든 분석 결과를 지운다. 모델이 쓴 판단 이유에는 대화 내용이 담길 수 있어서,
 * 근거 메시지만이 아니라 그 대화를 쓴 분석 전체를 지운다 (개인정보처리방침: 대화를 지우면 그로부터 계산한 결과도 지운다).
 * 대화·동의를 바꾸는 트랜잭션 안에서만 부른다. 과목·회원을 지울 때는 외래 키(ON DELETE CASCADE)로 함께 지워진다.
 * 사용자 피드백(concept_feedback)은 사용자가 직접 남긴 기록이라 지우지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AnalysisCleanup {

	private final AnalysisRunRepository runRepository;

	// AI 대화 분석 동의 철회
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAllOfUser(Long userId) {
		delete(runRepository.findIdsByUserId(userId));
	}

	// 대화 한 건 삭제
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteRunsUsingConversation(Long conversationId) {
		delete(runRepository.findIdsUsingConversation(conversationId));
	}

	private void delete(List<Long> runIds) {
		if (!runIds.isEmpty()) {
			runRepository.deleteAllByIdIn(runIds);
		}
	}
}
