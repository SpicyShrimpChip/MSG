import {
  use,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import * as m from 'motion/react-m';
import { MOTION_DURATION, SWEEP_TRANSITION } from '@/constants';
import { cn } from '@/utils';
import { SceneMotionContext } from './sceneMotionContext';

// 장면 안 움직임 부품. 움직임이 꺼져 있으면(SceneMotionContext) 마지막 모습을 그대로 그린다.
// delay는 장면이 들어온 뒤부터 센 시간(초)

// 개인정보 가림막이 한 번 쓸고 지나가는 시간(초)
const MASK_SWEEP_DURATION = 0.8;

const TAGS = { div: m.div, li: m.li, p: m.p, span: m.span } as const;

const EFFECTS = {
  // 살짝 아래에서 올라오며 짙어진다
  rise: { initial: { opacity: 0, y: 14 }, animate: { opacity: 1, y: 0 } },
  // 작게 시작해 톡 커진다 (체크 표시·배지·순번)
  pop: {
    initial: { opacity: 0, scale: 0.3 },
    animate: { opacity: 1, scale: 1 },
  },
};

interface RevealProps {
  as?: keyof typeof TAGS;
  effect?: keyof typeof EFFECTS;
  delay?: number;
  className?: string;
  children?: ReactNode;
}

export function Reveal({
  as = 'div',
  effect = 'rise',
  delay = 0,
  className,
  children,
}: RevealProps) {
  const animated = use(SceneMotionContext);
  if (!animated) {
    const Tag = as;
    return <Tag className={className}>{children}</Tag>;
  }
  const Tag = TAGS[as];
  return (
    <Tag
      {...EFFECTS[effect]}
      transition={{
        ...SWEEP_TRANSITION,
        duration: effect === 'pop' ? 0.35 : 0.5,
        delay,
      }}
      className={className}
    >
      {children}
    </Tag>
  );
}

// 처음엔 또렷했다가 흐려진다 (고르지 않은 대화)
export function Dim({
  delay = 0,
  className,
  children,
}: {
  delay?: number;
  className?: string;
  children: ReactNode;
}) {
  const animated = use(SceneMotionContext);
  if (!animated) {
    return <span className={cn('opacity-50', className)}>{children}</span>;
  }
  return (
    <m.span
      initial={{ opacity: 1 }}
      animate={{ opacity: 0.5 }}
      transition={{ duration: MOTION_DURATION.slow, delay }}
      className={className}
    >
      {children}
    </m.span>
  );
}

// 개인정보를 가리는 장면: 파란 막이 글자 위를 덮는 순간 원래 글자를 [이메일] 같은 표시로 바꾸고, 막이 걷히며 드러난다.
// 바꾼 뒤에는 표시 길이에 맞게 줄어든다. 화면 읽기 프로그램에는 가린 뒤의 글자만 읽힌다
export function MaskSwap({
  original,
  masked,
  delay = 0,
}: {
  original: string;
  masked: string;
  delay?: number;
}) {
  const animated = use(SceneMotionContext);
  const [swapped, setSwapped] = useState(!animated);

  // 막이 글자를 다 덮은 때(쓸기 시간의 절반쯤)에 바꾼다
  useEffect(() => {
    if (!animated) return;
    const timer = window.setTimeout(
      () => setSwapped(true),
      (delay + MASK_SWEEP_DURATION / 2) * 1000,
    );
    return () => window.clearTimeout(timer);
  }, [animated, delay]);

  // 바뀐 뒤 폭을 재서 상자 폭을 서서히 줄인다 (뒤의 글자가 한 번에 툭 당겨지지 않게)
  const labelRef = useRef<HTMLSpanElement>(null);
  const [width, setWidth] = useState<number>();
  useLayoutEffect(() => {
    if (animated && labelRef.current) setWidth(labelRef.current.offsetWidth);
  }, [animated, swapped]);

  const label = swapped ? (
    <span
      ref={labelRef}
      className="inline-block rounded-md bg-primary-pill px-2 font-bold whitespace-nowrap text-heading"
    >
      {masked}
    </span>
  ) : (
    <span
      ref={labelRef}
      aria-hidden="true"
      className="inline-block px-1 whitespace-nowrap"
    >
      {original}
    </span>
  );

  if (!animated) return label;
  return (
    // 줄 높이를 본문과 같게(inherit) 두고 위에 맞춰, 바뀌는 순간 위아래로 들썩이지 않게 한다
    <span
      className="relative inline-block overflow-hidden rounded-md align-top leading-[inherit] whitespace-nowrap transition-[width] duration-300 ease-out"
      style={width === undefined ? undefined : { width }}
    >
      {label}
      <m.span
        aria-hidden="true"
        initial={{ x: '-101%' }}
        animate={{ x: ['-101%', '0%', '0%', '101%'] }}
        transition={{
          duration: MASK_SWEEP_DURATION,
          times: [0, 0.4, 0.6, 1],
          ease: SWEEP_TRANSITION.ease,
          delay,
        }}
        className="absolute inset-0 rounded-md bg-primary"
      />
    </span>
  );
}

// 섞여 있던 자리(from)에서 추천 자리(to)로 옮겨 간다. 자리 번호 한 칸 = 높이 + 간격
export function SlotItem({
  from,
  to,
  slotHeight,
  delay = 0,
  className,
  children,
}: {
  from: number;
  to: number;
  slotHeight: number;
  delay?: number;
  className?: string;
  children: ReactNode;
}) {
  const animated = use(SceneMotionContext);
  if (!animated) {
    return (
      <li
        className={className}
        style={{ transform: `translateY(${to * slotHeight}px)` }}
      >
        {children}
      </li>
    );
  }
  return (
    <m.li
      initial={{ y: from * slotHeight }}
      animate={{ y: to * slotHeight }}
      transition={{ ...SWEEP_TRANSITION, duration: 0.9, delay }}
      className={className}
    >
      {children}
    </m.li>
  );
}
