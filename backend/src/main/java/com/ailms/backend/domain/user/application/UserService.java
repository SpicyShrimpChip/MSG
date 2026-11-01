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
	 * AI 대화 분석 동의를 바꾼다. 철회하면 가져온 대화와 가져오기 기록을 같은 트랜잭션에서 지운다
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
			// TODO: 대화 분석(개념 분류·근거·숙련도)을 만들면 그 결과도 여기서 지우고 숙련도를 다시 계산한다
			conversationService.deleteAllOfUser(userId);
		}
		return UserResponse.from(user);
	}
}
