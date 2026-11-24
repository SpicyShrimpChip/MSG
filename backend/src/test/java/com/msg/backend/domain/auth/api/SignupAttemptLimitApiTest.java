package com.msg.backend.domain.auth.api;

import static com.msg.backend.support.TestApi.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.FakeKakaoServer;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.KakaoLoginFlow;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 한 IP의 가입 시도 제한 (app.auth.signup-attempt). 이메일 가입과 카카오 가입 마무리를 함께 센다
@IntegrationTest
@TestPropertySource(properties = "app.auth.signup-attempt.max-per-ip=2")
class SignupAttemptLimitApiTest {

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

	@BeforeEach
	void setUp() {
		kakao.reset();
		socialAccountRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 같은_IP에서_상한을_넘으면_429이고_다른_IP는_가입할_수_있다() throws Exception {
		signup("a@example.com", "10.0.0.1").andExpect(status().isCreated());
		// 이미 가입한 이메일(409)도 시도로 센다 (가입 응답으로 이메일을 대량 확인하지 못하게)
		signup("a@example.com", "10.0.0.1").andExpect(status().isConflict());

		signup("b@example.com", "10.0.0.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("같은 곳에서 가입 시도가 너무 많아요. 60분 뒤 다시 시도해 주세요."));
		signup("b@example.com", "10.0.0.2").andExpect(status().isCreated());
	}

	@Test
	void 카카오_가입_마무리도_이메일_가입과_함께_센다() throws Exception {
		Cookie ticket = KakaoLoginFlow.signupCookie(KakaoLoginFlow.login(mockMvc, null));
		signup("c@example.com", "10.0.0.3").andExpect(status().isCreated());
		signup("d@example.com", "10.0.0.3").andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/social/signup").cookie(ticket).with(from("10.0.0.3"))
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("name", "새 사용자", "agreedTerms", true,
								"agreedPrivacy", true, "agreedAiAnalysis", false, "agreedAiQuiz", false))))
				.andExpect(status().isTooManyRequests());
	}

	private ResultActions signup(String email, String ip) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("email", email);
		body.put("name", "테스터");
		body.put("password", PASSWORD);
		body.put("passwordConfirm", PASSWORD);
		body.put("agreedTerms", true);
		body.put("agreedPrivacy", true);
		return mockMvc.perform(post("/api/auth/signup").with(from(ip))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private static RequestPostProcessor from(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}
}
