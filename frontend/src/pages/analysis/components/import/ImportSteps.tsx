import { Check } from 'lucide-react';

// 가져오기 단계. 파싱·필터·마스킹은 모두 브라우저에서 하고, 마지막 단계에서 고른 대화만 보낸다
const IMPORT_STEPS = [
  '파일 고르기',
  '공부한 대화 고르기',
  '가린 내용 확인',
  '보내기',
] as const;

interface ImportStepsProps {
  current: number;
}

// 번호 원과 단계 이름을 한 줄로. 지난 단계는 체크, 지금 단계는 파랑으로 채운다
function ImportSteps({ current }: ImportStepsProps) {
  return (
    <ol className="flex flex-wrap items-center gap-x-3 gap-y-2">
      {IMPORT_STEPS.map((step, index) => {
        const done = index < current;
        const active = index === current;
        return (
          <li
            key={step}
            aria-current={active ? 'step' : undefined}
            className="flex items-center gap-3"
          >
            {index > 0 && (
              <span
                aria-hidden="true"
                className={`h-0.5 w-6 rounded-full ${done || active ? 'bg-primary' : 'bg-line'}`}
              />
            )}
            <span className="flex items-center gap-2">
              <span
                className={`flex size-7 shrink-0 items-center justify-center rounded-full text-caption font-semibold ${
                  active
                    ? 'bg-primary text-on-primary'
                    : done
                      ? 'bg-primary-soft text-primary'
                      : 'bg-surface-muted text-ink-muted'
                }`}
              >
                {done ? <Check size={14} aria-hidden="true" /> : index + 1}
              </span>
              <span
                className={`text-body ${
                  active
                    ? 'font-semibold text-heading'
                    : done
                      ? 'text-ink-soft'
                      : 'text-ink-muted'
                }`}
              >
                {step}
                {done && <span className="sr-only">(완료)</span>}
              </span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}

export default ImportSteps;
