package com.msg.backend.domain.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.support.FakeKakaoServer;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestKakao;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

// 카카오 로그인 시작: 카카오 인가 주소로 보내고, 돌아올 때 확인할 state를 쿠키로 심는다
@IntegrationTest
class SocialAuthApiTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private FakeKakaoServer kakao;

	@Test
	void providers는_카카오를_돌려준다() throws Exception {
		mockMvc.perform(get("/api/auth/social/providers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0]").value("kakao"))
				.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void start는_카카오_인가_주소로_보내고_state_쿠키를_심는다() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/kakao/start").param("returnTo", "/analysis"))
				.andExpect(status().isFound())
				.andReturn().getResponse();

		UriComponents location = UriComponentsBuilder.fromUriString(response.getHeader(HttpHeaders.LOCATION)).build();
		assertThat(location.getScheme() + "://" + location.getHost() + ":" + location.getPort() + location.getPath())
				.isEqualTo(kakao.baseUrl() + "/oauth/authorize");
		assertThat(location.getQueryParams().getFirst("response_type")).isEqualTo("code");
		assertThat(location.getQueryParams().getFirst("client_id")).isEqualTo(TestKakao.CLIENT_ID);
		assertThat(decode(location.getQueryParams().getFirst("redirect_uri"))).isEqualTo(TestKakao.REDIRECT_URI);
		String state = location.getQueryParams().getFirst("state");
		assertThat(state).isNotBlank().hasSizeGreaterThanOrEqualTo(43);

		List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
		// 탭마다 따로 두도록 이름에 state를 붙인다
		String stateCookie = cookies.stream().filter(c -> c.startsWith("msg_oauth_state_" + state + "=")).findFirst()
				.orElseThrow();
		assertThat(stateCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/auth/kakao")
				.contains("Max-Age=600");
		String value = stateCookie.substring(("msg_oauth_state_" + state + "=").length(), stateCookie.indexOf(';'));
		assertThat(value).startsWith(state + ".");
		// 카카오를 아직 부르지 않는다 (브라우저가 인가 화면으로 간다)
		assertThat(kakao.totalCalls()).isZero();
	}

	@Test
	void start를_두_번_부르면_state가_매번_다르다() throws Exception {
		String first = mockMvc.perform(get("/api/auth/kakao/start")).andReturn().getResponse().getHeader("Location");
		String second = mockMvc.perform(get("/api/auth/kakao/start")).andReturn().getResponse().getHeader("Location");

		assertThat(stateOf(first)).isNotEqualTo(stateOf(second));
	}

	private static String stateOf(String location) {
		return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
	}

	private static String decode(String value) {
		return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
	}
}
