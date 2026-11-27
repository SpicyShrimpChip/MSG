package com.msg.backend.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 남은 파일 정리 작업을 다시 시도하는 순서. 계속 실패하는 작업이 쌓여도 뒤 작업이 막히지 않아야 한다.
 */
@IntegrationTest
class FileCleanupRetryTest {

	@Autowired
	private FileCleanupService fileCleanupService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
		jdbcTemplate.update("delete from file_deletion_task");
	}

	@AfterEach
	void tearDown() {
		clock.reset();
		jdbcTemplate.update("delete from file_deletion_task");
	}

	@Test
	void 계속_실패하는_작업이_한_번에_가져오는_양만큼_쌓여도_뒤_작업을_지운다() {
		LocalDateTime now = LocalDateTime.now(clock);
		// 저장소 key 형식이 아니라 LocalFileStorage.delete가 늘 실패하는 작업
		for (int i = 0; i < FileCleanupService.RETRY_BATCH_SIZE; i++) {
			insertTask("bad key " + i, now.minusHours(2));
		}
		insertTask("materials/1/gone.pdf", now.minusHours(1));

		assertThat(fileCleanupService.retryPending()).isZero();
		clock.advance(Duration.ofMinutes(10));

		assertThat(fileCleanupService.retryPending()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from file_deletion_task where storage_key = 'materials/1/gone.pdf'", Integer.class))
				.isZero();
		// 실패한 작업은 지워지지 않고 시도 횟수만 쌓인다
		assertThat(jdbcTemplate.queryForObject("select count(*) from file_deletion_task", Integer.class))
				.isEqualTo(FileCleanupService.RETRY_BATCH_SIZE);
	}

	private void insertTask(String key, LocalDateTime createdAt) {
		jdbcTemplate.update("insert into file_deletion_task (storage_key, attempt_count, created_at) values (?, 0, ?)",
				key, Timestamp.valueOf(createdAt));
	}
}
