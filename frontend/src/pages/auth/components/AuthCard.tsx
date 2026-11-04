import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

interface AuthCardProps {
  title: string;
  description?: string;
  // 입력칸이 많은 화면(회원가입)은 카드를 더 넓게 쓴다
  wide?: boolean;
  children: ReactNode;
}

// 로그인·회원가입 틀: 제목과 폼을 한 장의 흰 카드에 담아 화면 가운데에 놓는다.
// 일반 서브페이지(SubPage)는 1200px 칸에 제목을 왼쪽에 붙이는데, 폼만 폭을 줄이면 왼쪽으로 쏠려 보여서 따로 둔다
function AuthCard({
  title,
  description,
  wide = false,
  children,
}: AuthCardProps) {
  return (
    <div className="mx-auto max-w-page px-4 pt-8 pb-20 md:px-6 md:pt-12">
      <div
        className={cn(
          'mx-auto flex w-full flex-col gap-6 rounded-2xl border border-line bg-surface-raised p-6 shadow-sm md:p-8',
          wide ? 'max-w-xl' : 'max-w-md',
        )}
      >
        <div className="flex flex-col gap-2 text-center">
          <h1 className="text-2xl font-extrabold tracking-tight text-heading">
            {title}
          </h1>
          {description && <p className="text-ink-soft">{description}</p>}
        </div>
        {children}
      </div>
    </div>
  );
}

export default AuthCard;
