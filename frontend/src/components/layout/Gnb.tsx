import { useLayoutEffect, useRef, useState } from 'react';
import { useReducedMotion } from 'motion/react';
import * as m from 'motion/react-m';
import { Link, useLocation } from 'react-router-dom';
import { MENU } from '@/constants';
import { MOTION_TRANSITION } from '@/constants/motion';
import { cn } from '@/utils/cn';
import { currentMenuPath, isActiveGroup } from './menuActive';

interface Slot {
  left: number;
  width: number;
}

// 상단바 알약 안의 주 메뉴 (넓은 화면만). 좁은 화면에서는 햄버거 메뉴(MobileMenu)가 대신한다.
// 연한 파랑 알약 하나가 현재 위치에 있다가, 마우스를 올리거나 키보드로 옮긴 메뉴로 미끄러져 가고 벗어나면 돌아온다(11/6).
// 위치는 메뉴 칸을 재서 x·폭으로 움직인다(layoutId는 LazyMotion의 domMax가 필요해 번들이 커져서 쓰지 않는다)
function Gnb() {
  const { pathname } = useLocation();
  const current = currentMenuPath(MENU, pathname);
  const activeIndex = MENU.findIndex((group) => isActiveGroup(group, current));
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const listRef = useRef<HTMLUListElement>(null);
  const itemRefs = useRef<(HTMLLIElement | null)[]>([]);
  const reduceMotion = useReducedMotion();

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
            initial={false}
            animate={
              visible
                ? { x: slot.left, width: slot.width, opacity: 1 }
                : { opacity: 0 }
            }
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
              {/* 상단바 알약과 같은 유리 재질(styles/glass.css). 살짝 아래에서 올라오며 나타난다 */}
              <ul className="glass invisible absolute top-full left-0 z-10 mt-2 min-w-44 translate-y-1 rounded-2xl p-1.5 opacity-0 transition-[opacity,translate] group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 group-has-[:focus-visible]:visible group-has-[:focus-visible]:translate-y-0 group-has-[:focus-visible]:opacity-100 motion-reduce:transition-none">
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
