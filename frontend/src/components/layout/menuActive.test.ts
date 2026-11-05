import { describe, expect, it } from 'vitest';
import { MENU } from '@/constants';
import { currentMenuPath, isActiveGroup } from './menuActive';

const activeGroups = (pathname: string) => {
  const current = currentMenuPath(MENU, pathname);
  return MENU.filter((group) => isActiveGroup(group, current)).map(
    (group) => group.label,
  );
};

describe('주 메뉴 현재 위치', () => {
  it.each([
    ['/courses', '학습자료'],
    ['/materials/12', '학습자료'],
    ['/materials/upload', '학습자료'],
    ['/analysis/import', '학습자료'],
    ['/analysis', '학습분석'],
    ['/curriculum', '맞춤학습'],
    ['/assessments', '맞춤학습'],
    ['/quizzes/3/result', '맞춤학습'],
    ['/privacy', '이용안내'],
  ])('%s는 %s 하나만 켠다', (pathname, label) => {
    expect(activeGroups(pathname)).toEqual([label]);
  });

  it('메뉴에 없는 주소는 아무것도 켜지 않는다', () => {
    expect(activeGroups('/')).toEqual([]);
    expect(activeGroups('/notices')).toEqual([]);
    expect(activeGroups('/analysisx')).toEqual([]);
  });

  it('하위 메뉴 주소는 서로 겹치지 않는다', () => {
    const paths = MENU.flatMap((group) => group.children.map((c) => c.path));
    expect(new Set(paths).size).toBe(paths.length);
  });
});
