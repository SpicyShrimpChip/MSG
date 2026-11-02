import type {
  AnalysisRun,
  ConceptFeedback,
  ConceptFeedbackInput,
  ProviderStatus,
} from '@/types';
import {
  mockDeleteConceptFeedback,
  mockGetLatestAnalysis,
  mockGetProviders,
  mockSaveConceptFeedback,
  mockStartAnalysis,
} from '@/mocks';
import { USE_MOCK } from './axios';
import { ApiError, request } from './request';

export const ANALYSIS_QUERY_KEY = ['analysis'] as const;

// 과목의 최근 분석 쿼리 키 (피드백을 바꾸면 이 키를 다시 불러 순서를 새로 받는다)
export const analysisLatestKey = (courseId: number) =>
  [...ANALYSIS_QUERY_KEY, 'latest', courseId] as const;

// 모델별 설정 여부
export function getProviders(): Promise<ProviderStatus[]> {
  if (USE_MOCK) return mockGetProviders();
  return request<ProviderStatus[]>({
    method: 'GET',
    url: '/analysis/providers',
  });
}

// 과목의 가장 최근 분석. 아직 분석하지 않았으면(404) null
export async function getLatestAnalysis(
  courseId: number,
): Promise<AnalysisRun | null> {
  if (USE_MOCK) return mockGetLatestAnalysis(courseId);
  try {
    return await request<AnalysisRun>({
      method: 'GET',
      url: `/analysis/courses/${courseId}/runs/latest`,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

// 개념에 대한 내 생각 남기기 (있으면 바꾼다). AI 판단은 바뀌지 않고 추천 순서만 바뀐다
export function saveConceptFeedback(
  courseId: number,
  input: ConceptFeedbackInput,
): Promise<ConceptFeedback> {
  if (USE_MOCK) return mockSaveConceptFeedback(courseId, input);
  return request<ConceptFeedback>({
    method: 'PUT',
    url: `/analysis/courses/${courseId}/feedback`,
    data: input,
  });
}

// 내 생각 취소
export function deleteConceptFeedback(id: number): Promise<void> {
  if (USE_MOCK) return mockDeleteConceptFeedback(id);
  return request<void>({ method: 'DELETE', url: `/analysis/feedback/${id}` });
}

// 분석 시작. 모델을 기다리므로 길면 2분쯤 걸린다
export function startAnalysis(courseId: number): Promise<AnalysisRun> {
  if (USE_MOCK) return mockStartAnalysis(courseId);
  return request<AnalysisRun>({
    method: 'POST',
    url: `/analysis/courses/${courseId}/runs`,
    timeout: 180_000,
  });
}
