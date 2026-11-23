package com.msg.backend.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.FakeKakaoServer;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.KakaoLoginFlow;
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestApi;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 카카오로만 가입한 사용자(비밀번호 없음)는 "탈퇴"를 입력해 탈퇴하고, 탈퇴하면 카카오 연결도 끊는다
@IntegrationTest
class SocialWithdrawalApiTest {

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
	void 카카오_사용자는_탈퇴를_입력해_탈퇴하고_연결이_끊긴다() throws Exception {
		String token = kakaoSignup();

		withdraw(token, Map.of("confirmText", " 탈퇴 ")).andExpect(status().isOk());

		assertThat(userRepository.count()).isZero();
		assertThat(socialAccountRepository.count()).isZero();
		assertThat(kakao.calls(FakeKakaoServer.UNLINK)).isEqualTo(1);
		assertThat(kakao.lastAuthorization(FakeKakaoServer.UNLINK)).isEqualTo("KakaoAK test-admin");
		assertThat(kakao.lastForm(FakeKakaoServer.UNLINK)).containsEntry("target_id", "4242");
	}

	@Test
	void 탈퇴_문구가_틀리면_400이고_연결은_그대로() throws Exception {
		String token = kakaoSignup();

		withdraw(token, Map.of("confirmText", "탈퇴할래요")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("확인 문구로 \"탈퇴\"를 입력해 주세요."));
		withdraw(token, Map.of()).andExpect(status().isBadRequest());
		withdraw(token, Map.of("password", "아무거나")).andExpect(status().isBadRequest());

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(kakao.calls(FakeKakaoServer.UNLINK)).isZero();
	}

	@Test
	void 연결_끊기가_실패해도_탈퇴는_된다() throws Exception {
		String token = kakaoSignup();
		kakao.reply(FakeKakaoServer.UNLINK, 500, "{}");

		withdraw(token, Map.of("confirmText", "탈퇴")).andExpect(status().isOk());

		assertThat(userRepository.count()).isZero();
		assertThat(kakao.calls(FakeKakaoServer.UNLINK)).isEqualTo(1);
	}

	@Test
	void 비밀번호와_카카오가_둘_다_있으면_비밀번호로_탈퇴하고_연결도_끊긴다() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, "kakao@example.com", Map.of());
		// 같은 확인된 이메일로 카카오 로그인 → 연결
		KakaoLoginFlow.login(mockMvc, null);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		String token = TestApi.login(mockMvc, objectMapper, "kakao@example.com");

		withdraw(token, Map.of("confirmText", "탈퇴")).andExpect(status().isBadRequest());
		withdraw(token, Map.of("password", TestApi.PASSWORD)).andExpect(status().isOk());

		assertThat(userRepository.count()).isZero();
		assertThat(kakao.calls(FakeKakaoServer.UNLINK)).isEqualTo(1);
	}

	@Test
	void 카카오가_없는_사용자가_탈퇴하면_연결_끊기를_부르지_않는다() throws Exception {
		String token = TestApi.signupAndLogin(mockMvc, objectMapper, "plain@example.com", Map.of());

		withdraw(token, Map.of("password", TestApi.PASSWORD)).andExpect(status().isOk());

		assertThat(kakao.calls(FakeKakaoServer.UNLINK)).isZero();
	}

	@Test
	void 내_정보에_hasPassword가_실린다() throws Exception {
		String passwordUser = TestApi.signupAndLogin(mockMvc, objectMapper, "plain@example.com", Map.of());
		String kakaoUser = kakaoSignup();

		me(passwordUser).andExpect(jsonPath("$.data.hasPassword").value(true));
		me(kakaoUser).andExpect(jsonPath("$.data.hasPassword").value(false));
	}

	// 카카오로 처음 와서 가입을 마치고 Access Token을 돌려준다 (회원번호 4242, kakao@example.com)
	private String kakaoSignup() throws Exception {
		var ticket = KakaoLoginFlow.signupCookie(KakaoLoginFlow.login(mockMvc, null));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", "카카오 사용자");
		body.put("agreedTerms", true);
		body.put("agreedPrivacy", true);
		String response = mockMvc.perform(post("/api/auth/social/signup").cookie(ticket)
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}

	private ResultActions withdraw(String token, Map<String, Object> body) throws Exception {
		return mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, TestApi.bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private ResultActions me(String token) throws Exception {
		return mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, TestApi.bearer(token)));
	}
}
