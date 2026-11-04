package com.ailms.backend.domain.search.application;

import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.material.dao.MaterialConceptRepository;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialConcept;
import com.ailms.backend.domain.notice.dao.NoticeRepository;
import com.ailms.backend.domain.search.dto.SearchResponse;
import com.ailms.backend.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 검색. 로그인한 사용자 본인의 과목(이름·키워드)·자료(제목·파일 이름)·자료 핵심 개념(이름)과, 모두에게 공개된 공지사항(제목)에서 찾는다.
 * 대소문자와 띄어쓰기를 무시하고 글자가 들어 있으면 맞은 것으로 본다. 자료 본문은 검색하지 않는다(본문을 저장하지 않음).
 * 검색어와 맞는 과목에 올린 자료도 함께 보여 준다(matchedBy = course). 사용자 한 명의 데이터는 많지 않아 메모리에서 거른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

	public static final int MAX_QUERY_LENGTH = 50;
	static final int MAX_PER_GROUP = 30;
	static final int MAX_NOTICES = 5;
	static final String EMPTY_QUERY_MESSAGE = "검색어를 입력해 주세요.";
	static final String LONG_QUERY_MESSAGE = "검색어는 " + MAX_QUERY_LENGTH + "자까지 입력할 수 있어요.";

	private final CourseRepository courseRepository;
	private final MaterialRepository materialRepository;
	private final MaterialConceptRepository conceptRepository;
	private final NoticeRepository noticeRepository;

	public SearchResponse search(Long userId, String rawQuery) {
		String query = rawQuery == null ? "" : rawQuery.strip();
		String needle = squash(query);
		if (needle.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, EMPTY_QUERY_MESSAGE);
		}
		if (query.length() > MAX_QUERY_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, LONG_QUERY_MESSAGE);
		}

		List<Course> courses = courseRepository.findAllByUserIdOrderByIdAsc(userId);
		Map<Long, Course> courseById = courses.stream().collect(Collectors.toMap(Course::getId, Function.identity()));
		List<Material> materials = materialRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
		Map<Long, Integer> materialCount = new HashMap<>();
		materials.stream().filter(m -> m.getCourseId() != null)
				.forEach(m -> materialCount.merge(m.getCourseId(), 1, Integer::sum));

		List<SearchResponse.CourseHit> courseHits = new ArrayList<>();
		for (Course course : courses) {
			boolean nameMatched = squash(course.getName()).contains(needle);
			List<String> keywords = course.getKeywords().stream().filter(k -> squash(k).contains(needle)).toList();
			if ((nameMatched || !keywords.isEmpty()) && courseHits.size() < MAX_PER_GROUP) {
				courseHits.add(new SearchResponse.CourseHit(course.getId(), course.getName(), course.getSemester(),
						nameMatched, keywords, materialCount.getOrDefault(course.getId(), 0)));
			}
		}
		// 과목 이름이 맞은 과목의 자료만 함께 보여 준다 (키워드만 맞은 과목의 자료까지 넣으면 결과가 너무 넓어진다)
		Set<Long> nameMatchedCourses = courseHits.stream().filter(SearchResponse.CourseHit::nameMatched)
				.map(SearchResponse.CourseHit::id).collect(Collectors.toSet());

		List<SearchResponse.MaterialHit> materialHits = new ArrayList<>();
		List<SearchResponse.MaterialHit> byCourse = new ArrayList<>();
		for (Material material : materials) {
			Course course = material.getCourseId() == null ? null : courseById.get(material.getCourseId());
			String courseName = course == null ? null : course.getName();
			if (squash(material.getTitle()).contains(needle) || squash(material.getOriginalFileName()).contains(needle)) {
				materialHits.add(new SearchResponse.MaterialHit(material.getId(), material.getTitle(),
						material.getOriginalFileName(), material.getCourseId(), courseName, "title"));
			} else if (course != null && nameMatchedCourses.contains(course.getId())) {
				byCourse.add(new SearchResponse.MaterialHit(material.getId(), material.getTitle(),
						material.getOriginalFileName(), material.getCourseId(), courseName, "course"));
			}
		}
		materialHits.addAll(byCourse);

		Map<Long, Material> materialById = materials.stream()
				.collect(Collectors.toMap(Material::getId, Function.identity()));
		List<SearchResponse.ConceptHit> conceptHits = new ArrayList<>();
		for (MaterialConcept concept : conceptRepository.findAllByUserIdOrderByMaterialIdAscSortOrderAsc(userId)) {
			Material material = materialById.get(concept.getMaterialId());
			if (material != null && squash(concept.getName()).contains(needle) && conceptHits.size() < MAX_PER_GROUP) {
				conceptHits.add(new SearchResponse.ConceptHit(material.getId(), material.getTitle(),
						concept.getConceptKey(), concept.getName(), concept.getDescription()));
			}
		}

		List<SearchResponse.NoticeHit> noticeHits = noticeRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
				.filter(notice -> squash(notice.getTitle()).contains(needle))
				.limit(MAX_NOTICES)
				.map(notice -> new SearchResponse.NoticeHit(notice.getId(), notice.getTitle(), notice.getCreatedAt()))
				.toList();

		return new SearchResponse(query, courseHits, materialHits.stream().limit(MAX_PER_GROUP).toList(), conceptHits,
				noticeHits);
	}

	// 대소문자·띄어쓰기를 무시한 비교용 값
	static String squash(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}
}
