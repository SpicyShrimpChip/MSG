package com.ailms.backend.domain.user.dao;

import com.ailms.backend.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	// 동의 변경과 대화 가져오기가 동시에 오면 차례로 처리한다
	// (철회해 대화를 지우는 사이에 가져오기가 동의를 확인하고 저장해 대화가 남는 것을 막는다)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(Long id);
}
