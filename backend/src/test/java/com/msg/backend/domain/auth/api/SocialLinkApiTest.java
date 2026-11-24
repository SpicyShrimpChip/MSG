package com.msg.backend.domain.auth.api;

import static com.msg.backend.support.KakaoLoginFlow.REFRESH_COOKIE;
import static com.msg.backend.support.KakaoLoginFlow.SIGNUP_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestApi;
import com.msg.backend.support.TestKakao;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 같은 이메일의 비밀번호 계정에 카카오를 붙이려면 그 계정의 비밀번호를 한 번 확인한다 (미리 만든 계정 탈취 방지)
@IntegrationTest
class SocialLinkApiTest {

	private static final String EMAIL = "kakao@example.com";

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
	void 비밀번호가_맞으면_카카오를_연결하고_로그인한다() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, EMAIL, Map.of());
		Cookie ticket = linkTicket();

		MockHttpServletResponse response = link(ticket, TestApi.PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.user.email").value(EMAIL))
				.andExpect(jsonPath("$.data.user.hasPassword").value(true))
				.andReturn().getResponse();

		assertThat(KakaoLoginFlow.setCookie(response, REFRESH_COOKIE)).isPresent();
		assertThat(KakaoLoginFlow.setCookie(response, SIGNUP_COOKIE).orElseThrow()).contains("Max-Age=0");
		Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
		assertThat(socialAccountRepository.findAllByUserId(userId)).singleElement()
				.extracting(UserSocialAccount::getProviderUserId).isEqualTo("4242");
		// 다음부터는 카카오로 바로 로그인된다
		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.isEqualTo(TestKakao.FRONTEND + "/auth/social/done?returnTo=%2F");
	}

	@Test
	void 비밀번호가_틀리면_로그인과_같은_401이고_연결하지_않는다() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, EMAIL, Map.of());
		Cookie ticket = linkTicket();

		MockHttpServletResponse response = link(ticket, "Wrong0pass!")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해 주세요."))
				.andReturn().getResponse();

		assertThat(KakaoLoginFlow.setCookie(response, REFRESH_COOKIE)).isEmpty();
		// 다시 시도할 수 있게 표는 남긴다
		assertThat(KakaoLoginFlow.setCookie(response, SIGNUP_COOKIE)).isEmpty();
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void 비밀번호를_계속_틀리면_로그인처럼_막힌다() throws Exception {
		// 시도 제한은 메모리에 남아 다른 테스트에 번지지 않도록 이 테스트만 다른 이메일을 쓴다
		TestApi.signupAndLogin(mockMvc, objectMapper, "lockout@example.com", Map.of());
		kakao.user(4343L, "lockout@example.com", true, true, null);
		Cookie ticket = linkTicket();

		for (int i = 0; i < 5; i++) {
			link(ticket, "Wrong0pass!" + i).andExpect(status().isUnauthorized());
		}

		link(ticket, TestApi.PASSWORD).andExpect(status().isTooManyRequests());
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void 표가_없거나_만료되면_401() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, EMAIL, Map.of());
		link(null, TestApi.PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("가입 정보가 만료됐어요. 카카오 로그인을 다시 해 주세요."));

		Cookie ticket = linkTicket();
		clock.advance(Duration.ofMinutes(11));
		link(ticket, TestApi.PASSWORD).andExpect(status().isUnauthorized());
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void X_Requested_With가_없으면_403() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, EMAIL, Map.of());

		mockMvc.perform(post("/api/auth/social/link").cookie(linkTicket())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", TestApi.PASSWORD))))
				.andExpect(status().isForbidden());
		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	void 그새_다른_카카오가_연결됐으면_409() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, EMAIL, Map.of());
		Cookie ticket = linkTicket();
		User user = userRepository.findByEmail(EMAIL).orElseThrow();
		socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, SocialProvider.KAKAO, "9999",
				java.time.LocalDateTime.now(clock)));

		link(ticket, TestApi.PASSWORD).andExpect(status().isConflict());
		assertThat(socialAccountRepository.count()).isEqualTo(1);
	}

	@Test
	void 그새_계정이_사라졌으면_409() throws Exception {
		TestApi.signupAndLogin(mockMvc, objectMapper, EMAIL, Map.of());
		Cookie ticket = linkTicket();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();

		link(ticket, TestApi.PASSWORD).andExpect(status().isConflict());
	}

	// 같은 이메일의 비밀번호 계정이 있을 때 카카오 콜백이 준 표 (회원번호 4242)
	private Cookie linkTicket() throws Exception {
		MockHttpServletResponse callback = KakaoLoginFlow.login(mockMvc, null);
		assertThat(callback.getHeader(HttpHeaders.LOCATION)).startsWith(TestKakao.FRONTEND + "/auth/social/link");
		return KakaoLoginFlow.signupCookie(callback);
	}

	private ResultActions link(Cookie ticket, String password) throws Exception {
		var request = post("/api/auth/social/link")
				.header("X-Requested-With", "XMLHttpRequest")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("password", password)));
		if (ticket != null) {
			request.cookie(ticket);
		}
		return mockMvc.perform(request);
	}
}
