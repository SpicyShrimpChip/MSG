import { useSuspenseQuery } from '@tanstack/react-query';
import { QUIZZES_QUERY_KEY, getQuizzes, quizQuery } from '@/api';

// 퀴즈 목록. 불러오는 동안·실패는 감싼 AsyncBoundary가 처리한다
export function useQuizzes() {
  return useSuspenseQuery({ queryKey: QUIZZES_QUERY_KEY, queryFn: getQuizzes });
}

// 퀴즈 하나 (풀기 화면). 만들기 화면이 넣어 둔 캐시가 있으면 다시 부르지 않는다
export function useQuiz(id: number) {
  return useSuspenseQuery(quizQuery(id));
}
