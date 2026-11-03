package com.ailms.backend.domain.auth.application;

import com.ailms.backend.domain.auth.application.RefreshTokenService.IssuedToken;
import com.ailms.backend.domain.auth.dto.LoginRequest;
import com.ailms.backend.domain.auth.dto.SignupRequest;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.application.LearningProfileValidator;
import com.ailms.backend.domain.user.dto.UserResponse;
import com.ailms.backend.domain.user.entity.User;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.security.ApiErrorResponder;
import com.ailms.backend.global.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

	static final String DUPLICATE_EMAIL_MESSAGE = "이미 가입된 이메일이에요.";
	static final String PASSWORD_MISMATCH_MESSAGE = "비밀번호가 서로 달라요. 다시 확인해 주세요.";
	// 이메일이 없는지 비밀번호가 틀렸는지 구분하지 않는다 (가입 여부 노출 방지)
	static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호를 확인해 주세요.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final JwtTokenProvider tokenProvider;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final LearningProfileValidator profileValidator;
	private final Clock clock;
	// 없는 이메일로 로그인해도 비밀번호 비교 시간이 비슷하도록 쓰는 해시 (응답 시간으로 가입 여부를 알 수 없게)
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokenService, JwtTokenProvider tokenProvider,
			LoginAttemptLimiter loginAttemptLimiter, LearningProfileValidator profileValidator, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.tokenProvider = tokenProvider;
		this.loginAttemptLimiter = loginAttemptLimiter;
		this.profileValidator = profileValidator;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-password");
	}

	public record LoginResult(String accessToken, String refreshToken, UserResponse user) {
	}

	public record RefreshResult(String accessToken, String refreshToken) {
	}

	@Transactional
	public UserResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (!request.password().equals(request.passwordConfirm())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, PASSWORD_MISMATCH_MESSAGE);
		}
		PasswordPolicy.findViolation(request.password()).ifPresent(message -> {
			throw new BusinessException(HttpStatus.BAD_REQUEST, message);
		});
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(HttpStatus.CONFLICT, DUPLICATE_EMAIL_MESSAGE);
		}

		LearningProfileValidator.Profile profile = profileValidator.validate(request.learningPurpose(),
				request.jobField(), request.jobFieldOther());
		LocalDateTime now = LocalDateTime.now(clock);
		User user = User.signUp(email, passwordEncoder.encode(request.password()), request.name().strip(),
				request.agreedAiAnalysis(), now);
		user.changeLearningProfile(profile.purpose(), profile.jobField(), profile.jobFieldOther(), now);
		user.changeAiQuizConsent(request.agreedAiQuiz(), now);
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 거의 동시에 가입한 경우: DB UNIQUE 제약이 막는다
			throw new BusinessException(HttpStatus.CONFLICT, DUPLICATE_EMAIL_MESSAGE);
		}
		return UserResponse.from(user);
	}

	public LoginResult login(LoginRequest request, String clientIp) {
		String email = normalizeEmail(request.email());
		loginAttemptLimiter.checkAllowed(email, clientIp);

		Optional<User> found = userRepository.findByEmail(email);
		if (found.isEmpty()) {
			// 결과는 버리고 비교 시간만 맞춘다
			passwordMatches(request.password(), dummyPasswordHash);
			throw loginFailed(email, clientIp);
		}
		User user = found.get();
		if (!passwordMatches(request.password(), user.getPasswordHash())) {
			throw loginFailed(email, clientIp);
		}
		loginAttemptLimiter.recordSuccess(email, clientIp);

		IssuedToken refreshToken = refreshTokenService.startSession(user.getId());
		String accessToken = tokenProvider.createAccessToken(user.getId(), refreshToken.sessionId());
		return new LoginResult(accessToken, refreshToken.rawToken(), UserResponse.from(user));
	}

	public RefreshResult refresh(String rawRefreshToken) {
		if (!StringUtils.hasText(rawRefreshToken)) {
			throw unauthorized();
		}
		return refreshTokenService.rotate(rawRefreshToken)
				.map(issued -> new RefreshResult(
						tokenProvider.createAccessToken(issued.userId(), issued.sessionId()), issued.rawToken()))
				.orElseThrow(AuthService::unauthorized);
	}

	public void logout(String rawRefreshToken) {
		if (StringUtils.hasText(rawRefreshToken)) {
			refreshTokenService.revokeSession(rawRefreshToken);
		}
	}

	private BusinessException loginFailed(String email, String clientIp) {
		loginAttemptLimiter.recordFailure(email, clientIp);
		return new BusinessException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
	}

	private boolean passwordMatches(String rawPassword, String passwordHash) {
		// BCrypt는 72바이트를 넘는 비밀번호를 비교하지 못한다. 가입 때 막으므로 그런 비밀번호로 만든 계정은 없다
		if (PasswordPolicy.exceedsMaxBytes(rawPassword)) {
			return false;
		}
		return passwordEncoder.matches(rawPassword, passwordHash);
	}

	private static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static BusinessException unauthorized() {
		return new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE);
	}
}
