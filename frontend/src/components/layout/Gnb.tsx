import { useLayoutEffect, useRef, useState } from 'react';
import {
  animate,
  useMotionValue,
  useReducedMotion,
  useTransform,
} from 'motion/react';
import * as m from 'motion/react-m';
import { Link, useLocation } from 'react-router-dom';
import { MENU } from '@/constants';
import {
  MOTION_TRANSITION,
  PILL_LEAD_SPRING,
  PILL_TRAIL_SPRING,
} from '@/constants/motion';
import { cn } from '@/utils/cn';
import { currentMenuPath, isActiveGroup } from './menuActive';

interface Slot {
  left: number;
  width: number;
}

// 상단바 알약 안의 주 메뉴 (넓은 화면만). 좁은 화면에서는 햄버거 메뉴(MobileMenu)가 대신한다.
// 연한 파랑 알약 하나가 현재 위치에 있다가, 마우스를 올리거나 키보드로 옮긴 메뉴로 미끄러져 가고 벗어나면 돌아온다(11/6).
// 위치는 메뉴 칸을 재서 x·폭으로 움직인다(layoutId는 LazyMotion의 domMax가 필요해 번들이 커져서 쓰지 않는다).
// 알약의 왼쪽 끝·오른쪽 끝을 따로 움직인다(11/24): 가는 쪽 앞 끝은 빠른 스프링, 뒤 끝은 느린 스프링이라
// 목표 쪽으로 늘어났다가 끌려오듯 줄어든다. 늘어난 만큼 높이를 살짝 줄여 액체처럼 보이게 한다.
// SVG 필터(gooey)는 상단바 유리의 흐림 위에서 스크롤을 느리게 해서 쓰지 않는다
const MAX_SQUASH = 0.14; // 늘어났을 때 높이를 최대 14%까지 줄인다
const SQUASH_PER_STRETCH = 0.12; // 제자리 폭보다 100% 늘어날 때마다 줄이는 비율

