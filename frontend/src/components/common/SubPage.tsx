import type { ReactNode } from 'react';
import { cn } from '@/utils';

// 폼·글 위주 화면의 본문 폭. 제목과 본문을 이 폭으로 함께 가운데에 둔다(넓은 창에서 왼쪽으로 쏠리지 않게)
// 화면 안쪽에는 따로 폭 제한을 두지 않는다(이 값 하나로 맞춘다)
const NARROW_CLASS = {
  form: 'max-w-4xl',
  reading: 'max-w-5xl',
} as const;

interface SubPageProps {
  title: string;
  description?: string;
  // 없으면 페이지 전체 폭(표·카드 목록 화면). 있으면 그 폭으로 가운데 정렬
  narrow?: keyof typeof NARROW_CLASS;
  children: ReactNode;
}

// 서브페이지 공통 틀: 왼쪽 정렬 제목·부제, 아래 본문 (현재 위치는 상단바 주 메뉴의 선택 표시로 알려 준다)
function SubPage({ title, description, narrow, children }: SubPageProps) {
  return (
    <div className="mx-auto max-w-page px-6 pt-10 pb-20">
      <div
        className={cn(
          'mx-auto flex flex-col gap-8',
          narrow && cn('w-full', NARROW_CLASS[narrow]),
        )}
      >
        <div className="flex flex-col gap-2">
          <h1 className="text-3xl font-extrabold tracking-tight text-heading">
            {title}
          </h1>
          {description && <p className="text-ink-soft">{description}</p>}
        </div>
        {children}
      </div>
    </div>
  );
}

export default SubPage;
