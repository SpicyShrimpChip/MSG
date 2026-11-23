package com.msg.backend.domain.notice.application;

import com.msg.backend.domain.notice.dao.NoticeRepository;
import com.msg.backend.domain.notice.dto.NoticeDetailResponse;
import com.msg.backend.domain.notice.dto.NoticeRequest;
import com.msg.backend.domain.notice.dto.NoticeResponse;
import com.msg.backend.domain.notice.entity.Notice;
import com.msg.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

	static final String NOT_FOUND_MESSAGE = "공지사항을 찾을 수 없어요.";

	private final NoticeRepository noticeRepository;
	private final Clock clock;

	public List<NoticeResponse> getNotices() {
		return noticeRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
				.map(NoticeResponse::from)
				.toList();
	}

	public NoticeDetailResponse getNotice(Long id) {
		return NoticeDetailResponse.from(find(id));
	}

	@Transactional
	public NoticeDetailResponse write(Long authorId, NoticeRequest request) {
		Notice notice = Notice.write(
				request.title().strip(), request.content().strip(), LocalDate.now(clock), authorId);
		return NoticeDetailResponse.from(noticeRepository.save(notice));
	}

	@Transactional
	public NoticeDetailResponse edit(Long id, NoticeRequest request) {
		Notice notice = find(id);
		notice.edit(request.title().strip(), request.content().strip(), LocalDateTime.now(clock));
		return NoticeDetailResponse.from(notice);
	}

	@Transactional
	public void delete(Long id) {
		noticeRepository.delete(find(id));
	}

	private Notice find(Long id) {
		return noticeRepository.findById(id)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}
}
