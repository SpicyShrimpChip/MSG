package com.msg.backend.global.llm;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiDailyUsageRepository extends JpaRepository<AiDailyUsage, LocalDate> {

	// 상한 아래일 때만 1 올린다. 한 문장이라 동시에 불러도 상한을 넘지 않는다 (올렸으면 1, 상한이면 0)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update AiDailyUsage u set u.callCount = u.callCount + 1"
			+ " where u.usageDate = :usageDate and u.callCount < :limit")
	int incrementIfBelow(LocalDate usageDate, int limit);
}
