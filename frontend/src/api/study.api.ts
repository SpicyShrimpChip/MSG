import type { ConceptStudy } from '@/types';
import { mockCompleteConceptStudy, mockGetConceptStudies } from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

// 자료의 개념별 학습 상태. 키는 [...STUDY_QUERY_KEY, materialId]
export const STUDY_QUERY_KEY = ['concept-study'] as const;

// 자료의 핵심 개념마다 학습 기록·확인 퀴즈 결과 (Swagger `개념 학습 기록`). 목업은 브라우저에만 저장하고 서버로 보내지 않는다
export function getConceptStudies(materialId: number): Promise<ConceptStudy[]> {
  if (USE_MOCK) return mockGetConceptStudies(materialId);
  return request<ConceptStudy[]>({
    method: 'GET',
    url: `/materials/${materialId}/concepts/progress`,
  });
}

// 학습 완료 (공부했다는 기록일 뿐, 이해 여부·숙련도는 바꾸지 않는다). 여러 번 보내도 기록은 하나다
export function completeConceptStudy(
  materialId: number,
  conceptId: string,
): Promise<ConceptStudy> {
  if (USE_MOCK) return mockCompleteConceptStudy(materialId, conceptId);
  return request<ConceptStudy>({
    method: 'POST',
    url: `/materials/${materialId}/concepts/progress`,
    data: { conceptId },
  });
}
