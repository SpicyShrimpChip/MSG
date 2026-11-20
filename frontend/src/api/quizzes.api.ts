import type {
  Quiz,
  QuizAnswer,
  QuizCreateInput,
  QuizGenerationStatus,
  QuizResult,
  QuizSummary,
} from '@/types';
import {
  mockCreateQuiz,
  mockGetGenerationStatus,
  mockGetLatestQuizResult,
  mockGetQuiz,
  mockGetQuizzes,
  mockRegradeQuiz,
  mockSubmitQuiz,
} from '@/mocks';
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { request } from './request';

// React Query에서 퀴즈를 담는 키. 한 퀴즈는 [...QUIZZES_QUERY_KEY, id], 최근 결과는 [...QUIZZES_QUERY_KEY, id, 'result'],
// 과목의 개념별 결과는 [...QUIZZES_QUERY_KEY, 'concept-stats', courseId]
export const QUIZZES_QUERY_KEY = ['quizzes'] as const;

// 백엔드 구현됨 (11/2 풀기·채점, 11/3 만들기·주관식·서술형, Swagger `퀴즈`, docs/frontend-requests.md "퀴즈")
export function getQuizzes(): Promise<QuizSummary[]> {
  if (isMockData()) return mockGetQuizzes();
  return request<QuizSummary[]>({ method: 'GET', url: '/quizzes' });
}

export function getQuiz(id: number): Promise<Quiz> {
  if (isMockData()) return mockGetQuiz(id);
  return request<Quiz>({ method: 'GET', url: `/quizzes/${id}` });
}

// AI로 퀴즈 만들기. 서버는 전체 150초(app.quiz.generation-deadline) 안에서만 모델을 기다리므로 그보다 조금 길게 기다린다
export function createQuiz(input: QuizCreateInput): Promise<Quiz> {
  if (isMockData()) return mockCreateQuiz(input);
  return request<Quiz>({
    method: 'POST',
    url: '/quizzes',
    data: input,
    timeout: 180_000,
  });
}

// 만들기 요청 상태. 화면을 새로 고쳤거나 다른 화면에 다녀왔을 때 같은 요청의 결과를 찾는다
export function getGenerationStatus(
  requestKey: string,
): Promise<QuizGenerationStatus> {
  if (isMockData()) return mockGetGenerationStatus(requestKey);
  return request<QuizGenerationStatus>({
    method: 'GET',
    url: `/quizzes/generations/${encodeURIComponent(requestKey)}`,
  });
}

// 주관식·서술형은 AI 채점을 기다리므로 오래 걸릴 수 있다
export function submitQuiz(
  id: number,
  answers: QuizAnswer[],
): Promise<QuizResult> {
  if (isMockData()) return mockSubmitQuiz(id, answers);
  return request<QuizResult>({
    method: 'POST',
    url: `/quizzes/${id}/submissions`,
    data: { answers },
    timeout: 150_000,
  });
}

// AI 채점을 기다리는(pending) 답만 다시 채점한다
export function regradeQuiz(
  id: number,
  submissionId: number,
): Promise<QuizResult> {
  if (isMockData()) return mockRegradeQuiz(id);
  return request<QuizResult>({
    method: 'POST',
    url: `/quizzes/${id}/submissions/${submissionId}/grading`,
    timeout: 150_000,
  });
}

// 아직 안 풀었으면 null
export function getLatestQuizResult(id: number): Promise<QuizResult | null> {
  if (isMockData()) return mockGetLatestQuizResult(id);
  return request<QuizResult | null>({
    method: 'GET',
    url: `/quizzes/${id}/submissions/latest`,
  });
}
