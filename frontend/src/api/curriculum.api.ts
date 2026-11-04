import type { Curriculum } from '@/types';
import { mockGetCurriculum } from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

export const CURRICULUM_QUERY_KEY = ['curriculum'] as const;

// 과목의 맞춤 학습 순서 (백엔드 11/3, Swagger `맞춤 커리큘럼`). 서버 코드 규칙이고 AI를 부르지 않는다
export function getCurriculum(courseId: number): Promise<Curriculum> {
  if (USE_MOCK) return mockGetCurriculum(courseId);
  return request<Curriculum>({
    method: 'GET',
    url: `/curriculum/courses/${courseId}`,
  });
}
