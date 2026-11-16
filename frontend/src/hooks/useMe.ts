import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { ME_QUERY_KEY, getMe } from '@/api';
import { useAuthStore } from '@/store/useAuthStore';

// 로그인했을 때만 내 정보를 불러온다. 로그인 전이거나 불러오는 중이면 data가 없다
export function useMe() {
  const authenticated = useAuthStore(
    (state) => state.status === 'authenticated',
  );
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
    enabled: authenticated,
  });
}

// 로그인이 필요한 화면에서 내 정보를 기다렸다가 쓴다. 불러오는 동안·실패는 감싼 AsyncBoundary가 처리한다
export function useSuspenseMe() {
  return useSuspenseQuery({ queryKey: ME_QUERY_KEY, queryFn: getMe });
}
