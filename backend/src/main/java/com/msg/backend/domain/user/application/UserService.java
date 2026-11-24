package com.msg.backend.domain.user.application;

import com.msg.backend.domain.conversation.application.ConversationService;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.dto.LearningProfileRequest;
import com.msg.backend.domain.user.dto.UserResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final ConversationService conversationService;
	private final LearningProfileValidator profileValidator;
	private final Clock clock;

	public UserResponse getMe(Long userId) {
		return UserResponse.from(userRepository.getLoginUser(userId));
	}

	/**
	 * 동의를 바꾼다(보낸 항목만). AI 대화 분석 동의를 바꾼다. 철회하면 가져온 대화·가져오기 기록·학습 분석 결과를 같은 트랜잭션에서 지운다
	 * (개인정보처리방침의 보유 기간 "동의를 철회하거나 탈퇴할 때까지", docs/frontend-requests.md "인증·마이페이지").
	 * 사용자 행을 잠가 같은 사용자의 대화 가져오기와 차례로 처리한다.
	 */
	@Transactional
	public UserResponse changeConsents(Long userId, Boolean analysis, Boolean quiz) {
		var user = userRepository.lockLoginUser(userId);
		LocalDateTime now = LocalDateTime.now(clock);
		if (quiz != null) {
			// 철회해도 이미 만든 퀴즈·채점 결과는 남긴다 (사용자의 학습 기록). 이후 AI 생성·채점만 멈춘다
			user.changeAiQuizConsent(quiz, now);
		}
		if (analysis == null) {
			return UserResponse.from(user);
		}
		boolean consent = analysis;
		user.changeAiAnalysisConsent(consent, now);
		if (!consent) {
			// 이미 철회한 상태에서 다시 철회해도 남은 대화가 없도록 매번 지운다
			// 그 대화로 만든 학습 분석 결과도 deleteAllOfUser가 AnalysisCleanup으로 함께 지운다
			// 숙련도는 분석 결과를 돌려줄 때 계산하고, 단계 기록(concept_mastery)은 analysis_run과 함께(CASCADE) 지워져 다시 계산할 것이 없다
			conversationService.deleteAllOfUser(userId);
		}
		return UserResponse.from(user);
	}

	/**
	 * 학습 목적·관심 직무를 바꾼다 (마이페이지). 이미 만든 퀴즈는 그대로 두고, 다음에 만드는 퀴즈의 기본값만 바뀐다.
	 */
	@Transactional
	public UserResponse changeLearningProfile(Long userId, LearningProfileRequest request) {
		LearningProfileValidator.Profile profile = profileValidator.validate(request.learningPurpose(),
				request.jobField(), request.jobFieldOther());
		var user = userRepository.getLoginUser(userId);
		user.changeLearningProfile(profile.purpose(), profile.jobField(), profile.jobFieldOther(),
				LocalDateTime.now(clock));
		return UserResponse.from(user);
	}
}
