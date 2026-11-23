package com.msg.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인을 브라우저처럼 따라간다: start로 state 쿠키를 받고, 카카오에서 돌아온 것처럼 callback을 부른다.
 * 카카오 쪽 응답은 FakeKakaoServer가 정한다.
 */
public final class KakaoLoginFlow {

	public static final String STATE_COOKIE = "msg_oauth_state";
	public static final String SIGNUP_COOKIE = "msg_social_signup";
	public static final String REFRESH_COOKIE = "refresh_token";

	private KakaoLoginFlow() {
	}

	// start를 불러 얻은 state와 쿠키
	public record Started(String state, Cookie stateCookie) {
	}

	public static Started start(MockMvc mockMvc, String returnTo) throws Exception {
		var request = get("/api/auth/kakao/start");
		if (returnTo != null) {
			request.param("returnTo", returnTo);
		}
		MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
		String state = UriComponentsBuilder.fromUriString(response.getHeader(HttpHeaders.LOCATION)).build()
				.getQueryParams().getFirst("state");
		String value = cookieValue(response, STATE_COOKIE).orElseThrow();
		return new Started(state, new Cookie(STATE_COOKIE, value));
	}

	// 카카오가 code와 state를 붙여 돌려보낸 것처럼 콜백을 부른다
	public static MockHttpServletResponse callback(MockMvc mockMvc, Started started) throws Exception {
		return mockMvc.perform(get("/api/auth/kakao/callback")
						.param("code", "fake-code")
						.param("state", started.state())
						.cookie(started.stateCookie()))
				.andReturn().getResponse();
	}

	// start부터 callback까지 한 번에
	public static MockHttpServletResponse login(MockMvc mockMvc, String returnTo) throws Exception {
		return callback(mockMvc, start(mockMvc, returnTo));
	}

	// 가입 마무리로 보내진 응답에서 가입 마무리 쿠키를 꺼낸다
	public static Cookie signupCookie(MockHttpServletResponse callbackResponse) {
		return new Cookie(SIGNUP_COOKIE, cookieValue(callbackResponse, SIGNUP_COOKIE).orElseThrow());
	}

	public static Optional<String> cookieValue(MockHttpServletResponse response, String name) {
		return setCookie(response, name).map(header -> header.substring(name.length() + 1, header.indexOf(';')));
	}

	public static Optional<String> setCookie(MockHttpServletResponse response, String name) {
		List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
		return headers.stream().filter(header -> header.startsWith(name + "=")).findFirst();
	}
}
