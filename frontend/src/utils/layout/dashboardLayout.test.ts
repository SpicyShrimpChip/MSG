import { describe, expect, it } from 'vitest';
import { DEFAULT_LAYOUT, parseDashboardLayout } from './dashboardLayout';

const valid = {
  generatedAt: '2026-10-25T14:00:00',
  aiSummary: '이해 부족 개념이 있어 추천 순서를 위에 두었어요.',
  basis: { notUnderstoodCount: 3, disagreementCount: 2, conversationCount: 12 },
  items: [
    {
      component: 'conceptDetail',
      order: 2,
      size: 'normal',
      aiReason: '근거 확인',
    },
    {
      component: 'recommendation',
      order: 1,
      size: 'wide',
      aiReason: '먼저 할 일',
    },
  ],
};

describe('parseDashboardLayout', () => {
  it('검증을 통과하면 순서대로 정렬하고 빠진 영역은 기본 순서로 뒤에 붙인다', () => {
    const layout = parseDashboardLayout(valid);
    expect(layout.explanation?.basis.notUnderstoodCount).toBe(3);
    expect(layout.items.map((item) => item.component)).toEqual([
      'recommendation',
      'conceptDetail',
      'summary',
      'conceptTable',
      'models',
    ]);
    expect(layout.items.map((item) => item.order)).toEqual([1, 2, 3, 4, 5]);
    expect(layout.items[2].aiReason).toBeNull();
  });

  it('배치가 없으면 기본 배치', () => {
    expect(parseDashboardLayout(undefined)).toBe(DEFAULT_LAYOUT);
  });

  it.each([
    [
      '없는 영역 id',
      { component: 'superCard', order: 3, size: 'wide', aiReason: 'x' },
    ],
    [
      '허용하지 않는 크기',
      { component: 'models', order: 3, size: 'huge', aiReason: 'x' },
    ],
    [
      '너무 긴 이유',
      {
        component: 'models',
        order: 3,
        size: 'wide',
        aiReason: '가'.repeat(81),
      },
    ],
    [
      '빈 이유',
      { component: 'models', order: 3, size: 'wide', aiReason: '  ' },
    ],
    [
      '같은 영역 두 번',
      { component: 'recommendation', order: 3, size: 'wide', aiReason: 'x' },
    ],
    [
      '겹치는 순서',
      { component: 'models', order: 1, size: 'wide', aiReason: 'x' },
    ],
  ])('%s → 기본 배치', (_, item) => {
    const layout = parseDashboardLayout({
      ...valid,
      items: [...valid.items, item],
    });
    expect(layout).toBe(DEFAULT_LAYOUT);
  });

  it('근거 숫자가 음수거나 형식이 다르면 기본 배치', () => {
    expect(
      parseDashboardLayout({
        ...valid,
        basis: { ...valid.basis, notUnderstoodCount: -1 },
      }),
    ).toBe(DEFAULT_LAYOUT);
    expect(parseDashboardLayout('<script>alert(1)</script>')).toBe(
      DEFAULT_LAYOUT,
    );
  });
});
