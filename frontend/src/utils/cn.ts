import { clsx } from 'clsx';
import type { ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

// index.css @theme에 직접 만든 크기 토큰을 알려 준다. 모르는 이름은 색으로 보고 합쳐 버려서
// 알려 주지 않으면 cn('text-caption', 'text-ink-soft')에서 글자 크기가 사라진다.
// @theme에 --text-*·--container-* 토큰을 추가하면 여기에도 넣는다
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: ['body', 'caption'],
      container: ['page'],
    },
  },
});

// 조건부 클래스 결합(clsx) + 겹치는 클래스는 뒤에 온 값만 남김(twMerge)
// ex) cn('px-2 py-1', isActive && 'bg-primary', 'px-4') -> 'py-1 bg-primary px-4'
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
