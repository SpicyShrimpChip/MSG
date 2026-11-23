import { Suspense, lazy, useEffect, useState } from 'react';
import type { OrbState } from 'thinking-orbs';
import { cn } from '@/utils/cn';

// 구슬 코드(gzip 약 10KB)는 공통 부품 묶음에 넣지 않고 구슬이 처음 뜰 때 받는다
const ThinkingOrb = lazy(() =>
  import('thinking-orbs').then((module) => ({ default: module.ThinkingOrb })),
);

// 화면 모드 폴백 전환(styles/theme-transition.css)이 끝난 뒤 한 번 더 읽는 시간. useThemeStore의 THEME_SWITCH_MS와 같다
const THEME_SWITCH_MS = 400;

// 구슬 색은 hex·rgb 글자만 받아서 CSS 변수를 직접 넘길 수 없다. 지금 화면 모드의 primary 값을 읽어 넘긴다
function usePrimaryColor() {
  const read = () =>
    getComputedStyle(document.documentElement)
      .getPropertyValue('--color-primary')
      .trim() || undefined;
  const [color, setColor] = useState(read);
  useEffect(() => {
    let timer: number | undefined;
    const observer = new MutationObserver(() => {
      setColor(read());
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setColor(read()), THEME_SWITCH_MS);
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });
    return () => {
      observer.disconnect();
      window.clearTimeout(timer);
    };
  }, []);
  return color;
}

// 32는 라이브러리가 64·20 사이를 계산한 크기라 점이 적게 그려진다(connecting은 64에서 약 40개 → 32에서 약 15개).
// 화면에서 비교해 보고 점 개수(dots)·굵기(dotSize)를 채웠다. 표에 없는 움직임은 라이브러리 기본값
const ORB_TUNING: Partial<
  Record<OrbState, { dots?: number; dotSize?: number }>
> = {
  connecting: { dots: 2, dotSize: 1.3 },
  weaving: { dotSize: 1.3 },
};

interface AiOrbProps {
  // 움직임 모양. 세 모델이 따로 판단하는 분석은 weaving, 그 밖의 AI 작업(만들기·채점)은 connecting
  state?: OrbState;
  className?: string;
}

// AI 응답을 기다리는 동안 도는 점 구슬(32px). 보통은 안내 글과 함께 AiWaiting으로 쓰고,
// 아이콘 자리(빈 화면 EmptyState 등)에는 이것만 넣는다.
// 화면 상태 표시라 AI 보라가 아니라 primary 파랑으로 칠한다(기본 흑백은 밝은 화면의 연한 파랑 바탕에 묻힌다).
// 꾸밈용이라 화면 낭독기에는 숨기고 진행 상태는 옆 글자로 알린다. "동작 줄이기"면 라이브러리가 멈춘 그림만 그린다
function AiOrb({ state = 'connecting', className }: AiOrbProps) {
  const color = usePrimaryColor();
  return (
    // 받는 동안은 같은 크기의 빈 칸으로 자리를 잡아 옆 글자가 밀리지 않게 한다
    <Suspense fallback={<span className={cn('size-8 shrink-0', className)} />}>
      <ThinkingOrb
        state={state}
        size={32}
        color={color}
        {...ORB_TUNING[state]}
        aria-hidden="true"
        className={cn('shrink-0', className)}
      />
    </Suspense>
  );
}

export default AiOrb;
