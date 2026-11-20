import { Suspense } from 'react';
import type { ReactNode } from 'react';
import { QueryErrorResetBoundary } from '@tanstack/react-query';
import { ErrorBoundary } from 'react-error-boundary';
import { useLocation } from 'react-router-dom';

import LoadingFallback from './LoadingFallback';
import ErrorFallback from './ErrorFallback';

interface AsyncBoundaryProps {
  children: ReactNode;
}

export default function AsyncBoundary({ children }: AsyncBoundaryProps) {
  const { pathname, search } = useLocation();

  return (
    // "다시 시도"를 누르면 실패한 조회의 오류 상태도 지워 다시 요청한다
    // (이게 없으면 React Query가 실패한 조회를 다시 부르지 않아 같은 오류 화면이 남는다)
    <QueryErrorResetBoundary>
      {({ reset }) => (
        <ErrorBoundary
          FallbackComponent={ErrorFallback}
          onReset={reset}
          // 같은 화면에서 주소만 바뀌어도(다른 퀴즈·자료로 이동) 이전 오류 화면을 남기지 않는다
          resetKeys={[pathname, search]}
        >
          <Suspense fallback={<LoadingFallback />}>{children}</Suspense>
        </ErrorBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}
