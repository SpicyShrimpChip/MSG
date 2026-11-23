import { CircleAlert } from 'lucide-react';
import type { FallbackProps } from 'react-error-boundary';
import { ApiError } from '@/api/request';
import Button from './Button';

// 찾는 것이 없을 때(404) "다시 시도" 대신 보여 줄 돌아갈 곳
export interface NotFoundAction {
  label: string;
  to: string;
}

interface ErrorFallbackProps extends FallbackProps {
  notFoundAction?: NotFoundAction;
}

// AsyncBoundary 안에서 요청이 실패했을 때 보여 주는 화면
// ApiError는 화면에 그대로 보여 줄 수 있는 문구를 담고 있다 (api/request.ts)
// 없거나 남의 것(서버가 404로 답함)이면 다시 불러도 같으므로, 돌아갈 곳이 있으면 그 버튼만 보여 준다
export default function ErrorFallback({
  error,
  resetErrorBoundary,
  notFoundAction,
}: ErrorFallbackProps) {
  const message =
    error instanceof Error && error.message
      ? error.message
      : '잠시 후 다시 시도해 주세요.';
  const notFound =
    notFoundAction !== undefined &&
    error instanceof ApiError &&
    error.status === 404;

  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-2xl bg-surface-muted px-6 py-14 text-center"
    >
      <CircleAlert size={28} aria-hidden="true" className="text-danger" />
      <p className="text-lg font-bold text-heading">
        {notFound ? '찾을 수 없어요' : '내용을 불러오지 못했어요'}
      </p>
      <p className="max-w-120 text-body leading-relaxed text-ink-soft">
        {message}
        {notFound && ' 지워졌거나 주소가 잘못됐을 수 있어요.'}
      </p>
      {notFound ? (
        <Button to={notFoundAction.to} className="mt-2">
          {notFoundAction.label}
        </Button>
      ) : (
        <Button onClick={resetErrorBoundary} className="mt-2">
          다시 시도
        </Button>
      )}
    </div>
  );
}
