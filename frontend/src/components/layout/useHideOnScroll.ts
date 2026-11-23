import { useEffect, useState } from 'react';

// 맨 위에서 이만큼(px) 안쪽이면 늘 보인다 (상단바 자리 높이쯤)
const TOP_ZONE = 80;
// 손떨림·관성 스크롤의 작은 움직임으로 깜빡이지 않게, 한 방향으로 이만큼(px) 움직여야 바꾼다
const MIN_DELTA = 6;

// 지난번 위치(from)에서 지금 위치(to)로 스크롤했을 때 상단바를 숨길지.
// 아래로 내리면 숨고, 조금이라도 위로 올리거나 맨 위 근처면 보인다. 작은 움직임은 지금 상태를 유지한다
export function nextHeaderHidden(hidden: boolean, from: number, to: number) {
  if (to <= TOP_ZONE) return false;
  const delta = to - from;
  if (Math.abs(delta) < MIN_DELTA) return hidden;
  return delta > 0;
}

// 학습 화면(요약·퀴즈·개념 목록)을 읽는 동안 상단바가 자리를 차지하지 않게,
// 아래로 내리면 숨기고 위로 올리면 다시 보인다(11/24)
export function useHideOnScroll() {
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    let last = window.scrollY;
    let current = false;
    const handleScroll = () => {
      const now = window.scrollY;
      const next = nextHeaderHidden(current, last, now);
      // 작은 움직임은 기준 위치를 옮기지 않고 쌓아 두어, 천천히 굴려도 결국 바뀌게 한다
      if (next !== current || Math.abs(now - last) >= MIN_DELTA) last = now;
      if (next !== current) {
        current = next;
        setHidden(next);
      }
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return hidden;
}
