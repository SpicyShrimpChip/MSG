import { useSuspenseQuery } from '@tanstack/react-query';
import { curriculumQuery } from '@/api';

// 과목 하나의 맞춤 커리큘럼 (커리큘럼 화면과 대시보드 학습 진도에서 같이 쓴다)
export function useCurriculum(courseId: number) {
  return useSuspenseQuery(curriculumQuery(courseId));
}
