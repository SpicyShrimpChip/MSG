package com.msg.backend.domain.notice.dao;

import com.msg.backend.domain.notice.entity.Notice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

	// 최신 공지가 위로: 작성일 내림차순, 같은 날이면 id 큰 순
	List<Notice> findAllByOrderByCreatedAtDescIdDesc();
}
