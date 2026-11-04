import type { SearchMaterialHit, SearchResult } from '@/types';
import { ApiError } from '@/api/request';
import { SEARCH_MAX_LENGTH, matchesQuery } from '@/utils/search';
import { mockGetCourses } from './courses';
import { mockDelay } from './delay';
import { mockMaterialsSnapshot } from './materials';
import { mockGetNotices } from './notices';

// 목업 통합 검색: 서버(SearchService)와 같은 규칙으로 목업 과목·자료·개념·공지에서 찾는다
export async function mockSearch(rawQuery: string): Promise<SearchResult> {
  const query = rawQuery.trim();
  if (!query) throw new ApiError('검색어를 입력해 주세요.', 400);
  if (query.length > SEARCH_MAX_LENGTH) {
    throw new ApiError(
      `검색어는 ${SEARCH_MAX_LENGTH}자까지 입력할 수 있어요.`,
      400,
    );
  }
  const [courses, notices] = await Promise.all([
    mockGetCourses(),
    mockGetNotices(),
  ]);
  const materials = [...mockMaterialsSnapshot()].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt),
  );
  const courseName = new Map(courses.map((course) => [course.id, course.name]));
  const countOf = (courseId: number) =>
    materials.filter((material) => material.courseId === courseId).length;

  const courseHits = courses
    .map((course) => ({
      id: course.id,
      name: course.name,
      semester: course.semester,
      nameMatched: matchesQuery(course.name, query),
      matchedKeywords: course.keywords.filter((keyword) =>
        matchesQuery(keyword, query),
      ),
      materialCount: countOf(course.id),
    }))
    .filter((hit) => hit.nameMatched || hit.matchedKeywords.length > 0);
  const nameMatched = new Set(
    courseHits.filter((hit) => hit.nameMatched).map((hit) => hit.id),
  );

  const byTitle: SearchMaterialHit[] = [];
  const byCourse: SearchMaterialHit[] = [];
  for (const material of materials) {
    const hit = {
      id: material.id,
      title: material.title,
      fileName: material.fileName,
      courseId: material.courseId,
      courseName:
        material.courseId === null
          ? null
          : (courseName.get(material.courseId) ?? null),
    };
    if (
      matchesQuery(material.title, query) ||
      matchesQuery(material.fileName, query)
    ) {
      byTitle.push({ ...hit, matchedBy: 'title' });
    } else if (
      material.courseId !== null &&
      nameMatched.has(material.courseId)
    ) {
      byCourse.push({ ...hit, matchedBy: 'course' });
    }
  }

  const concepts = materials.flatMap((material) =>
    material.concepts
      .filter((concept) => matchesQuery(concept.name, query))
      .map((concept) => ({
        materialId: material.id,
        materialTitle: material.title,
        conceptId: concept.conceptId,
        name: concept.name,
        aiDescription: concept.aiDescription,
      })),
  );

  return mockDelay({
    query,
    courses: courseHits,
    materials: [...byTitle, ...byCourse],
    concepts,
    notices: notices
      .filter((notice) => matchesQuery(notice.title, query))
      .slice(0, 5),
  });
}
