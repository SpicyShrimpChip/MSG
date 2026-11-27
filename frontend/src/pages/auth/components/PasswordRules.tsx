import { Check, Circle } from 'lucide-react';
import { checkPassword, cn } from '@/utils';

interface PasswordRulesProps {
  id: string;
  password: string;
}

// 비밀번호 조건 체크리스트. 색만으로 구분하지 않도록 아이콘과 글자("충족"/"아직")를 함께 쓴다
function PasswordRules({ id, password }: PasswordRulesProps) {
  return (
    <ul
      id={id}
      aria-label="비밀번호 조건"
      className="flex flex-wrap gap-x-4 gap-y-1"
    >
      {checkPassword(password).map((rule) => (
        <li
          key={rule.id}
          className={cn(
            'flex items-center gap-1 text-caption',
            rule.met ? 'text-strength' : 'text-ink-muted',
          )}
        >
          {rule.met ? (
            <Check size={14} aria-hidden="true" />
          ) : (
            <Circle size={14} aria-hidden="true" />
          )}
          {rule.label}
          <span className="sr-only">
            {rule.met ? ' 충족' : ' 아직 아니에요'}
          </span>
        </li>
      ))}
    </ul>
  );
}

export default PasswordRules;
