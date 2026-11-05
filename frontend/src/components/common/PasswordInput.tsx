import { useState, type ComponentProps } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '@/utils';
import TextInput from './TextInput';

// 비밀번호 입력 칸 + 오른쪽 "비밀번호 보기" 버튼. 처음에는 가려 두고, 누를 때만 글자를 보여 준다.
// 나머지 속성(id, autoComplete, value 등)은 그대로 input에 넘긴다. type은 이 부품이 정한다
function PasswordInput({
  className,
  ...props
}: Omit<ComponentProps<'input'>, 'type'>) {
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;

  return (
    <div className="relative">
      <TextInput
        {...props}
        type={visible ? 'text' : 'password'}
        // 엣지가 따로 그리는 보기 버튼과 겹치지 않게 숨긴다
        className={cn('w-full pr-12 [&::-ms-reveal]:hidden', className)}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}
        aria-pressed={visible}
        aria-controls={props.id}
        className="absolute top-1/2 right-1 flex size-9 -translate-y-1/2 items-center justify-center rounded-md text-ink-soft hover:text-heading focus-visible:outline-2 focus-visible:outline-primary"
      >
        <Icon className="size-5" aria-hidden="true" />
      </button>
    </div>
  );
}

export default PasswordInput;
