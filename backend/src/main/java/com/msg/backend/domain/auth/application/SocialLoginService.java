package com.msg.backend.domain.auth.application;

import com.msg.backend.domain.auth.application.RefreshTokenService.IssuedToken;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.auth.entity.UserSocialAccount;
import com.msg.backend.domain.user.application.RejoinRestriction;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오에서 받은 프로필로 로그인·계정 연결·가입 마무리를 나눈다. 카카오 호출(KakaoClient)은 이 트랜잭션 밖에서 먼저 한다.
 *
 * <ol>
 *   <li>이미 연결된 카카오 회원번호면 그 사용자로 로그인한다.</li>
 *   <li>이메일이 없거나 카카오가 확인하지 않은 이메일이면 막는다(남의 이메일로 그 사람 계정에 연결되는 것 방지).</li>
 *   <li>같은 이메일 회원이 있으면 카카오를 연결하고 로그인한다. 그 회원에 다른 카카오가 이미 연결돼 있으면 막는다.</li>
 *   <li>처음 온 사용자는 재가입 제한만 확인하고 DB에 쓰지 않은 채 가입 마무리 표(티켓)를 준다(카카오 정책: 가입 전 저장 금지).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

	private final UserRepository userRepository;
	private final UserSocialAccountRepository socialAccountRepository;
	private final RefreshTokenService refreshTokenService;
	private final RejoinRestriction rejoinRestriction;
	private final JwtTokenProvider tokenProvider;
	private final Clock clock;

	public sealed interface SocialLoginOutcome permits LoggedIn, NeedsSignup {
	}

	// 로그인됨: 이 Refresh Token을 쿠키로 심고, 화면이 /api/auth/refresh로 Access Token을 받는다
	public record LoggedIn(String refreshToken) implements SocialLoginOutcome {
	}

	// 가입 마무리 필요: 이 표를 쿠키로 심고 가입 마무리 화면으로 보낸다
	public record NeedsSignup(String ticket) implements SocialLoginOutcome {
	}

	@Transactional
	public SocialLoginOutcome handle(SocialProfile profile) {
		Optional<UserSocialAccount> linked = socialAccountRepository
				.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
		if (linked.isPresent()) {
			return login(linked.get().getUserId());
		}
		if (profile.email() == null || profile.email().isBlank()) {
			throw new SocialLoginException(SocialError.EMAIL_REQUIRED);
		}
		if (!profile.emailVerified()) {
			throw new SocialLoginException(SocialError.EMAIL_UNVERIFIED);
		}
		String email = normalizeEmail(profile.email());
		Optional<User> sameEmail = userRepository.findByEmail(email);
		if (sameEmail.isPresent()) {
			User user = sameEmail.get();
			if (socialAccountRepository.existsByUserIdAndProvider(user.getId(), profile.provider())) {
				throw new SocialLoginException(SocialError.LINK_CONFLICT);
			}
			socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, profile.provider(),
					profile.providerUserId(), LocalDateTime.now(clock)));
			return login(user.getId());
		}
		ensureCanJoin(email);
		SocialProfile normalized = new SocialProfile(profile.provider(), profile.providerUserId(), email, true,
				profile.nickname());
		return new NeedsSignup(tokenProvider.createSocialSignupTicket(normalized));
	}

	// 탈퇴 뒤 재가입 제한 기간이면 막는다 (가입 마무리에서도 한 번 더 부른다)
	void ensureCanJoin(String email) {
		try {
			rejoinRestriction.ensureCanJoin(email);
		} catch (BusinessException e) {
			throw new SocialLoginException(SocialError.REJOIN_RESTRICTED);
		}
	}

	private LoggedIn login(Long userId) {
		IssuedToken issued = refreshTokenService.startSession(userId);
		return new LoggedIn(issued.rawToken());
	}

	static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}
}
