import { z } from 'zod';
import {
  DASHBOARD_COMPONENT_IDS,
  type DashboardLayout,
  type LayoutItem,
} from '@/types';

// LLM은 화면 코드를 만들지 않고, 정해진 영역 id·순서·크기·이유만 JSON으로 준다
const LAYOUT_REASON_MAX_LENGTH = 80;
const LAYOUT_SUMMARY_MAX_LENGTH = 120;

const count = z.number().int().nonnegative();

const layoutItemSchema = z.object({
  component: z.enum(DASHBOARD_COMPONENT_IDS),
  order: z.number().int().min(1).max(DASHBOARD_COMPONENT_IDS.length),
  size: z.enum(['wide', 'normal']),
  aiReason: z.string().trim().min(1).max(LAYOUT_REASON_MAX_LENGTH),
});

const layoutSchema = z.object({
  aiSummary: z.string().trim().min(1).max(LAYOUT_SUMMARY_MAX_LENGTH),
  basis: z.object({
    notUnderstoodCount: count,
    disagreementCount: count,
    conversationCount: count,
  }),
  items: z
    .array(layoutItemSchema)
    .min(1)
    .max(DASHBOARD_COMPONENT_IDS.length)
    .refine(
      (items) =>
        new Set(items.map((item) => item.component)).size === items.length,
      '같은 영역이 두 번 나왔어요',
    )
    .refine(
      (items) => new Set(items.map((item) => item.order)).size === items.length,
      '순서 번호가 겹쳐요',
    ),
});

// 검증 실패·배치 없음일 때 쓰는 기본 배치 (이유 없음)
export const DEFAULT_LAYOUT: DashboardLayout = {
  explanation: null,
  items: [
    { component: 'summary', order: 1, size: 'wide', aiReason: null },
    { component: 'conceptTable', order: 2, size: 'normal', aiReason: null },
    { component: 'conceptDetail', order: 3, size: 'normal', aiReason: null },
    { component: 'models', order: 4, size: 'normal', aiReason: null },
    { component: 'recommendation', order: 5, size: 'normal', aiReason: null },
  ],
};

// 서버가 준 배치 JSON(unknown)을 검증한다. 통과하면 순서대로 정렬하고,
// 빠진 영역은 기본 순서로 뒤에 붙인다. 통과하지 못하면 기본 배치를 돌려준다
export function parseDashboardLayout(raw: unknown): DashboardLayout {
  if (raw === undefined || raw === null) return DEFAULT_LAYOUT;

  const result = layoutSchema.safeParse(raw);
  if (!result.success) {
    if (import.meta.env.DEV) {
      console.warn(
        '배치 JSON 검증 실패, 기본 배치를 씁니다',
        result.error.issues,
      );
    }
    return DEFAULT_LAYOUT;
  }

  const sorted: LayoutItem[] = [...result.data.items].sort(
    (a, b) => a.order - b.order,
  );
  const missing = DEFAULT_LAYOUT.items.filter(
    (item) => !sorted.some((placed) => placed.component === item.component),
  );
  const items = [...sorted, ...missing].map((item, index) => ({
    ...item,
    order: index + 1,
  }));

  return {
    items,
    explanation: { aiSummary: result.data.aiSummary, basis: result.data.basis },
  };
}
