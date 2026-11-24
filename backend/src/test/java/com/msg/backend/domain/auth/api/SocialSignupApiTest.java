package com.msg.backend.domain.auth.api;

import static com.msg.backend.support.KakaoLoginFlow.REFRESH_COOKIE;
import static com.msg.backend.support.KakaoLoginFlow.SIGNUP_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.support.FakeKakaoServer;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.KakaoLoginFlow;
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestApi;
import com.msg.backend.support.TestKakao;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 가입 마무리: 카카오에서 처음 온 사용자가 약관에 동의하면 그때 계정과 카카오 연결을 만들고 로그인한다
@IntegrationTest
class SocialSignupApiTest {

	private static final String EXPIRED = "가입 정보가 만료됐어요. 카카오 로그인을 다시 해 주세요.";

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

	@AfterEach
	void tearDown() {
		clock.reset();
	}

	@Test
	void pending은_카카오_이메일과_닉네임을_보여준다() throws Exception {
		Cookie ticket = ticket();

		mockMvc.perform(get("/api/auth/social/pending").cookie(ticket))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.provider").value("kakao"))
				.andExpect(jsonPath("$.data.email").value("kakao@example.com"))
				.andExpect(jsonPath("$.data.nickname").value("카카오닉"));
	}

	@Test
	void 약관에_동의하면_계정이_생기고_로그인된다() throws Exception {
		MockHttpServletResponse response = signup(ticket(), body("새 사용자", true, true))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.user.email").value("kakao@example.com"))
				.andExpect(jsonPath("$.data.user.name").value("새 사용자"))
				.andExpect(jsonPath("$.data.user.hasPassword").value(false))
				.andExpect(jsonPath("$.data.user.learningPurpose").value("work"))
				.andExpect(jsonPath("$.data.user.aiQuizConsent").value(true))
				.andReturn().getResponse();

		assertThat(KakaoLoginFlow.setCookie(response, SIGNUP_COOKIE).orElseThrow()).contains("Max-Age=0");
		assertThat(KakaoLoginFlow.setCookie(response, REFRESH_COOKIE).orElseThrow()).contains("HttpOnly");
		User user = userRepository.findByEmail("kakao@example.com").orElseThrow();
		assertThat(user.hasPassword()).isFalse();
		assertThat(socialAccountRepository.findAllByUserId(user.getId())).singleElement()
				.extracting("providerUserId").isEqualTo("4242");
		// 받은 Access Token으로 바로 내 정보를 볼 수 있다
		String accessToken = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.data.accessToken");
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, TestApi.bearer(accessToken)))
				.andExpect(status().isOk());
	}

	@Test
	void 약관_동의가_없으면_400이고_DB는_그대로() throws Exception {
		signup(ticket(), body("새 사용자", false, true)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("서비스 이용약관에 동의해 주세요."));
		signup(ticket(), body(" ", true, true)).andExpect(status().isBadRequest());

		assertThat(userRepository.count()).isZero();
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void 쿠키가_없거나_만료되면_401() throws Exception {
		mockMvc.perform(get("/api/auth/social/pending"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(EXPIRED));
		signup(null, body("새 사용자", true, true)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(EXPIRED));

		Cookie ticket = ticket();
		clock.advance(Duration.ofMinutes(11));

		mockMvc.perform(get("/api/auth/social/pending").cookie(ticket)).andExpect(status().isUnauthorized());
		signup(ticket, body("새 사용자", true, true)).andExpect(status().isUnauthorized());
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void Access_Token을_가입_쿠키에_넣으면_401() throws Exception {
		String accessToken = TestApi.signupAndLogin(mockMvc, objectMapper, "other@example.com", Map.of());

		signup(new Cookie(SIGNUP_COOKIE, accessToken), body("새 사용자", true, true))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void X_Requested_With가_없으면_403() throws Exception {
		mockMvc.perform(post("/api/auth/social/signup").cookie(ticket())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body("새 사용자", true, true))))
				.andExpect(status().isForbidden());
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void 같은_쿠키로_두_번_가입하면_두_번째는_409() throws Exception {
		Cookie ticket = ticket();
		signup(ticket, body("새 사용자", true, true)).andExpect(status().isOk());

		signup(ticket, body("새 사용자", true, true)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("이미 가입된 이메일이에요."));
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void 그새_같은_이메일로_비밀번호_가입했으면_409() throws Exception {
		Cookie ticket = ticket();
		TestApi.signupAndLogin(mockMvc, objectMapper, "kakao@example.com", Map.of());

		signup(ticket, body("새 사용자", true, true)).andExpect(status().isConflict());
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void 같은_쿠키로_동시에_두_번_가입해도_계정은_하나() throws Exception {
		Cookie ticket = ticket();
		Map<String, Object> body = body("새 사용자", true, true);
		CountDownLatch ready = new CountDownLatch(1);

		List<CompletableFuture<Integer>> results = List.of(
				CompletableFuture.supplyAsync(() -> signupAfter(ready, ticket, body)),
				CompletableFuture.supplyAsync(() -> signupAfter(ready, ticket, body)));
		ready.countDown();

		List<Integer> statuses = results.stream().map(future -> future.orTimeout(30, TimeUnit.SECONDS).join())
				.sorted().toList();
		assertThat(statuses).containsExactly(200, 409);
		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
	}

	@Test
	void 가입한_계정은_다음_카카오_로그인에서_바로_로그인된다() throws Exception {
		signup(ticket(), body("새 사용자", true, true)).andExpect(status().isOk());

		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.isEqualTo(TestKakao.FRONTEND + "/auth/social/done?returnTo=%2F");
	}

	private int signupAfter(CountDownLatch ready, Cookie ticket, Map<String, Object> body) {
		try {
			ready.await();
			return signup(ticket, body).andReturn().getResponse().getStatus();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Cookie ticket() throws Exception {
		return KakaoLoginFlow.signupCookie(KakaoLoginFlow.login(mockMvc, null));
	}

	private ResultActions signup(Cookie ticket, Map<String, Object> body) throws Exception {
		var request = post("/api/auth/social/signup")
				.header("X-Requested-With", "XMLHttpRequest")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body));
		if (ticket != null) {
			request.cookie(ticket);
		}
		return mockMvc.perform(request);
	}

	private static Map<String, Object> body(String name, boolean agreedTerms, boolean agreedPrivacy) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("agreedTerms", agreedTerms);
		body.put("agreedPrivacy", agreedPrivacy);
		body.put("agreedAiAnalysis", false);
		body.put("agreedAiQuiz", true);
		body.put("learningPurpose", "work");
		body.put("jobField", "data_analysis");
		return body;
	}
}
