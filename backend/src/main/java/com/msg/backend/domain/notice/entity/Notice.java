package com.msg.backend.domain.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

	public static final int TITLE_MAX_LENGTH = 100;
	public static final int CONTENT_MAX_LENGTH = 5000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	// 옛 공지는 본문이 없을 수 있다 (V3 이전에 만든 행)
	@Column(length = CONTENT_MAX_LENGTH)
	private String content;

	@Column(nullable = false)
	private LocalDate createdAt;

	// 한 번도 수정하지 않았으면 비어 있다
	private LocalDateTime updatedAt;

	// 작성한 관리자. 그 관리자가 탈퇴하면 비어 있다
	private Long authorId;

	public Notice(String title, LocalDate createdAt) {
		this.title = title;
		this.createdAt = createdAt;
	}

	public static Notice write(String title, String content, LocalDate createdAt, Long authorId) {
		Notice notice = new Notice(title, createdAt);
		notice.content = content;
		notice.authorId = authorId;
		return notice;
	}

	public void edit(String title, String content, LocalDateTime now) {
		this.title = title;
		this.content = content;
		this.updatedAt = now;
	}
}
