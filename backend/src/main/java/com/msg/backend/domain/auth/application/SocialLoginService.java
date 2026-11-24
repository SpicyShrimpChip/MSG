package com.msg.backend.domain.auth.application;

import com.msg.backend.domain.auth.application.RefreshTokenService.IssuedToken;
import com.msg.backend.domain.auth.application.AuthService.LoginResult;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.auth.dto.LoginRequest;
import com.msg.backend.domain.auth.dto.SocialSignupRequest;
import com.msg.backend.domain.auth.entity.UserSocialAccount;
import com.msg.backend.domain.user.application.LearningProfileValidator;
import com.msg.backend.domain.user.application.RejoinRestriction;
import com.msg.backend.domain.user.dto.UserResponse;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오에서 받은 프로필로 로그인·계정 연결·가입 마무리를 나눈다. 카카오 호출(KakaoClient)은 이 트랜잭션 밖에서 먼저 한다.
 *
 * <ol>
 *   <li>이미 연결된 카카오 회원번호면 그 사용자로 로그인한다.</li>
 *   <li>이메일이 없거나 카카오가 확인하지 않은 이메일이면 막는다(남의 이메일로 그 사람 계정에 연결되는 것 방지).</li>
 *   <li>같은 이메일 회원이 있으면 바로 연결하지 않고 연결 확인 표를 준다. 그 계정의 비밀번호를 확인한 뒤에야 연결한다
 *       (linkWithPassword). 비밀번호 가입은 이메일 주인을 확인하지 않아 남이 미리 만든 계정일 수 있기 때문이다.
 *       그 회원에 다른 카카오가 이미 연결돼 있거나 비밀번호가 없는 계정이면 막는다.</li>
 *   <li>처음 온 사용자는 재가입 제한만 확인하고 DB에 쓰지 않은 채 가입 마무리 표(티켓)를 준다(카카오 정책: 가입 전 저장 금지).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

	static final String LINK_TARGET_GONE_MESSAGE = "연결할 계정을 찾지 못했어요. 카카오 로그인을 다시 해 주세요.";
	static final String ALREADY_LINKED_MESSAGE = "이 계정에는 이미 카카오가 연결되어 있어요.";

	private final UserRepository userRepository;
	private final UserSocialAccountRepository socialAccountRepository;
	private final RefreshTokenService refreshTokenService;
	private final RejoinRestriction rejoinRestriction;
	private final JwtTokenProvider tokenProvider;
	private final LearningProfileValidator profileValidator;
	private final AuthService authService;
	private final Clock clock;

	public sealed interface SocialLoginOutcome permits LoggedIn, NeedsSignup, NeedsLink {
	}

	// 로그인됨: 이 Refresh Token을 쿠키로 심고, 화면이 /api/auth/refresh로 Access Token을 받는다
	public record LoggedIn(String refreshToken) implements SocialLoginOutcome {
	}

	// 가입 마무리 필요: 이 표를 쿠키로 심고 가입 마무리 화면으로 보낸다
	public record NeedsSignup(String ticket) implements SocialLoginOutcome {
	}

	// 같은 이메일의 비밀번호 계정이 있음: 이 표를 쿠키로 심고 비밀번호 확인(계정 연결) 화면으로 보낸다
	public record NeedsLink(String ticket) implements SocialLoginOutcome {
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
			if (!user.hasPassword()
					|| socialAccountRepository.existsByUserIdAndProvider(user.getId(), profile.provider())) {
				throw new SocialLoginException(SocialError.LINK_CONFLICT);
			}
			// DB에는 쓰지 않는다. 비밀번호를 확인해야 연결한다
			return new NeedsLink(tokenProvider.createSocialSignupTicket(normalized(profile, email)));
		}
		ensureCanJoin(email);
		return new NeedsSignup(tokenProvider.createSocialSignupTicket(normalized(profile, email)));
	}

	/**
	 * 같은 이메일의 비밀번호 계정에 카카오를 연결한다. 로그인과 같은 비밀번호 확인·시도 제한(AuthService.login)을 거친 뒤 연결하고 로그인한다.
	 * 틀리면 로그인과 같은 401·429. 그새 계정이 사라졌거나 카카오가 이미 연결됐으면 409.
	 */
	@Transactional
	public LoginResult linkWithPassword(SocialProfile profile, String password, String clientIp) {
		String email = normalizeEmail(profile.email());
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, LINK_TARGET_GONE_MESSAGE));
		if (socialAccountRepository.existsByUserIdAndProvider(user.getId(), profile.provider())
				|| socialAccountRepository
						.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()).isPresent()) {
			throw new BusinessException(HttpStatus.CONFLICT, ALREADY_LINKED_MESSAGE);
		}
		LoginResult result = authService.login(new LoginRequest(email, password), clientIp);
		try {
			socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, profile.provider(),
					profile.providerUserId(), LocalDateTime.now(clock)));
		} catch (DataIntegrityViolationException e) {
			// 같은 표로 거의 동시에 연결한 경우 (이 트랜잭션의 세션 발급도 함께 되돌린다)
			throw new BusinessException(HttpStatus.CONFLICT, ALREADY_LINKED_MESSAGE);
		}
		return result;
	}

	private static SocialProfile normalized(SocialProfile profile, String email) {
		return new SocialProfile(profile.provider(), profile.providerUserId(), email, true, profile.nickname());
	}

	/**
	 * 가입 마무리: 약관 동의를 받은 뒤에야 계정과 카카오 연결을 만들고 로그인한다.
	 * 같은 이메일 회원이 그새 생겼거나 같은 카카오가 이미 연결됐으면(같은 표로 두 번·동시에 가입) 409.
	 * 표는 10분 동안 유효하므로 재가입 제한을 여기서 한 번 더 확인한다.
	 */
	@Transactional
	public LoginResult completeSignup(SocialProfile profile, SocialSignupRequest request) {
		String email = normalizeEmail(profile.email());
		if (userRepository.existsByEmail(email) || socialAccountRepository
				.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()).isPresent()) {
			throw duplicate();
		}
		rejoinRestriction.ensureCanJoin(email);
		LearningProfileValidator.Profile learning = profileValidator.validate(request.learningPurpose(),
				request.jobField(), request.jobFieldOther());

		LocalDateTime now = LocalDateTime.now(clock);
		User user = User.signUpWithSocial(email, request.name().strip(), request.agreedAiAnalysis(), now);
		user.changeLearningProfile(learning.purpose(), learning.jobField(), learning.jobFieldOther(), now);
		user.changeAiQuizConsent(request.agreedAiQuiz(), now);
		try {
			userRepository.saveAndFlush(user);
			socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, profile.provider(),
					profile.providerUserId(), now));
		} catch (DataIntegrityViolationException e) {
			// 같은 표로 거의 동시에 가입한 경우: DB UNIQUE 제약이 막는다
			throw duplicate();
		}
		IssuedToken issued = refreshTokenService.startSession(user.getId());
		String accessToken = tokenProvider.createAccessToken(user.getId(), issued.sessionId());
		return new LoginResult(accessToken, issued.rawToken(), UserResponse.from(user));
	}

	private static BusinessException duplicate() {
		return new BusinessException(HttpStatus.CONFLICT, AuthService.DUPLICATE_EMAIL_MESSAGE);
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
