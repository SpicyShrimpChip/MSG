import {
  Suspense,
  lazy,
  useEffect,
  useState,
  useSyncExternalStore,
  type CSSProperties,
} from 'react';
import { cn } from '@/utils/cn';

// 라이브러리 기본 세기는 흰 카드 위에서 거의 안 보여, 라이브러리가 열어 둔 층별 진하기 배율을 올린다
// (서서히 나타나고 사라지는 효과는 그대로). 밝은 화면의 테두리 선은 검정이라 회색 줄로 보여 끈다
const BEAM_STRENGTH: Record<'light' | 'dark', CSSProperties> = {
  light: {
    '--beam-inner-opacity': 3,
    '--beam-bloom-opacity': 3,
    '--beam-stroke-opacity': 0,
  } as CSSProperties,
  dark: {
    '--beam-inner-opacity': 3,
    '--beam-bloom-opacity': 3,
  } as CSSProperties,
};

// 빛 코드(gzip 약 13KB)는 공통 부품 묶음에 넣지 않고 빛을 처음 돌릴 때 받는다
const BorderBeam = lazy(() =>
  import('border-beam').then((module) => ({ default: module.BorderBeam })),
);

// 한 바퀴 시간(초). 라이브러리 기본값(1.96)보다 조금 느리게 해 눈에 들어오게 한다
const LAP_SECONDS = 2.4;

// 라이브러리의 theme="auto"는 기기 설정만 봐서, 상단바로 고른 화면 모드(<html>의 dark 클래스)를 직접 읽는다
const subscribeDark = (onChange: () => void) => {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  });
  return () => observer.disconnect();
};
const readDark = () => document.documentElement.classList.contains('dark');

const reduceMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

interface ResultBeamProps {
  // true가 되는 순간 테두리를 따라 빛이 한 바퀴 돌고 사라진다
  active: boolean;
  // 빛이 다 사라진 뒤 (부모가 active를 내린다)
  onDone: () => void;
  className?: string;
}

// 새 결과가 도착한 카드 위에 겹쳐 그리는 테두리 빛. 카드를 감싸지 않고 위에 덮어서(absolute)
// 빛을 켜고 끌 때 카드 안의 상태(펼침·선택)가 다시 만들어지지 않게 한다. 부모는 relative여야 한다.
// 꾸밈용이라 화면 낭독기에는 숨기고, "동작 줄이기"면 그리지 않는다(새 결과는 화면 내용으로 알 수 있다)
function ResultBeam({ active, onDone, className }: ResultBeamProps) {
  const dark = useSyncExternalStore(subscribeDark, readDark);
  const [playing, setPlaying] = useState(active);
  const [seenActive, setSeenActive] = useState(active);
  // active가 다시 켜지면 처음부터 돈다 (이전 값과 비교해 렌더 중에 맞춘다)
  if (active !== seenActive) {
    setSeenActive(active);
    setPlaying(active);
  }

  useEffect(() => {
    if (!active) return;
    if (reduceMotion()) {
      onDone();
      return;
    }
    // 한 바퀴 뒤 끄면 라이브러리가 서서히 지우고 onDeactivate를 부른다
    const timer = window.setTimeout(
      () => setPlaying(false),
      LAP_SECONDS * 1000,
    );
    return () => window.clearTimeout(timer);
  }, [active, onDone]);

  if (!active) return null;
  return (
    <Suspense fallback={null}>
      <BorderBeam
        aria-hidden="true"
        active={playing}
        onDeactivate={onDone}
        // 밝기·채도를 올리고, 색이 청록으로 흐르지 않게 색 흔들림을 줄였다
        colorVariant="ocean"
        brightness={2}
        saturation={1.8}
        hueRange={15}
        theme={dark ? 'dark' : 'light'}
        style={BEAM_STRENGTH[dark ? 'dark' : 'light']}
        duration={LAP_SECONDS}
        className={cn('pointer-events-none absolute! inset-0', className)}
      >
        <div className="size-full rounded-2xl" />
      </BorderBeam>
    </Suspense>
  );
}

export default ResultBeam;
