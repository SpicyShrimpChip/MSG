package com.msg.backend.domain.auth.api;

import static com.msg.backend.support.KakaoLoginFlow.REFRESH_COOKIE;
import static com.msg.backend.support.KakaoLoginFlow.SIGNUP_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.domain.auth.entity.UserSocialAccount;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.support.FakeKakaoServer;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.KakaoLoginFlow;
import com.msg.backend.support.KakaoLoginFlow.Started;
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestApi;
import com.msg.backend.support.TestKakao;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 카카오에서 돌아온 뒤: 연결된 사용자는 로그인, 같은 이메일 회원은 연결 뒤 로그인, 처음 온 사용자는 DB에 쓰지 않고 가입 마무리로
@IntegrationTest
class KakaoCallbackApiTest {

	private static final String FRONTEND = TestKakao.FRONTEND;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private FakeKakaoServer kakao;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserSocialAccountRepository socialAccountRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
		kakao.reset();
		socialAccountRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 연결된_사용자는_바로_로그인된다() throws Exception {
		User user = userRepository.saveAndFlush(User.signUpWithSocial("linked@example.com", "연결", false, now()));
		socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, SocialProvider.KAKAO, "4242", now()));

		MockHttpServletResponse response = KakaoLoginFlow.login(mockMvc, "/analysis");

		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getHeader(HttpHeaders.LOCATION))
				.isEqualTo(FRONTEND + "/auth/social/done?returnTo=%2Fanalysis");
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		String refreshCookie = KakaoLoginFlow.setCookie(response, REFRESH_COOKIE).orElseThrow();
		assertThat(refreshCookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth");

		// 화면이 이 쿠키로 Access Token을 받아 로그인을 이어 간다
		String refreshToken = KakaoLoginFlow.cookieValue(response, REFRESH_COOKIE).orElseThrow();
		mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
						.cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
				.andExpect(status().isOk());
		assertThat(socialAccountRepository.count()).isEqualTo(1);
	}

	@Test
	void 확인된_같은_이메일의_기존_회원은_연결하고_로그인한다() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, "same@example.com", Map.of());
		kakao.user(4242L, "Same@Example.com", true, true, "닉");

		MockHttpServletResponse response = KakaoLoginFlow.login(mockMvc, null);

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/auth/social/done?returnTo=%2F");
		assertThat(KakaoLoginFlow.setCookie(response, REFRESH_COOKIE)).isPresent();
		Long userId = userRepository.findByEmail("same@example.com").orElseThrow().getId();
		assertThat(socialAccountRepository.findAllByUserId(userId))
				.singleElement().extracting(UserSocialAccount::getProviderUserId).isEqualTo("4242");
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void 처음_온_사용자는_가입_마무리로_가고_DB는_그대로다() throws Exception {
		long users = userRepository.count();
		long tokens = refreshTokenRepository.count();

		MockHttpServletResponse response = KakaoLoginFlow.login(mockMvc, "/quizzes");

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/signup/social?returnTo=%2Fquizzes");
		String signupCookie = KakaoLoginFlow.setCookie(response, SIGNUP_COOKIE).orElseThrow();
		assertThat(signupCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/auth/social")
				.contains("Max-Age=600");
		assertThat(KakaoLoginFlow.setCookie(response, REFRESH_COOKIE)).isEmpty();
		assertThat(userRepository.count()).isEqualTo(users);
		assertThat(socialAccountRepository.count()).isZero();
		assertThat(refreshTokenRepository.count()).isEqualTo(tokens);
	}

	@Test
	void state가_다르면_카카오를_부르지_않는다() throws Exception {
		Started started = KakaoLoginFlow.start(mockMvc, null);

		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/kakao/callback")
						.param("code", "c").param("state", "someone-elses-state").cookie(started.stateCookie()))
				.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/login?socialError=state");
		assertThat(kakao.totalCalls()).isZero();
	}

	@Test
	void state_쿠키가_없으면_카카오를_부르지_않는다() throws Exception {
		Started started = KakaoLoginFlow.start(mockMvc, null);

		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/kakao/callback")
						.param("code", "c").param("state", started.state()))
				.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/login?socialError=state");
		assertThat(kakao.totalCalls()).isZero();
	}

	@Test
	void state_쿠키는_한_번만_쓸_수_있다() throws Exception {
		Started started = KakaoLoginFlow.start(mockMvc, null);
		MockHttpServletResponse first = KakaoLoginFlow.callback(mockMvc, started);
		assertThat(KakaoLoginFlow.setCookie(first, KakaoLoginFlow.STATE_COOKIE).orElseThrow()).contains("Max-Age=0");

		// 브라우저는 지워진 쿠키를 다시 보내지 않지만, 같은 값을 다시 보내도 다음 start의 state와는 다르다
		Started next = KakaoLoginFlow.start(mockMvc, null);
		MockHttpServletResponse replay = mockMvc.perform(get("/api/auth/kakao/callback")
						.param("code", "c").param("state", started.state()).cookie(next.stateCookie()))
				.andReturn().getResponse();

		assertThat(replay.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/login?socialError=state");
	}

	@Test
	void 취소하면_cancelled() throws Exception {
		Started started = KakaoLoginFlow.start(mockMvc, null);

		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/kakao/callback")
						.param("error", "access_denied").param("state", started.state()).cookie(started.stateCookie()))
				.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/login?socialError=cancelled");
		assertThat(kakao.totalCalls()).isZero();
	}

	@Test
	void 카카오가_다른_오류를_주면_failed() throws Exception {
		Started started = KakaoLoginFlow.start(mockMvc, null);

		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/kakao/callback")
						.param("error", "server_error").param("state", started.state()).cookie(started.stateCookie()))
				.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/login?socialError=failed");
	}

	@Test
	void 이메일이_없으면_email_required() throws Exception {
		kakao.user(4242L, null, false, false, "닉");

		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.isEqualTo(FRONTEND + "/login?socialError=email_required");
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void 확인_안_된_이메일이면_email_unverified() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, "victim@example.com", Map.of());
		kakao.user(4242L, "victim@example.com", true, false, "닉");

		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.isEqualTo(FRONTEND + "/login?socialError=email_unverified");
		// 확인 안 된 이메일로 남의 계정에 연결되지 않는다
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void 카카오_장애면_failed고_DB는_그대로() throws Exception {
		kakao.reply(FakeKakaoServer.ME, 500, "{}");

		MockHttpServletResponse response = KakaoLoginFlow.login(mockMvc, null);

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND + "/login?socialError=failed");
		assertThat(KakaoLoginFlow.setCookie(response, SIGNUP_COOKIE)).isEmpty();
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void 이미_다른_카카오가_연결된_회원이면_link_conflict() throws Exception {
		User user = userRepository.saveAndFlush(User.signUpWithSocial("taken@example.com", "기존", false, now()));
		socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, SocialProvider.KAKAO, "1111", now()));
		kakao.user(2222L, "taken@example.com", true, true, null);

		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.isEqualTo(FRONTEND + "/login?socialError=link_conflict");
		assertThat(socialAccountRepository.count()).isEqualTo(1);
	}

	@Test
	void returnTo는_정리되어_넘어간다() throws Exception {
		assertThat(KakaoLoginFlow.login(mockMvc, "//evil.com").getHeader(HttpHeaders.LOCATION))
				.isEqualTo(FRONTEND + "/signup/social?returnTo=%2F");
	}

	@Test
	void 쿼리가_있는_returnTo도_그대로_넘어간다() throws Exception {
		assertThat(KakaoLoginFlow.login(mockMvc, "/quizzes/3?tab=a&b=1").getHeader(HttpHeaders.LOCATION))
				.isEqualTo(FRONTEND + "/signup/social?returnTo=%2Fquizzes%2F3%3Ftab%3Da%26b%3D1");
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
	}
}
