package com.msg.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestDatabaseShardTest {

	@Test
	void 테스트_DB_이름_가운데에_번호를_넣는다() {
		assertThat(TestDatabaseShard.shardUrl("jdbc:postgresql://localhost:5432/msg_test", 0, 4))
				.isEqualTo("jdbc:postgresql://localhost:5432/msg_1_test");
		assertThat(TestDatabaseShard.shardUrl("jdbc:postgresql://127.0.0.1:5432/msg_test?sslmode=require", 7, 4))
				.isEqualTo("jdbc:postgresql://127.0.0.1:5432/msg_4_test?sslmode=require");
	}

	@Test
	void 이어진_작업자_번호는_서로_다른_DB를_고른다() {
		Set<String> urls = new HashSet<>();
		for (long worker = 13; worker < 17; worker++) {
			urls.add(TestDatabaseShard.shardUrl("jdbc:postgresql://localhost:5432/msg_test", worker, 4));
		}
		assertThat(urls).hasSize(4);
	}

	@Test
	void 바꾼_이름도_안전장치를_통과한다() {
		String url = TestDatabaseShard.shardUrl("jdbc:postgresql://localhost:5432/msg_test", 2, 4);
		TestDatabaseGuard.check(url);
	}

	@Test
	void H2나_test가_아닌_DB는_바꾸지_않는다() {
		assertThat(TestDatabaseShard.shardUrl("jdbc:h2:mem:lms;MODE=PostgreSQL", 1, 4))
				.isEqualTo("jdbc:h2:mem:lms;MODE=PostgreSQL");
		assertThat(TestDatabaseShard.shardUrl("jdbc:postgresql://localhost:5432/msg", 1, 4))
				.isEqualTo("jdbc:postgresql://localhost:5432/msg");
	}
}
