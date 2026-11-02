import type { Quiz, QuizAnswer, QuizResult, QuizSummary } from '@/types';
import {
  mockGetLatestQuizResult,
  mockGetQuiz,
  mockGetQuizzes,
  mockSubmitQuiz,
} from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

// React Query에서 퀴즈를 담는 키. 한 퀴즈는 [...QUIZZES_QUERY_KEY, id], 최근 결과는 [...QUIZZES_QUERY_KEY, id, 'result']
export const QUIZZES_QUERY_KEY = ['quizzes'] as const;

// 백엔드 구현됨 (11/2, Swagger `퀴즈`, docs/frontend-requests.md "퀴즈"). 퀴즈 만들기 API는 아직 없다
export function getQuizzes(): Promise<QuizSummary[]> {
  if (USE_MOCK) return mockGetQuizzes();
  return request<QuizSummary[]>({ method: 'GET', url: '/quizzes' });
}

export function getQuiz(id: number): Promise<Quiz> {
  if (USE_MOCK) return mockGetQuiz(id);
  return request<Quiz>({ method: 'GET', url: `/quizzes/${id}` });
}

export function submitQuiz(
  id: number,
  answers: QuizAnswer[],
): Promise<QuizResult> {
  if (USE_MOCK) return mockSubmitQuiz(id, answers);
  return request<QuizResult>({
    method: 'POST',
    url: `/quizzes/${id}/submissions`,
    data: { answers },
  });
}

// 아직 안 풀었으면 null
export function getLatestQuizResult(id: number): Promise<QuizResult | null> {
  if (USE_MOCK) return mockGetLatestQuizResult(id);
  return request<QuizResult | null>({
    method: 'GET',
    url: `/quizzes/${id}/submissions/latest`,
  });
}
