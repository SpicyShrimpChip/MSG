import type { Course } from '@/types';
import { keywordHaystackOf, keywordTargets, matchKeywords } from './filter';
import type { ParsedConversation } from './types';

export interface CourseMatch {
  courseId: number;
  // 이 과목 키워드 중 대화에서 찾은 것 (등록한 순서)
  matchedKeywords: string[];
}

export interface AssignedConversation {
  conversation: ParsedConversation;
  // 키워드가 걸린 과목들. 많이 걸린 과목이 앞이고, 첫 번째가 기본으로 들어갈 과목이다
  matches: CourseMatch[];
}

export interface AssignResult {
  assigned: AssignedConversation[];
  // 어느 과목 키워드에도 걸리지 않아 보내지 않는 대화 수
  unmatchedCount: number;
}

// 내 과목 전체의 키워드로 공부한 대화를 고르고, 각 대화를 어느 과목에 넣을지 정한다.
// 여러 과목에 걸리면 키워드가 더 많이 걸린 과목, 같으면 과목 목록에서 앞선 과목을 고른다 (사용자가 바꿀 수 있다).
// 대화마다 찾을 글을 한 번만 만들어 모든 과목의 키워드를 같이 확인한다
export function assignToCourses(
  conversations: ParsedConversation[],
  courses: Course[],
): AssignResult {
  const courseTargets = courses
    .map((course) => ({
      courseId: course.id,
      targets: keywordTargets(course.keywords),
    }))
    .filter(({ targets }) => targets.length > 0);
  if (courseTargets.length === 0)
    return { assigned: [], unmatchedCount: conversations.length };

  const assigned: AssignedConversation[] = [];
  for (const conversation of conversations) {
    const haystack = keywordHaystackOf(conversation);
    const matches: CourseMatch[] = [];
    for (const { courseId, targets } of courseTargets) {
      const matchedKeywords = matchKeywords(haystack, targets);
      if (matchedKeywords.length > 0)
        matches.push({ courseId, matchedKeywords });
    }
    if (matches.length === 0) continue;
    // sort는 안정 정렬이라 개수가 같으면 과목 목록 순서가 유지된다
    matches.sort((a, b) => b.matchedKeywords.length - a.matchedKeywords.length);
    assigned.push({ conversation, matches });
  }
  return { assigned, unmatchedCount: conversations.length - assigned.length };
}
