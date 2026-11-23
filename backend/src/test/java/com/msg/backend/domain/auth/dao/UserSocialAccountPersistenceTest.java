package com.msg.backend.domain.auth.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.domain.auth.entity.UserSocialAccount;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// 소셜 계정 연결 테이블(V3)의 DB 제약 확인. pgTest에서는 실제 PostgreSQL로 확인된다
@IntegrationTest
class UserSocialAccountPersistenceTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserSocialAccountRepository socialAccountRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		socialAccountRepository.deleteAllInBatch();
		jdbcTemplate.update("DELETE FROM refresh_token");
		userRepository.deleteAllInBatch();
	}

	@Test
	void 비밀번호_없는_사용자를_저장할_수_있다() {
		Long id = userRepository.saveAndFlush(socialUser("social@example.com")).getId();

		User stored = userRepository.findById(id).orElseThrow();
		assertThat(stored.getPasswordHash()).isNull();
		assertThat(stored.hasPassword()).isFalse();
		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
		assertThat(hash).isNull();
	}

	@Test
	void 같은_카카오_회원번호는_두_번_연결할_수_없다() {
		User first = userRepository.saveAndFlush(socialUser("first@example.com"));
		User second = userRepository.saveAndFlush(socialUser("second@example.com"));
		socialAccountRepository.saveAndFlush(UserSocialAccount.link(first, SocialProvider.KAKAO, "123", now()));

		assertThatThrownBy(() -> socialAccountRepository
				.saveAndFlush(UserSocialAccount.link(second, SocialProvider.KAKAO, "123", now())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 한_사용자에_카카오는_하나만_연결된다() {
		User user = userRepository.saveAndFlush(socialUser("one@example.com"));
		socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, SocialProvider.KAKAO, "1", now()));

		assertThatThrownBy(() -> socialAccountRepository
				.saveAndFlush(UserSocialAccount.link(user, SocialProvider.KAKAO, "2", now())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 사용자를_지우면_연결도_지워진다() {
		User user = userRepository.saveAndFlush(socialUser("cascade@example.com"));
		socialAccountRepository.saveAndFlush(UserSocialAccount.link(user, SocialProvider.KAKAO, "77", now()));

		// 탈퇴와 같이 DB의 ON DELETE CASCADE로 지워지는지 본다(JPA 연관 삭제가 아니라)
		jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());

		assertThat(socialAccountRepository.findAllByUserId(user.getId())).isEmpty();
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "77")).isEmpty();
	}

	private static User socialUser(String email) {
		return User.signUpWithSocial(email, "카카오 사용자", false, now());
	}

	private static LocalDateTime now() {
		return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
	}
}
