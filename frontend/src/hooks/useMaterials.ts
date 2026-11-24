import { useSuspenseQuery } from '@tanstack/react-query';
import { materialsQuery } from '@/api';

// 내 자료 목록 (자료 목록·퀴즈 만들기·평가 화면에서 같이 쓴다). 불러오는 동안·실패는 감싼 AsyncBoundary가 처리한다
export function useMaterials() {
  return useSuspenseQuery(materialsQuery());
}
