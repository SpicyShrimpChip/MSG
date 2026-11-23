package com.msg.backend.domain.auth.api;

import static com.msg.backend.support.TestApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AuthApiTest {

	private static final String PASSWORD = "Passw0rd!";
	private static final String COOKIE = "refresh_token";
	private static final String UNAUTHORIZED = "로그인이 필요해요. 다시 로그인해 주세요.";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	// ---------- 회원가입 ----------

	@Test
	void 회원가입하면_비밀번호를_해시로_저장하고_응답에_넣지_않는다() throws Exception {
		signup(signupBody("New.User@Example.com", PASSWORD))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.email").value("new.user@example.com"))
				.andExpect(jsonPath("$.data.name").value("김주미"))
				.andExpect(jsonPath("$.data.aiAnalysisConsent").value(false))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());

		User saved = userRepository.findByEmail("new.user@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
		assertThat(saved.getTermsAgreedAt()).isNotNull();
	}

	@Test
	void 이미_가입한_이메일은_대소문자가_달라도_409로_거절한다() throws Exception {
		signup(signupBody("dup@example.com", PASSWORD)).andExpect(status().isCreated());

		signup(signupBody("DUP@example.com", PASSWORD))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("이미 가입된 이메일이에요."));
	}

	@Test
	void 이메일_형식_비밀번호_확인_필수_동의가_틀리면_400과_고칠_점을_알려_준다() throws Exception {
		signup(signupBody("not-an-email", PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이메일 형식을 확인해 주세요. (예: name@example.com)"));

		Map<String, Object> mismatch = signupBody("confirm@example.com", PASSWORD);
		mismatch.put("passwordConfirm", "Different1!");
		signup(mismatch)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("비밀번호가 서로 달라요. 다시 확인해 주세요."));

		Map<String, Object> noTerms = signupBody("terms@example.com", PASSWORD);
		noTerms.put("agreedTerms", false);
		signup(noTerms)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("서비스 이용약관에 동의해 주세요."));
	}

	@Test
	void 비밀번호_규칙을_어기면_400과_어긴_규칙을_알려_준다() throws Exception {
		signup(signupBody("weak@example.com", "password1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("비밀번호에 특수문자(!, @, # 등)를 포함해 주세요."));
		assertThat(userRepository.existsByEmail("weak@example.com")).isFalse();
	}

	// ---------- 로그인 ----------

	@Test
	void 로그인하면_Access_Token과_HttpOnly_Refresh_쿠키를_주고_내_정보를_볼_수_있다() throws Exception {
		createUser("login@example.com");

		MvcResult result = login("LOGIN@example.com", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isString())
				.andExpect(jsonPath("$.data.user.email").value("login@example.com"))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
				.andReturn();

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(result))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("login@example.com"))
				.andExpect(jsonPath("$.data.name").value("김주미"));
	}

	@Test
	void 비밀번호가_틀려도_계정이_없어도_같은_문구로_401() throws Exception {
		createUser("exists@example.com");

		login("exists@example.com", "Wrong000!")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해 주세요."))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		login("nobody@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해 주세요."));
	}

	@Test
	void 같은_계정으로_반복해서_틀리면_맞는_비밀번호도_잠시_막는다() throws Exception {
		createUser("brute@example.com");
		for (int i = 0; i < 5; i++) {
			login("brute@example.com", "Wrong000!").andExpect(status().isUnauthorized());
		}

		login("brute@example.com", PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("로그인 시도가 너무 많아요. 15분 뒤 다시 시도해 주세요."));
	}

	@Test
	void X_Requested_With_헤더가_없는_쿠키_요청은_403() throws Exception {
		createUser("csrf@example.com");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("email", "csrf@example.com", "password", PASSWORD))))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isForbidden());
	}

	// ---------- 보호된 API ----------

	// ---------- 토큰 만료·갱신·로그아웃 ----------

	@Test
	void Access_Token이_만료되면_401이고_Refresh로_다시_발급받을_수_있다() throws Exception {
		createUser("expire@example.com");
		MvcResult loginResult = login("expire@example.com", PASSWORD).andReturn();
		String expiredAccess = accessToken(loginResult);

		clock.advance(Duration.ofMinutes(16));

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredAccess)))
				.andExpect(status().isUnauthorized());

		MvcResult refreshed = refresh(refreshCookie(loginResult))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isString())
				.andReturn();
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(refreshed))))
				.andExpect(status().isOk());
	}

	@Test
	void 갱신하면_Refresh_Token이_바뀌고_이전_토큰을_다시_쓰면_세션_전체가_폐기된다() throws Exception {
		createUser("rotate@example.com");
		MvcResult loginResult = login("rotate@example.com", PASSWORD).andReturn();
		String firstRefresh = refreshCookie(loginResult);

		MvcResult rotated = refresh(firstRefresh).andExpect(status().isOk()).andReturn();
		String secondRefresh = refreshCookie(rotated);
		String secondAccess = accessToken(rotated);
		assertThat(secondRefresh).isNotEqualTo(firstRefresh);

		// 동시 갱신 유예 시간(10초)이 지난 뒤 이미 쓴 토큰 재사용 → 탈취로 보고 세션 폐기, 쿠키 삭제
		clock.advance(Duration.ofSeconds(11));
		refresh(firstRefresh)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(UNAUTHORIZED))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		// 같은 세션의 새 토큰들도 함께 막힌다
		refresh(secondRefresh).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(secondAccess)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 방금_회전된_토큰으로_다시_갱신하면_유예_시간_안에서는_세션을_끊지_않고_다시_발급한다() throws Exception {
		createUser("grace@example.com");
		String firstRefresh = refreshCookie(login("grace@example.com", PASSWORD).andReturn());

		// 탭 두 개가 같은 쿠키로 갱신: 먼저 온 쪽이 회전한 뒤 늦게 온 쪽이 같은(이미 폐기된) 토큰을 보낸다
		MvcResult first = refresh(firstRefresh).andExpect(status().isOk()).andReturn();
		clock.advance(Duration.ofSeconds(9));
		MvcResult second = refresh(firstRefresh).andExpect(status().isOk()).andReturn();

		// 두 응답의 새 토큰과 Access Token이 모두 쓸 수 있다 (쿠키에는 늦게 도착한 쪽이 남는다)
		String fromFirst = refreshCookie(first);
		String fromSecond = refreshCookie(second);
		assertThat(fromSecond).isNotEqualTo(fromFirst).isNotEqualTo(firstRefresh);
		for (MvcResult result : new MvcResult[] {first, second}) {
			mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(result))))
					.andExpect(status().isOk());
		}
		clock.advance(Duration.ofMinutes(1));
		refresh(fromFirst).andExpect(status().isOk());
		refresh(fromSecond).andExpect(status().isOk());
	}

	@Test
	void 같은_쿠키로_동시에_갱신해도_둘_다_성공하고_세션이_유지된다() throws Exception {
		createUser("race@example.com");
		String refreshToken = refreshCookie(login("race@example.com", PASSWORD).andReturn());

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<MvcResult>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return refresh(refreshToken).andReturn();
				}));
			}
			start.countDown();
			List<String> nextTokens = new ArrayList<>();
			for (Future<MvcResult> future : futures) {
				MvcResult result = future.get(30, TimeUnit.SECONDS);
				assertThat(result.getResponse().getStatus()).isEqualTo(200);
				nextTokens.add(refreshCookie(result));
			}
			// 동시 갱신 뒤에도 세션이 살아 있어 다음 갱신이 된다
			clock.advance(Duration.ofMinutes(1));
			for (String next : nextTokens) {
				refresh(next).andExpect(status().isOk());
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 로그아웃한_세션의_토큰은_유예_시간_안이어도_다시_발급하지_않는다() throws Exception {
		createUser("grace-logout@example.com");
		String firstRefresh = refreshCookie(login("grace-logout@example.com", PASSWORD).andReturn());
		String secondRefresh = refreshCookie(refresh(firstRefresh).andExpect(status().isOk()).andReturn());

		// 회전 직후 다른 탭에서 로그아웃 → 회전으로 폐기된 토큰도, 로그아웃으로 폐기된 토큰도 받지 않는다
		logout(secondRefresh).andExpect(status().isOk());
		refresh(firstRefresh).andExpect(status().isUnauthorized());
		refresh(secondRefresh).andExpect(status().isUnauthorized());
	}

	@Test
	void Refresh_Token이_만료되면_다시_로그인해야_한다() throws Exception {
		createUser("refresh-expire@example.com");
		MvcResult loginResult = login("refresh-expire@example.com", PASSWORD).andReturn();

		clock.advance(Duration.ofDays(15));

		refresh(refreshCookie(loginResult)).andExpect(status().isUnauthorized());
	}

	@Test
	void 로그아웃하면_Refresh와_Access_Token을_모두_쓸_수_없다() throws Exception {
		createUser("logout@example.com");
		MvcResult loginResult = login("logout@example.com", PASSWORD).andReturn();
		String access = accessToken(loginResult);
		String refreshToken = refreshCookie(loginResult);

		logout(refreshToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("로그아웃했어요."))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		refresh(refreshToken).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void Refresh_쿠키가_없으면_401() throws Exception {
		mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.data.accessToken").doesNotExist());
	}

	@Test
	void Refresh_Token_원문은_DB에_저장하지_않는다() throws Exception {
		createUser("hash@example.com");
		String raw = refreshCookie(login("hash@example.com", PASSWORD).andReturn());

		assertThat(refreshTokenRepository.findAll())
				.singleElement()
				.satisfies(token -> assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(raw));
	}

	// ---------- 도우미 ----------

	private Map<String, Object> signupBody(String email, String password) {
		Map<String, Object> body = new HashMap<>();
		body.put("email", email);
		body.put("name", "김주미");
		body.put("password", password);
		body.put("passwordConfirm", password);
		body.put("agreedTerms", true);
		body.put("agreedPrivacy", true);
		body.put("agreedAiAnalysis", false);
		return body;
	}

	private ResultActions signup(Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(body)));
	}

	private void createUser(String email) throws Exception {
		signup(signupBody(email, PASSWORD)).andExpect(status().isCreated());
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.header("X-Requested-With", "XMLHttpRequest")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("email", email, "password", password))));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
				.header("X-Requested-With", "XMLHttpRequest")
				.cookie(new Cookie(COOKIE, refreshToken)));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/logout")
				.header("X-Requested-With", "XMLHttpRequest")
				.cookie(new Cookie(COOKIE, refreshToken)));
	}

	private String json(Object body) throws Exception {
		return objectMapper.writeValueAsString(body);
	}

	private static String accessToken(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	// Set-Cookie: refresh_token=<값>; Path=...; 에서 값만 꺼낸다
	private static String refreshCookie(MvcResult result) {
		String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(header).startsWith(COOKIE + "=");
		String value = header.substring(COOKIE.length() + 1, header.indexOf(';'));
		assertThat(value).isNotEmpty();
		return value;
	}

}
