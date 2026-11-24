import { useSuspenseQuery } from '@tanstack/react-query';
import { conceptStudiesQuery } from '@/api';

// 자료 하나의 개념별 학습 상태 (개념 탭과 확인 퀴즈 결과에서 같이 쓴다)
export function useConceptStudies(materialId: number) {
  return useSuspenseQuery(conceptStudiesQuery(materialId));
}
