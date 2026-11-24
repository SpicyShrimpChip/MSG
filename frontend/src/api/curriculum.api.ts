import { queryOptions } from '@tanstack/react-query';
import type { Curriculum } from '@/types';
import { mockGetCurriculum } from '@/mocks';
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { request } from './request';

export const CURRICULUM_QUERY_KEY = ['curriculum'] as const;

// 과목 하나의 커리큘럼. 커리큘럼 화면과 대시보드 학습 진도가 같은 캐시를 쓴다
export const curriculumQuery = (courseId: number) =>
  queryOptions({
    queryKey: [...CURRICULUM_QUERY_KEY, courseId],
    queryFn: () => getCurriculum(courseId),
  });

// 과목의 맞춤 학습 순서 (백엔드 11/3, Swagger `맞춤 커리큘럼`). 서버 코드 규칙이고 AI를 부르지 않는다
export function getCurriculum(courseId: number): Promise<Curriculum> {
  if (isMockData()) return mockGetCurriculum(courseId);
  return request<Curriculum>({
    method: 'GET',
    url: `/curriculum/courses/${courseId}`,
  });
}
