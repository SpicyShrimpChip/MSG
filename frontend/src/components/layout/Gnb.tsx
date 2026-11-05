import type { Ref } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { MENU } from '@/constants';
import { currentMenuPath, isActiveGroup } from './menuActive';

interface GnbProps {
  // 상단바가 알약 폭을 재는 데 쓴다
  ref?: Ref<HTMLElement>;
  // 스크롤해서 상단바가 알약이 됐을 때: 아래 선 대신 연한 파랑 알약으로 현재 위치를 보여 준다
  compact?: boolean;
}

// 상단바 안의 주 메뉴. 좁은 화면에서는 햄버거 메뉴(MobileMenu)가 대신한다.
// 알약 폭을 메뉴 칸 너비로 재므로, 두 모양에서 칸 너비(px-3·글자 굵기)는 같게 둔다
function Gnb({ ref, compact = false }: GnbProps) {
  const { pathname } = useLocation();
  const current = currentMenuPath(MENU, pathname);

  return (
    <nav
      ref={ref}
      aria-label="주 메뉴"
      className="flex-1 self-stretch max-lg:hidden"
    >
      <ul className="flex h-full gap-1">
        {MENU.map((group) => {
          const active = isActiveGroup(group, current);
          return (
            // 마우스를 올리거나 키보드로 포커스가 들어오면 하위 메뉴를 연다
            // (마우스 클릭 포커스로는 열지 않아 이동 후 메뉴가 남지 않는다)
            // 하위 메뉴는 상단바에서 8px 띄워 둔다. 그 틈을 지나는 동안 닫히지 않게 메뉴 칸 아래로 보이지 않는 다리(after)를 놓는다
            <li
              key={group.label}
              className="group relative flex items-center after:absolute after:inset-x-0 after:top-full after:h-2 after:content-['']"
            >
              <Link
                to={group.path}
                aria-current={active ? 'page' : undefined}
                className={`flex items-center px-3 text-base ${
                  compact ? 'h-10 rounded-full' : 'h-full'
                } ${
                  active
                    ? `font-bold text-primary ${compact ? 'bg-primary-soft' : 'shadow-[inset_0_-3px_0_var(--color-primary)]'}`
                    : 'font-medium text-heading hover:text-primary'
                }`}
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
