package com.msg.backend.global.llm;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserAiCallDailyRepository extends JpaRepository<UserAiCallDaily, UserAiCallDaily.Key> {

	// 상한 아래일 때만 1 올린다. 한 문장이라 동시에 불러도 상한을 넘지 않는다 (올렸으면 1, 상한이면 0)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update UserAiCallDaily u set u.callCount = u.callCount + 1"
			+ " where u.userId = :userId and u.usageDate = :usageDate and u.callCount < :limit")
	int incrementIfBelow(Long userId, LocalDate usageDate, int limit);

	// 0보다 클 때만 1 줄인다 (받아 갔지만 부르지 않은 한 번을 돌려줄 때, 날짜가 바뀌어 줄이 0이면 그대로)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update UserAiCallDaily u set u.callCount = u.callCount - 1"
			+ " where u.userId = :userId and u.usageDate = :usageDate and u.callCount > 0")
	int decrementIfPositive(Long userId, LocalDate usageDate);

	@Query("select u.callCount from UserAiCallDaily u where u.userId = :userId and u.usageDate = :usageDate")
	Optional<Integer> findCallCount(Long userId, LocalDate usageDate);

	@Modifying
	@Query("delete from UserAiCallDaily u where u.usageDate < :before")
	int deleteBefore(LocalDate before);

	@Modifying
	@Query("delete from UserAiCallDaily u where u.userId = :userId")
	int deleteAllOfUser(Long userId);
}
