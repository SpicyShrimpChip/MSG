import { Link, useLocation } from 'react-router-dom';
import { MENU } from '@/constants';
import { currentMenuPath, isActiveGroup } from './menuActive';

// 상단바 알약 안의 주 메뉴 (넓은 화면만). 좁은 화면에서는 햄버거 메뉴(MobileMenu)가 대신한다.
// 현재 위치는 연한 파랑 알약으로 보여 준다
function Gnb() {
  const { pathname } = useLocation();
  const current = currentMenuPath(MENU, pathname);

  return (
    <nav aria-label="주 메뉴" className="self-stretch max-lg:hidden lg:ml-6">
      <ul className="flex h-full gap-5">
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
                className={`flex h-10 items-center rounded-full px-3 text-base ${
                  active
                    ? 'bg-primary-soft font-bold text-primary'
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
