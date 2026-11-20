package com.ailms.backend.global.llm;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserAiUsageRecordRepository extends JpaRepository<UserAiUsageRecord, Long> {

	long countByUserIdAndKindAndCreatedAtAfter(Long userId, UserAiUsage.Kind kind, LocalDateTime after);

	@Modifying
	@Query("delete from UserAiUsageRecord r where r.createdAt < :before")
	int deleteOlderThan(LocalDateTime before);
}
