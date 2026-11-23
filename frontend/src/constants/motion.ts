// 움직임 기준값 (초). CSS 쪽 기본 전환 시간은 index.css의 --default-transition-duration과 맞춘다
export const MOTION_DURATION = { fast: 0.15, base: 0.25, slow: 0.45 } as const;

// 빠르게 시작해 부드럽게 멈추는 곡선 (index.css의 --default-transition-timing-function과 같은 값)
export const MOTION_EASE = [0.22, 1, 0.36, 1] as const;

export const MOTION_TRANSITION = {
  duration: MOTION_DURATION.base,
  ease: MOTION_EASE,
} as const;

// 주 메뉴 알약의 양 끝 (Gnb). 가는 쪽 앞 끝은 빨리 닿고 뒤 끝은 늦게 끌려와
// 목표 쪽으로 늘어났다 줄어드는 액체 느낌을 낸다
export const PILL_LEAD_SPRING = {
  type: 'spring',
  stiffness: 620,
  damping: 40,
} as const;
export const PILL_TRAIL_SPRING = {
  type: 'spring',
  stiffness: 210,
  damping: 26,
} as const;

// 상단바 숨기기·다시 보이기 (Header). 숨을 때는 빠르게 위로 빠지고,
// 나올 때는 스프링으로 살짝 지나쳤다가 탄력 있게 제자리를 잡는다
export const HEADER_HIDE_TRANSITION = {
  duration: MOTION_DURATION.base,
  ease: MOTION_EASE,
} as const;
export const HEADER_SHOW_SPRING = {
  type: 'spring',
  stiffness: 500,
  damping: 30,
} as const;

// 장면을 가림막 걷듯 바꾸는 움직임 (메인 스크롤 소개). 길게 미끄러지다 천천히 멈춘다
export const SWEEP_TRANSITION = {
  duration: 0.64,
  ease: [0.16, 1, 0.3, 1],
} as const;

// 나타날 때 쓰는 공통 움직임: 살짝 아래에서 올라오며 짙어진다
export const FADE_UP = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: 4 },
  transition: MOTION_TRANSITION,
} as const;
