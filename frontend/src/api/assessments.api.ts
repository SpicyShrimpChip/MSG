import type {
  AssessmentComparison,
  AssessmentCreateInput,
  Quiz,
  QuizAiStatus,
} from '@/types';
import {
  mockCreateAssessment,
  mockGetAssessmentComparison,
  mockGetQuizAiStatus,
} from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

export const ASSESSMENTS_QUERY_KEY = ['assessments'] as const;

// 사전·사후평가 만들기 (백엔드 11/3, Swagger `사전·사후평가`). 퀴즈 만들기와 같은 시간 예산(150초)
export function createAssessment(input: AssessmentCreateInput): Promise<Quiz> {
  if (USE_MOCK) return mockCreateAssessment(input);
  return request<Quiz>({
    method: 'POST',
    url: '/assessments',
    data: input,
    timeout: 180_000,
  });
}

// 과목(대시보드) 또는 자료별 최신 사전평가 vs 그 사후평가
export function getAssessmentComparison(target: {
  courseId?: number;
  materialId?: number;
}): Promise<AssessmentComparison> {
  if (USE_MOCK) return mockGetAssessmentComparison(target);
  return request<AssessmentComparison>({
    method: 'GET',
    url: '/assessments/comparison',
    params: target,
  });
}

export function getQuizAiStatus(): Promise<QuizAiStatus> {
  if (USE_MOCK) return mockGetQuizAiStatus();
  return request<QuizAiStatus>({
    method: 'GET',
    url: '/assessments/ai-status',
  });
}
