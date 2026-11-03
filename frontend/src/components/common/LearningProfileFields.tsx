import {
  JOB_FIELDS,
  JOB_FIELD_OTHER_MAX,
  LEARNING_PURPOSES,
} from '@/constants';
import type { JobField, LearningPurpose } from '@/types';
import { cn } from '@/utils';
import FormRow from './FormRow';
import SelectInput from './SelectInput';
import TextInput from './TextInput';

export interface LearningProfileValue {
  learningPurpose: LearningPurpose;
  jobField: JobField | null;
  jobFieldOther: string;
}

interface LearningProfileFieldsProps {
  idPrefix: string;
  value: LearningProfileValue;
  onChange: (value: LearningProfileValue) => void;
}

// 학습 목적(하나)과 관심 직무(업무 활용일 때 고름). 가입·마이페이지가 함께 쓴다.
// 하는 일을 자세히 적게 하지 않고 분야만 고르게 한다. 목록에 없으면 기타 + 짧은 이름
function LearningProfileFields({
  idPrefix,
  value,
  onChange,
}: LearningProfileFieldsProps) {
  const showJob = value.learningPurpose === 'work' || value.jobField !== null;
  return (
    <div className="flex flex-col gap-5">
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 font-bold text-heading">
          주로 어떤 목적으로 공부하나요?
        </legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {LEARNING_PURPOSES.map((purpose) => {
            const checked = value.learningPurpose === purpose.value;
            return (
              <label
                key={purpose.value}
                className={cn(
                  'flex cursor-pointer flex-col gap-1 rounded-xl border px-4 py-3',
                  checked
                    ? 'border-2 border-primary bg-primary-soft'
                    : 'border-line hover:bg-surface-muted',
                )}
              >
                <span className="flex items-center gap-2 font-bold text-heading">
                  <input
                    type="radio"
                    name={`${idPrefix}-purpose`}
                    checked={checked}
                    onChange={() =>
                      onChange({ ...value, learningPurpose: purpose.value })
                    }
                  />
                  {purpose.label}
                </span>
                <span className="text-caption text-ink-soft">
                  {purpose.description}
                </span>
              </label>
            );
          })}
        </div>
        <p className="text-caption text-ink-muted">
          기본값으로 쓰고, 퀴즈를 만들 때마다 다른 목적을 골라도 돼요.
        </p>
      </fieldset>

      {showJob && (
        <FormRow
          label="관심 직무"
          htmlFor={`${idPrefix}-job`}
          hint="업무 활용 문제의 상황을 이 분야에 맞춰요. 고르지 않으면 일반적인 실무 상황으로 만들어요."
        >
          <SelectInput
            id={`${idPrefix}-job`}
            value={value.jobField ?? ''}
            onChange={(e) =>
              onChange({
                ...value,
                jobField: (e.target.value || null) as JobField | null,
              })
            }
          >
            <option value="">고르지 않음</option>
            {JOB_FIELDS.map((job) => (
              <option key={job.value} value={job.value}>
                {job.label}
              </option>
            ))}
          </SelectInput>
        </FormRow>
      )}
      {showJob && value.jobField === 'other' && (
        <FormRow
          label="분야 이름"
          htmlFor={`${idPrefix}-job-other`}
          required
          hint={`예: 보안 감사, 정보시스템 감리 (${JOB_FIELD_OTHER_MAX}자까지, 연락처·회사 이름은 적지 마세요)`}
        >
          <TextInput
            id={`${idPrefix}-job-other`}
            type="text"
            required
            maxLength={JOB_FIELD_OTHER_MAX}
            value={value.jobFieldOther}
            onChange={(e) =>
              onChange({ ...value, jobFieldOther: e.target.value })
            }
          />
        </FormRow>
      )}
    </div>
  );
}

export default LearningProfileFields;
