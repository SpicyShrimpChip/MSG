import type { ReactNode } from 'react';

interface FormRowProps {
  label: string;
  htmlFor: string;
  required?: boolean;
  hint?: string;
  // 입력 오류 문구. 입력칸에는 aria-describedby={`${htmlFor}-error`}를 붙인다
  error?: string;
  children: ReactNode;
}

// 폼 한 칸: 위에 라벨, 아래 입력칸·오류·도움말. 여러 칸은 세로로 간격을 두고 쌓는다
function FormRow({
  label,
  htmlFor,
  required,
  hint,
  error,
  children,
}: FormRowProps) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={htmlFor} className="text-body font-semibold text-heading">
        {label}
        {required && (
          <span className="ml-1 text-primary">
            *<span className="sr-only">필수</span>
          </span>
        )}
      </label>
      {children}
      {error !== undefined && (
        <p
          id={`${htmlFor}-error`}
          aria-live="polite"
          className="text-caption text-danger empty:hidden"
        >
          {error}
        </p>
      )}
      {hint && <p className="text-caption text-ink-muted">{hint}</p>}
    </div>
  );
}

export default FormRow;
