import type { MenuGroup } from '@/constants';

// 현재 경로가 이 메뉴 경로이거나 그 아래 경로인지
const isActivePath = (path: string, pathname: string) =>
  pathname === path || pathname.startsWith(`${path}/`);

// 현재 위치를 나타내는 메뉴 경로 하나. 겹치면 더 긴(구체적인) 경로를 고른다.
// 예: '/analysis/import'는 학습자료 묶음의 하위 메뉴라서, 앞부분이 같은 학습분석('/analysis')이 함께 켜지지 않게 한다
export const currentMenuPath = (menu: MenuGroup[], pathname: string) =>
  menu
    .flatMap((group) => [
      group.path,
      ...group.children.map((child) => child.path),
    ])
    .filter((path) => isActivePath(path, pathname))
    .sort((a, b) => b.length - a.length)[0];

// 현재 위치가 이 메뉴 묶음(대메뉴 또는 하위 메뉴)에 속하는지
export const isActiveGroup = (group: MenuGroup, current: string | undefined) =>
  current !== undefined &&
  [group.path, ...group.children.map((child) => child.path)].includes(current);
