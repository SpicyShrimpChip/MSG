package com.ailms.backend.domain.user.application;

import com.ailms.backend.domain.conversation.application.ConversationService;
import com.ailms.backend.domain.user.dao.UserRepository;
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
	private final Clock clock;

	public UserResponse getMe(Long userId) {
		return userRepository.findById(userId)
				.map(UserResponse::from)
				// 토큰은 유효하지만 계정이 사라진 경우 (탈퇴 직후 등)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
	}

	/**
	 * AI 대화 분석 동의를 바꾼다. 철회하면 가져온 대화·가져오기 기록·학습 분석 결과를 같은 트랜잭션에서 지운다
	 * (개인정보처리방침의 보유 기간 "동의를 철회하거나 탈퇴할 때까지", docs/frontend-requests.md "인증·마이페이지").
	 * 사용자 행을 잠가 같은 사용자의 대화 가져오기와 차례로 처리한다.
	 */
	@Transactional
	public UserResponse changeAiAnalysisConsent(Long userId, boolean consent) {
		var user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		user.changeAiAnalysisConsent(consent, LocalDateTime.now(clock));
		if (!consent) {
			// 이미 철회한 상태에서 다시 철회해도 남은 대화가 없도록 매번 지운다
			// 그 대화로 만든 학습 분석 결과도 deleteAllOfUser가 AnalysisCleanup으로 함께 지운다
			// TODO: 숙련도 계산이 생기면 철회 뒤 다시 계산한다
			conversationService.deleteAllOfUser(userId);
		}
		return UserResponse.from(user);
	}
}
