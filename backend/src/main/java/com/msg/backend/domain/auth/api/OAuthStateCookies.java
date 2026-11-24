package com.msg.backend.domain.auth.api;

import com.msg.backend.domain.auth.application.AuthProperties;
import com.msg.backend.domain.auth.application.ReturnTo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * 카카오 로그인 state 확인 쿠키. 시작할 때 무작위 state를 만들어 카카오로 보내고, 같은 값을 이 브라우저의 쿠키에 둔다.
 * 콜백에 온 state가 쿠키와 같을 때만 진행한다(다른 사람의 인가 코드를 내 브라우저에 밀어 넣는 로그인 CSRF 방지).
 * 쿠키에는 돌아갈 화면 경로(returnTo)도 같이 담는다. 콜백에서 한 번 읽으면 늘 지운다.
 *
 * <p>쿠키 이름에 state를 붙여(msg_oauth_state_&lt;state&gt;) 탭마다 따로 둔다. 이름이 하나면 두 탭에서 차례로 시작할 때
 * 나중 탭이 덮어써서 먼저 탭의 콜백이 state 오류가 된다. 콜백은 자기 쿠키만 지우고, 끝까지 가지 않은 탭의 쿠키는 10분 뒤 만료된다.
 * 콜백의 state는 우리가 만든 모양(base64url 43자)일 때만 쿠키 이름으로 쓴다(Set-Cookie 헤더에 남의 글자가 들어가지 않게).
 *
 * <p>SameSite=Lax: 카카오에서 우리 주소로 돌아오는 최상위 GET 이동에 쿠키가 실려야 한다(Strict면 실리지 않는다).
 * Path=/api/auth/kakao: 시작·콜백 주소에만 보낸다.
 */
@Component
public class OAuthStateCookies {

	static final String NAME_PREFIX = "msg_oauth_state_";
	private static final String PATH = "/api/auth/kakao";
	private static final Duration MAX_AGE = Duration.ofMinutes(10);
	private static final int STATE_BYTES = 32;
	private static final Pattern STATE_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final SecureRandom random = new SecureRandom();
	private final boolean secure;

	public OAuthStateCookies(AuthProperties authProperties) {
		this.secure = authProperties.refreshCookie().secure();
	}

	// 새 state를 만들어 쿠키에 심고 돌려준다 (카카오 인가 주소에 붙인다)
	public String issue(HttpServletResponse response, String returnTo) {
		byte[] bytes = new byte[STATE_BYTES];
		random.nextBytes(bytes);
		String state = encode(bytes);
		String value = state + "." + encode(ReturnTo.sanitize(returnTo).getBytes(StandardCharsets.UTF_8));
		add(response, state, value, MAX_AGE);
		return state;
	}

	// 콜백의 state가 쿠키와 같으면 돌아갈 경로를 준다. 맞든 틀리든 쿠키는 지운다(한 번만 쓴다)
	public Optional<String> consume(HttpServletRequest request, HttpServletResponse response, String state) {
		if (state == null || !STATE_FORMAT.matcher(state).matches()) {
			return Optional.empty();
		}
		Cookie cookie = WebUtils.getCookie(request, NAME_PREFIX + state);
		add(response, state, "", Duration.ZERO);
		if (cookie == null) {
			return Optional.empty();
		}
		String value = cookie.getValue();
		int dot = value.indexOf('.');
		if (dot <= 0) {
			return Optional.empty();
		}
		byte[] expected = value.substring(0, dot).getBytes(StandardCharsets.US_ASCII);
		if (!MessageDigest.isEqual(expected, state.getBytes(StandardCharsets.US_ASCII))) {
			return Optional.empty();
		}
		try {
			String returnTo = new String(Base64.getUrlDecoder().decode(value.substring(dot + 1)), StandardCharsets.UTF_8);
			return Optional.of(ReturnTo.sanitize(returnTo));
		} catch (IllegalArgumentException e) {
			return Optional.of(ReturnTo.HOME);
		}
	}

	private void add(HttpServletResponse response, String state, String value, Duration age) {
		ResponseCookie cookie = ResponseCookie.from(NAME_PREFIX + state, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path(PATH)
				.maxAge(age)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private static String encode(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
