package com.ailms.backend.domain.course.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 등록한 과목. 이름·학기·키워드는 프론트(frontend/src/types/course.ts)와 같은 값을 쓴다.
 * 검증(길이·개수·중복)은 CourseService에서 하고, 엔티티는 이미 검증한 값만 받는다.
 */
@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

	public static final int NAME_MAX_LENGTH = 40;
	public static final int KEYWORD_MAX_COUNT = 20;
	public static final int KEYWORD_MAX_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	// 중복 검사용 소문자 이름 (uk_course_user_semester_name)
	@Column(nullable = false, length = 80)
	private String nameKey;

	@Column(nullable = false, length = 20)
	private String semester;

	@ElementCollection
	@CollectionTable(name = "course_keyword", joinColumns = @JoinColumn(name = "course_id"))
	@OrderColumn(name = "sort_order")
	@Column(name = "keyword", nullable = false, length = KEYWORD_MAX_LENGTH)
	private List<String> keywords = new ArrayList<>();

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static Course create(Long userId, String name, String semester, List<String> keywords, LocalDateTime now) {
		Course course = new Course();
		course.userId = userId;
		course.apply(name, semester, keywords);
		course.createdAt = now;
		course.updatedAt = now;
		return course;
	}

	public void edit(String name, String semester, List<String> keywords, LocalDateTime now) {
		apply(name, semester, keywords);
		this.updatedAt = now;
	}

	// 대소문자만 다른 이름을 같은 이름으로 본다 (프론트 CourseForm의 중복 검사와 같은 기준)
	public static String nameKey(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private void apply(String name, String semester, List<String> keywords) {
		this.name = name;
		this.nameKey = nameKey(name);
		this.semester = semester;
		this.keywords.clear();
		this.keywords.addAll(keywords);
	}
}
