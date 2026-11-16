import { useSuspenseQuery } from '@tanstack/react-query';
import {
  ANALYSIS_QUERY_KEY,
  ASSESSMENTS_QUERY_KEY,
  getProviders,
  getQuizAiStatus,
} from '@/api';

// 학습 분석에 연결된 AI 모델 목록 (키 값 없이 연결 여부만)
export function useProviders() {
  return useSuspenseQuery({
    queryKey: [...ANALYSIS_QUERY_KEY, 'providers'],
    queryFn: getProviders,
  });
}

// 문제 만들기·채점에 쓸 AI 상태 (평가·핵심 개념 뽑기에서 같이 쓴다)
export function useQuizAiStatus() {
  return useSuspenseQuery({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'ai-status'],
    queryFn: getQuizAiStatus,
  });
}
