import { useEffect, type MouseEvent, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import * as m from 'motion/react-m';
import { MENU, MOTION_TRANSITION } from '@/constants';
import { currentMenuPath } from './menuActive';

export const MOBILE_MENU_ID = 'mobile-menu';

interface MobileMenuProps {
  onClose: () => void;
  // 휴대폰 폭에서 상단바에서 뺀 계정 링크·화면 모드 버튼 (메뉴 맨 위에 둔다)
  account?: ReactNode;
}

// 좁은 화면의 메뉴 패널. 상단바 아래로 펼쳐져 아래 내용을 밀어 낸다 (lg 이상에서는 쓰지 않는다)
function MobileMenu({ onClose, account }: MobileMenuProps) {
  const { pathname } = useLocation();
  // '/analysis'와 '/analysis/import'처럼 겹치면 더 긴(구체적인) 경로 하나만 현재 위치로 표시한다
  const currentPath = currentMenuPath(MENU, pathname);

  // 화면이 넓어져 상단바의 주 메뉴가 보이면 닫는다
  useEffect(() => {
    const query = window.matchMedia('(min-width: 1024px)');
    const handleChange = (e: MediaQueryListEvent) => {
      if (e.matches) onClose();
    };
    query.addEventListener('change', handleChange);
    return () => query.removeEventListener('change', handleChange);
  }, [onClose]);

  // 계정 줄의 링크·로그아웃을 누르면 메뉴를 닫는다 (화면 모드 버튼은 그대로 둔다)
  const handleAccountClick = (e: MouseEvent<HTMLDivElement>) => {
    if ((e.target as Element).closest('a, [data-menu-close]')) onClose();
  };

  return (
    // 높이가 0에서 펼쳐지고, 닫을 때는 접히며 사라진다 (Header의 AnimatePresence가 사라지는 동안 붙잡아 둔다)
    <m.nav
      id={MOBILE_MENU_ID}
      aria-label="전체 메뉴"
      initial={{ height: 0, opacity: 0 }}
      animate={{ height: 'auto', opacity: 1 }}
      exit={{ height: 0, opacity: 0 }}
      transition={MOTION_TRANSITION}
      className="overflow-hidden border-t border-line bg-surface-raised text-heading lg:hidden"
    >
      {/* 상단바가 위에 붙어 있어서, 메뉴가 화면보다 길면(가로로 눕힌 휴대폰 등) 페이지가 아니라 이 안에서 스크롤한다 */}
      {account && (
        // 클릭을 받아 닫기만 하고, 누를 수 있는 것은 안의 링크·버튼이다
        <div
          onClick={handleAccountClick}
          className="mx-auto flex max-w-page items-center justify-between gap-3 border-b border-line px-4 py-3 text-sm sm:hidden"
        >
          {account}
        </div>
      )}
      <ul className="mx-auto flex max-h-[calc(100svh-5rem)] max-w-page flex-col gap-4 overflow-y-auto overscroll-contain px-6 py-4 max-sm:max-h-[calc(100svh-8.5rem)] max-sm:px-4">
        {MENU.map((group) => (
          <li key={group.label}>
            <p className="text-base font-bold">{group.label}</p>
            <ul className="mt-1 flex flex-col">
              {group.children.map((child) => {
                const active = child.path === currentPath;
                return (
                  <li key={child.path}>
                    <Link
                      to={child.path}
                      onClick={onClose}
                      aria-current={active ? 'page' : undefined}
                      className={`block py-2 text-sm ${
                        active
                          ? 'font-bold text-primary'
                          : 'text-ink-soft hover:text-heading'
                      }`}
                    >
                      {child.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </li>
        ))}
      </ul>
    </m.nav>
  );
}

export default MobileMenu;
