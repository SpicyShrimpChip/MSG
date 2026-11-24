import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { ANALYSIS_QUERY_KEY, getProviders, quizAiStatusQuery } from '@/api';

// 학습 분석에 연결된 AI 모델 목록 (키 값 없이 연결 여부만)
export function useProviders() {
  return useSuspenseQuery({
    queryKey: [...ANALYSIS_QUERY_KEY, 'providers'],
    queryFn: getProviders,
  });
}

// 문제 만들기·채점에 쓸 AI 상태 (평가·핵심 개념 뽑기에서 같이 쓴다)
export function useQuizAiStatus() {
  return useSuspenseQuery(quizAiStatusQuery());
}

// 같은 AI 상태를 화면을 막지 않고 읽는다 (안내 문구용: 불러오는 중·실패면 data가 비어 있다)
export function useQuizAiStatusQuery() {
  return useQuery(quizAiStatusQuery());
}