function Gnb() {
  const { pathname } = useLocation();
  const current = currentMenuPath(MENU, pathname);
  const activeIndex = MENU.findIndex((group) => isActiveGroup(group, current));
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const listRef = useRef<HTMLUListElement>(null);
  const itemRefs = useRef<(HTMLLIElement | null)[]>([]);
  const reduceMotion = useReducedMotion();

  // 알약 양 끝(메뉴 목록 왼쪽 기준 px)과 도착했을 때의 폭
  const leftEdge = useMotionValue(0);
  const rightEdge = useMotionValue(0);
  const restWidth = useMotionValue(0);
  const pillWidth = useTransform(() => rightEdge.get() - leftEdge.get());
  const pillScaleY = useTransform(() => {
    const rest = restWidth.get();
    if (rest <= 0) return 1;
    const stretch = Math.max(0, pillWidth.get() / rest - 1);
    return 1 - Math.min(MAX_SQUASH, stretch * SQUASH_PER_STRETCH);
  });
  const placedTarget = useRef<number | null>(null);

  // 메뉴 칸의 위치·폭을 잰다. 글꼴이 늦게 불러와지거나 창 폭이 바뀌어 메뉴 폭이 달라지면 다시 잰다
  useLayoutEffect(() => {
    const list = listRef.current;
    if (!list) return;
    const measure = () =>
      setSlots(
        itemRefs.current.map((item) => ({
          left: item?.offsetLeft ?? 0,
          width: item?.offsetWidth ?? 0,
        })),
      );
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(list);
    return () => observer.disconnect();
  }, []);

  const target = hoverIndex ?? (activeIndex >= 0 ? activeIndex : null);
  const slot = target === null ? undefined : slots[target];
  const visible = slot !== undefined && slot.width > 0;
  const slotLeft = slot?.left ?? 0;
  const slotWidth = slot?.width ?? 0;

  // 목표 칸이 바뀌면 양 끝을 서로 다른 스프링으로 보낸다.
  // 처음 놓일 때, 같은 칸을 다시 잰 때(글꼴·창 폭), "동작 줄이기"에서는 바로 옮긴다
  useLayoutEffect(() => {
    if (!visible) return;
    const left = slotLeft;
    const right = slotLeft + slotWidth;
    restWidth.set(slotWidth);
    const jump =
      reduceMotion ||
      placedTarget.current === null ||
      placedTarget.current === target;
    placedTarget.current = target;
    if (jump) {
      leftEdge.jump(left);
      rightEdge.jump(right);
      return;
    }
    const movingRight = left > leftEdge.get();
    const leftControls = animate(
      leftEdge,
      left,
      movingRight ? PILL_TRAIL_SPRING : PILL_LEAD_SPRING,
    );
    const rightControls = animate(
      rightEdge,
      right,
      movingRight ? PILL_LEAD_SPRING : PILL_TRAIL_SPRING,
    );
    return () => {
      leftControls.stop();
      rightControls.stop();
    };
  }, [
    visible,
    target,
    slotLeft,
    slotWidth,
    reduceMotion,
    leftEdge,
    rightEdge,
    restWidth,
  ]);

  return (
    <nav aria-label="주 메뉴" className="self-stretch max-lg:hidden lg:ml-6">
      <ul
        ref={listRef}
        className="relative flex h-full items-center gap-5"
        onMouseLeave={() => setHoverIndex(null)}
        onBlur={(event) => {
          // 메뉴 밖으로 포커스가 나가면 현재 위치로 돌아간다
          if (!event.currentTarget.contains(event.relatedTarget)) {
            setHoverIndex(null);
          }
        }}
      >
        {/* 칸을 잰 뒤에 그려서(initial={false}) 처음에는 왼쪽 끝에서 미끄러져 오지 않고 제자리에 바로 놓인다.
            갈 곳이 없으면(현재 위치 없는 화면에서 마우스가 나감) 그 자리에서 흐려지기만 한다 */}
        {slots.length > 0 && (
          <m.span
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-0 -mt-5 h-10 rounded-full bg-primary-soft"
            style={{ x: leftEdge, width: pillWidth, scaleY: pillScaleY }}
            initial={false}
            animate={{ opacity: visible ? 1 : 0 }}
            transition={reduceMotion ? { duration: 0 } : MOTION_TRANSITION}
          />
        )}
        {MENU.map((group, index) => {
          const active = index === activeIndex;
          const highlighted = index === target;
          return (
            // 마우스를 올리거나 키보드로 포커스가 들어오면 하위 메뉴를 연다
            // (마우스 클릭 포커스로는 열지 않아 이동 후 메뉴가 남지 않는다)
            // 하위 메뉴는 상단바에서 8px 띄워 둔다. 그 틈을 지나는 동안 닫히지 않게 메뉴 칸 아래로 보이지 않는 다리(after)를 놓는다
            <li
              key={group.label}
              ref={(node) => {
                itemRefs.current[index] = node;
              }}
              className="group relative flex items-center after:absolute after:inset-x-0 after:top-full after:h-2 after:content-['']"
              onMouseEnter={() => setHoverIndex(index)}
              onFocus={() => setHoverIndex(index)}
            >
              <Link
                to={group.path}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'relative flex h-10 items-center rounded-full px-3 text-base',
                  active ? 'font-bold' : 'font-medium',
                  highlighted || active ? 'text-primary' : 'text-heading',
                )}
              >
                {group.label}
              </Link>
              {/* 상단바 알약과 같은 유리 그림자·선(styles/glass.css). 흐림(backdrop-filter)은 걸지 않는다:
                  상단바 알약의 흐림 위에 겹쳐 걸면 크롬이 파란 네모를 그렸다(11/7). 대신 거의 불투명한 바탕을 깐다.
                  살짝 아래에서 올라오며 나타난다 */}
              <ul className="glass invisible absolute top-full left-0 z-10 mt-2 min-w-44 translate-y-1 rounded-2xl bg-surface-raised/95 p-1.5 opacity-0 backdrop-filter-none transition-[opacity,translate,visibility] group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 group-has-[:focus-visible]:visible group-has-[:focus-visible]:translate-y-0 group-has-[:focus-visible]:opacity-100 motion-reduce:transition-none">
                {group.children.map((child) => (
                  <li key={child.path}>
                    <Link
                      to={child.path}
                      className="block rounded-xl px-3 py-2 text-sm whitespace-nowrap text-heading hover:bg-primary-soft/80 hover:text-primary"
                    >
                      {child.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

export default Gnb;
