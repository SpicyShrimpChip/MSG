package com.ailms.backend.domain.user.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.user.entity.User;
import com.ailms.backend.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// DB 수준 확인: 마이그레이션 적용, UNIQUE 제약, 한글·이모지(UTF-8) 저장. pgTest에서는 실제 PostgreSQL로 확인된다
@IntegrationTest
class UserPersistenceTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 같은_이메일은_DB_UNIQUE_제약이_막는다() {
		userRepository.saveAndFlush(newUser("unique@example.com", "첫째"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("unique@example.com", "둘째")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 한글과_이모지_이름을_그대로_저장하고_읽는다() {
		String name = "김주미 학습자😀";
		Long id = userRepository.saveAndFlush(newUser("utf8@example.com", name)).getId();

		// JPA 캐시가 아니라 DB에 저장된 값을 직접 읽는다
		String stored = jdbcTemplate.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id);
		assertThat(stored).isEqualTo(name);
	}

	private static User newUser(String email, String name) {
		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
		return User.signUp(email, "$2a$10$abcdefghijklmnopqrstuuJ0wZs3wq1yCq3sJxZ6v1oQ3n3s5m7e", name, false, now);
	}
}
