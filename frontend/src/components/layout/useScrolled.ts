import {
  useLayoutEffect,
  useState,
  useSyncExternalStore,
  type RefObject,
} from 'react';

// 맨 위에서 이만큼(px) 벗어나면 상단바를 알약으로 바꾼다
const SCROLL_THRESHOLD = 8;
// Tailwind lg (64rem). 좁은 화면은 햄버거 메뉴라 알약으로 바꾸지 않는다
const DESKTOP_QUERY = '(min-width: 64rem)';

// 알약 안쪽 여백·칸 사이·테두리(px). Header의 알약 클래스(gap-x-5 pl-5 pr-3 border)와 같게 맞춘다
const CAPSULE_SPACING = {
  gap: 20,
  paddingLeft: 20,
  paddingRight: 12,
  border: 2,
};
// 주 메뉴 칸 사이 (Gnb의 gap-1)
const MENU_GAP = 4;
// 상단바 검색 칸 폭(px). Header의 검색 form 클래스(w-9 / w-42)와 같게 맞춘다
export const SEARCH_WIDTH = { collapsed: 36, open: 168 };

function subscribeScroll(onChange: () => void) {
  window.addEventListener('scroll', onChange, { passive: true });
  return () => window.removeEventListener('scroll', onChange);
}

function subscribeDesktop(onChange: () => void) {
  const query = window.matchMedia(DESKTOP_QUERY);
  query.addEventListener('change', onChange);
  return () => query.removeEventListener('change', onChange);
}

// 넓은 화면에서 맨 위를 벗어났는지
export function useCapsuleMode() {
  const scrolled = useSyncExternalStore(
    subscribeScroll,
    () => window.scrollY > SCROLL_THRESHOLD,
    () => false,
  );
  const desktop = useSyncExternalStore(
    subscribeDesktop,
    () => window.matchMedia(DESKTOP_QUERY).matches,
    () => false,
  );
  return scrolled && desktop;
}

// 알약 폭: width:auto는 전환이 안 되므로 로고·메뉴 칸·오른쪽 묶음의 실제 너비를 더해 숫자로 만든다.
// 글꼴이 늦게 불러와지거나 로그인해서 이름이 생기면 너비가 바뀌므로 크기 변화를 지켜본다.
// 검색 칸은 접히고 펼쳐지는 동안 폭이 프레임마다 바뀌므로, 지금 폭 대신 도착할 폭(searchWidth)으로 계산한다.
// (지금 폭을 그대로 쓰면 알약의 목표 폭이 계속 바뀌어 전환이 매번 다시 시작되며 끊겨 보였다)
export function useCapsuleWidth(
  logoRef: RefObject<HTMLElement | null>,
  menuRef: RefObject<HTMLElement | null>,
  rightRef: RefObject<HTMLElement | null>,
  searchRef: RefObject<HTMLElement | null>,
  searchWidth: number,
) {
  const [width, setWidth] = useState(0);

  useLayoutEffect(() => {
    const logo = logoRef.current;
    const list = menuRef.current?.querySelector('ul');
    const right = rightRef.current;
    const search = searchRef.current;
    if (!logo || !list || !right || !search) return;
    const items = [...list.children] as HTMLElement[];

    const measure = () => {
      const menuWidth =
        items.reduce(
          (sum, item) => sum + item.getBoundingClientRect().width,
          0,
        ) +
        MENU_GAP * Math.max(0, items.length - 1);
      const { gap, paddingLeft, paddingRight, border } = CAPSULE_SPACING;
      setWidth(
        Math.ceil(
          logo.getBoundingClientRect().width +
            menuWidth +
            right.getBoundingClientRect().width -
            search.getBoundingClientRect().width +
            searchWidth +
            gap * 2 +
            paddingLeft +
            paddingRight +
            border,
        ),
      );
    };

    measure();
    const observer = new ResizeObserver(measure);
    [logo, right, ...items].forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [logoRef, menuRef, rightRef, searchRef, searchWidth]);

  return width;
}
