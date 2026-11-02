import type { AnalysisRun, ProviderStatus } from '@/types';
import {
  mockGetLatestAnalysis,
  mockGetProviders,
  mockStartAnalysis,
} from '@/mocks';
import { USE_MOCK } from './axios';
import { ApiError, request } from './request';

export const ANALYSIS_QUERY_KEY = ['analysis'] as const;

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

// 분석 시작. 모델을 기다리므로 길면 2분쯤 걸린다
export function startAnalysis(courseId: number): Promise<AnalysisRun> {
  if (USE_MOCK) return mockStartAnalysis(courseId);
  return request<AnalysisRun>({
    method: 'POST',
    url: `/analysis/courses/${courseId}/runs`,
    timeout: 180_000,
  });
}
