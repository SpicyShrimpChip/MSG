package com.ailms.backend.domain.course.application;

import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.dto.CourseRequest;
import com.ailms.backend.domain.course.dto.CourseResponse;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.material.application.MaterialService;
import com.ailms.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 과목. 사용자 id는 항상 토큰의 값만 받고, 남의 과목은 없는 과목과 같게 404로 응답한다.
 * 입력 규칙은 프론트(CourseForm, utils/keywords.ts)와 같다: 이름 40자, 키워드 20개·각 20자,
 * 앞뒤 공백 제거·안쪽 공백 한 칸, 대소문자만 다른 키워드는 하나로 친다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

	static final String NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String DUPLICATE_MESSAGE = "이 학기에 같은 이름의 과목이 이미 있어요.";

	private final CourseRepository courseRepository;
	private final MaterialService materialService;
	private final Clock clock;

	public List<CourseResponse> getCourses(Long userId) {
		return courseRepository.findAllByUserIdOrderByIdAsc(userId).stream()
				.map(CourseResponse::from)
				.toList();
	}

	@Transactional
	public CourseResponse create(Long userId, CourseRequest request) {
		String name = name(request.name());
		List<String> keywords = keywords(request.keywords());
		if (courseRepository.existsByUserIdAndSemesterAndNameKey(userId, request.semester(), Course.nameKey(name))) {
			throw new BusinessException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
		}
		Course course = Course.create(userId, name, request.semester(), keywords, LocalDateTime.now(clock));
		saveAndFlush(course);
		return CourseResponse.from(course);
	}

	@Transactional
	public CourseResponse update(Long userId, Long id, CourseRequest request) {
		Course course = find(userId, id);
		String name = name(request.name());
		List<String> keywords = keywords(request.keywords());
		if (courseRepository.existsByUserIdAndSemesterAndNameKeyAndIdNot(
				userId, request.semester(), Course.nameKey(name), id)) {
			throw new BusinessException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
		}
		course.edit(name, request.semester(), keywords, LocalDateTime.now(clock));
		saveAndFlush(course);
		return CourseResponse.from(course);
	}

	/**
	 * 과목과 그 과목의 학습자료(DB 행과 원본 파일)를 함께 지운다. 과목에 연결하지 않은 자료와 다른 과목의 자료는 그대로다.
	 * 한 트랜잭션이라 중간에 실패하면 아무것도 지워지지 않고, 파일은 커밋된 뒤에 지운다 (MaterialService 참고).
	 */
	@Transactional
	public void delete(Long userId, Long id) {
		Course course = find(userId, id);
		materialService.deleteAllInCourse(userId, course.getId());
		courseRepository.delete(course);
	}

	private Course find(Long userId, Long id) {
		return courseRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}

	// 위의 중복 검사를 거의 동시에 통과한 두 요청은 DB의 UNIQUE 제약이 막는다
	private void saveAndFlush(Course course) {
		try {
			courseRepository.saveAndFlush(course);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
		}
	}

	private static String name(String raw) {
		String name = normalize(raw);
		if (name.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "과목 이름을 입력해 주세요.");
		}
		if (name.length() > Course.NAME_MAX_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "과목 이름은 40자까지 넣을 수 있어요.");
		}
		return name;
	}

	private static List<String> keywords(List<String> raw) {
		List<String> keywords = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String item : raw) {
			String keyword = item == null ? "" : normalize(item);
			if (keyword.isEmpty()) {
				continue;
			}
			if (keyword.length() > Course.KEYWORD_MAX_LENGTH) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, "키워드는 20자까지 넣을 수 있어요.");
			}
			if (seen.add(Course.nameKey(keyword))) {
				keywords.add(keyword);
			}
		}
		return keywords;
	}

	// 앞뒤 공백을 지우고 안쪽 공백은 한 칸으로 줄인다 (frontend/src/utils/keywords.ts normalizeKeyword)
	private static String normalize(String raw) {
		return raw.strip().replaceAll("\\s+", " ");
	}
}
