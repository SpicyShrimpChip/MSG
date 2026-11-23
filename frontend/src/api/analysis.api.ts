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
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { AI_REQUEST_TIMEOUT_MS, ApiError, request } from './request';

export const ANALYSIS_QUERY_KEY = ['analysis'] as const;

// 과목의 최근 분석 쿼리 키 (피드백을 바꾸면 이 키를 다시 불러 순서를 새로 받는다)
export const analysisLatestKey = (courseId: number) =>
  [...ANALYSIS_QUERY_KEY, 'latest', courseId] as const;

// 모델별 설정 여부
export function getProviders(): Promise<ProviderStatus[]> {
  if (isMockData()) return mockGetProviders();
  return request<ProviderStatus[]>({
    method: 'GET',
    url: '/analysis/providers',
  });
}

// 과목의 가장 최근 분석. 아직 분석하지 않았으면(404) null
export async function getLatestAnalysis(
  courseId: number,
): Promise<AnalysisRun | null> {
  if (isMockData()) return mockGetLatestAnalysis(courseId);
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
  if (isMockData()) return mockSaveConceptFeedback(courseId, input);
  return request<ConceptFeedback>({
    method: 'PUT',
    url: `/analysis/courses/${courseId}/feedback`,
    data: input,
  });
}

// 내 생각 취소
export function deleteConceptFeedback(id: number): Promise<void> {
  if (isMockData()) return mockDeleteConceptFeedback(id);
  return request<void>({ method: 'DELETE', url: `/analysis/feedback/${id}` });
}

// 분석 시작. 모델을 기다리므로 길면 1분 40초쯤 걸린다 (서버 예산 100초)
export function startAnalysis(courseId: number): Promise<AnalysisRun> {
  if (isMockData()) return mockStartAnalysis(courseId);
  return request<AnalysisRun>({
    method: 'POST',
    url: `/analysis/courses/${courseId}/runs`,
    timeout: AI_REQUEST_TIMEOUT_MS,
  });
}
