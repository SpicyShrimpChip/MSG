package com.msg.backend.domain.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.user.application.RejoinRestriction;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.dao.WithdrawnAccountRepository;
import com.msg.backend.support.FakeKakaoServer;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.KakaoLoginFlow;
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestKakao;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 탈퇴 뒤 30일 재가입 제한은 카카오 가입에도 똑같이 건다 (RejoinRestrictionApiTest와 같은 설정이라 같은 컨텍스트를 쓴다)
@IntegrationTest
@TestPropertySource(properties = "app.rejoin.hash-secret=test-rejoin-secret")
class KakaoRejoinApiTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private FakeKakaoServer kakao;
	@Autowired
	private RejoinRestriction rejoinRestriction;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserSocialAccountRepository socialAccountRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private WithdrawnAccountRepository withdrawnAccountRepository;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
		kakao.reset();
		socialAccountRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		withdrawnAccountRepository.deleteAllInBatch();
	}

	@AfterEach
	void tearDown() {
		clock.reset();
	}

	@Test
	void 탈퇴_30일_안의_이메일이면_rejoin_restricted() throws Exception {
		rejoinRestriction.recordWithdrawal("kakao@example.com");

		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.isEqualTo(TestKakao.FRONTEND + "/login?socialError=rejoin_restricted");

		clock.advance(Duration.ofDays(30).plusMinutes(1));
		assertThat(KakaoLoginFlow.login(mockMvc, null).getHeader(HttpHeaders.LOCATION))
				.startsWith(TestKakao.FRONTEND + "/signup/social");
	}
}
