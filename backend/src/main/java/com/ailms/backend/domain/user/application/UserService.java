package com.ailms.backend.domain.user.application;

import com.ailms.backend.domain.conversation.application.ConversationService;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.dto.LearningProfileRequest;
import com.ailms.backend.domain.user.dto.UserResponse;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.security.ApiErrorResponder;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
		return userRepository.findById(userId)
				.map(UserResponse::from)
				// 토큰은 유효하지만 계정이 사라진 경우 (탈퇴 직후 등)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
	}

	/**
	 * 동의를 바꾼다(보낸 항목만). AI 대화 분석 동의를 바꾼다. 철회하면 가져온 대화·가져오기 기록·학습 분석 결과를 같은 트랜잭션에서 지운다
	 * (개인정보처리방침의 보유 기간 "동의를 철회하거나 탈퇴할 때까지", docs/frontend-requests.md "인증·마이페이지").
	 * 사용자 행을 잠가 같은 사용자의 대화 가져오기와 차례로 처리한다.
	 */
	@Transactional
	public UserResponse changeConsents(Long userId, Boolean analysis, Boolean quiz) {
		var user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
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
			// TODO: 숙련도 계산이 생기면 철회 뒤 다시 계산한다
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
		var user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		user.changeLearningProfile(profile.purpose(), profile.jobField(), profile.jobFieldOther(),
				LocalDateTime.now(clock));
		return UserResponse.from(user);
	}
}
