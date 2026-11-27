import type { ReactNode } from 'react';

// 확인 체크 칸: 네모 칸 + 오른쪽 설명. 설명 안에 작은 안내를 더 둘 때는 children에 `block text-caption` span을 넣는다.
// size="caption"은 작은 글씨 확인(개념 카드의 설명 만들기 확인, 퀴즈 만들기의 본문 일부 보내기 확인)
const LABEL_CLASS = {
  body: 'flex items-start gap-3 text-body text-heading',
  caption: 'flex items-start gap-3 text-caption text-heading',
} as const;
const INPUT_CLASS = {
  body: 'mt-1 size-4 accent-primary',
  caption: 'mt-0.5 size-4 accent-primary',
} as const;

interface CheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  size?: 'body' | 'caption';
  children: ReactNode;
}

function Checkbox({
  checked,
  onChange,
  disabled,
  size = 'body',
  children,
}: CheckboxProps) {
  return (
    <label className={LABEL_CLASS[size]}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className={INPUT_CLASS[size]}
      />
      <span>{children}</span>
    </label>
  );
}

export default Checkbox;
