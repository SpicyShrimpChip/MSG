import { useSyncExternalStore } from 'react';

// 맨 위에서 이만큼(px) 벗어나면 알약을 조금 얇게 줄인다
const SCROLL_THRESHOLD = 8;

function subscribeScroll(onChange: () => void) {
  window.addEventListener('scroll', onChange, { passive: true });
  return () => window.removeEventListener('scroll', onChange);
}

// 맨 위를 벗어났는지 (넓은 화면의 상단바 알약이 64px → 56px로 얇아진다)
export function useScrolled() {
  return useSyncExternalStore(
    subscribeScroll,
    () => window.scrollY > SCROLL_THRESHOLD,
    () => false,
  );
}
