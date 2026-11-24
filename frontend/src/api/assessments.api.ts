import { queryOptions } from '@tanstack/react-query';
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
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { AI_REQUEST_TIMEOUT_MS, request } from './request';

export const ASSESSMENTS_QUERY_KEY = ['assessments'] as const;

// 과목(대시보드)의 사전·사후평가 비교. 요약 줄과 아래 비교 영역이 같은 캐시를 쓴다
export const assessmentComparisonQuery = (courseId: number) =>
  queryOptions({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'comparison', courseId],
    queryFn: () => getAssessmentComparison({ courseId }),
  });

// 자료 하나의 사전·사후평가 비교 (평가 화면)
export const materialAssessmentComparisonQuery = (materialId: number) =>
  queryOptions({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'comparison', 'material', materialId],
    queryFn: () => getAssessmentComparison({ materialId }),
  });

// 문제 만들기·채점에 쓸 AI 상태
export const quizAiStatusQuery = () =>
  queryOptions({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'ai-status'],
    queryFn: getQuizAiStatus,
  });

// 사전·사후평가 만들기 (백엔드 11/3, Swagger `사전·사후평가`). 퀴즈 만들기와 같은 시간 예산(100초)
export function createAssessment(input: AssessmentCreateInput): Promise<Quiz> {
  if (isMockData()) return mockCreateAssessment(input);
  return request<Quiz>({
    method: 'POST',
    url: '/assessments',
    data: input,
    timeout: AI_REQUEST_TIMEOUT_MS,
  });
}

// 과목(대시보드) 또는 자료별 최신 사전평가 vs 그 사후평가
export function getAssessmentComparison(target: {
  courseId?: number;
  materialId?: number;
}): Promise<AssessmentComparison> {
  if (isMockData()) return mockGetAssessmentComparison(target);
  return request<AssessmentComparison>({
    method: 'GET',
    url: '/assessments/comparison',
    params: target,
  });
}

export function getQuizAiStatus(): Promise<QuizAiStatus> {
  if (isMockData()) return mockGetQuizAiStatus();
  return request<QuizAiStatus>({
    method: 'GET',
    url: '/assessments/ai-status',
  });
}
