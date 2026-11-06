import { useSyncExternalStore } from 'react';

// 휴대폰 폭(Tailwind sm 미만, 640px 미만)인지. 상단바가 검색·계정을 접는 기준
const QUERY = '(max-width: 639.98px)';

function subscribe(onChange: () => void) {
  const query = window.matchMedia(QUERY);
  query.addEventListener('change', onChange);
  return () => query.removeEventListener('change', onChange);
}

export function usePhone(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(QUERY).matches,
    () => false,
  );
}
