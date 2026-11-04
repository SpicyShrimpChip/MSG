import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BookOpen, Check, type LucideIcon } from 'lucide-react';
import {
  analysisLatestKey,
  deleteConceptFeedback,
  saveConceptFeedback,
} from '@/api';
import type { ConceptFeedbackType, ConceptResult } from '@/types';
import { Button } from '@/components';
import { cn } from '@/utils';

interface ConceptFeedbackBoxProps {
  courseId: number;
  runId: number;
  concept: ConceptResult;
}

const OPTIONS: {
  value: ConceptFeedbackType;
  label: string;
  icon: LucideIcon;
}[] = [
  { value: 'already_know', label: '이미 알아요', icon: Check },
  { value: 'want_to_study', label: '더 공부할래요', icon: BookOpen },
];

// 확인 퀴즈 결과 (코드 규칙, 서버 confirmation). 버튼을 눌렀다고 바뀌지 않고 실제로 푼 문제로만 정한다
const CONFIRMATION_LABEL: Record<
  NonNullable<NonNullable<ConceptResult['quiz']>['confirmation']>,
  string
> = {
  confirmed: '확인 퀴즈 결과: 이 개념을 알고 있는 것으로 보여요',
  not_confirmed: '확인 퀴즈 결과: 다시 살펴보면 좋겠어요',
  needs_check:
    '확인 퀴즈 결과: 문항이 적거나 채점이 끝나지 않아 추가 확인이 필요해요',
  not_taken: '아직 이 선택 뒤에 푼 확인 퀴즈가 없어요',
};

// "이 판단, 내 생각과 맞나요?" 내 선택은 AI 판단과 따로 저장하고, AI 판단은 바꾸지 않는다.
// "더 공부할래요"는 추천 순서 맨 앞으로만 옮긴다 (순서는 서버가 다시 매긴다)
function ConceptFeedbackBox({
  courseId,
  runId,
  concept,
}: ConceptFeedbackBoxProps) {
  const queryClient = useQueryClient();
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: analysisLatestKey(courseId) });

  const save = useMutation({
    mutationFn: (feedback: ConceptFeedbackType) =>
      saveConceptFeedback(courseId, {
        concept: concept.concept,
        feedback,
        analysisRunId: runId,
      }),
    onSuccess: refresh,
  });
  const cancel = useMutation({
    mutationFn: (id: number) => deleteConceptFeedback(id),
    onSuccess: refresh,
  });

  const current = concept.feedback?.feedback ?? null;
  const busy = save.isPending || cancel.isPending;
  const error = save.error ?? cancel.error;

  return (
    <div className="flex flex-col gap-3 border-t border-line pt-4">
      <p id={`feedback-${concept.concept}`} className="font-bold text-heading">
        이 판단, 내 생각과 맞나요?
      </p>
      <div
        role="group"
        aria-labelledby={`feedback-${concept.concept}`}
        className="flex gap-2"
      >
        {OPTIONS.map(({ value, label, icon: Icon }) => {
          const active = current === value;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={active}
              disabled={busy}
              onClick={() => save.mutate(value)}
              className={cn(
                'flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border font-bold disabled:opacity-60',
                active
                  ? 'border-2 border-primary bg-primary-soft text-primary'
                  : 'border-line-strong bg-surface-raised text-heading hover:bg-surface-muted',
              )}
            >
              <Icon size={16} className="text-primary" aria-hidden="true" />
              {label}
            </button>
          );
        })}
      </div>

      <div aria-live="polite" className="flex flex-col gap-2 empty:hidden">
        {current === 'already_know' && (
          <div className="flex flex-col gap-3 rounded-xl bg-surface-muted px-4 py-3 text-body leading-relaxed text-ink-soft">
            <p>
              AI 판단은 그대로 두고 내 선택만 따로 남겼어요. 확인 퀴즈를 풀면
              결과를 이 개념 옆에 함께 보여 줘요.
            </p>
            {concept.quiz?.confirmation && (
              <p className="font-bold text-heading">
                {CONFIRMATION_LABEL[concept.quiz.confirmation]}
              </p>
            )}
            <Button
              to={`/quizzes/new?courseId=${courseId}&mode=self_development&concept=${encodeURIComponent(concept.concept)}`}
              variant="secondary"
              className="self-start"
            >
              확인 퀴즈 만들기
            </Button>
          </div>
        )}
        {current === 'want_to_study' && (
          <p className="rounded-xl bg-surface-muted px-4 py-3 text-body leading-relaxed text-ink-soft">
            추천 학습 순서 맨 앞에 넣었어요. AI 판단은 그대로예요.{' '}
            <Link
              to="/curriculum"
              className="font-bold text-primary underline-offset-4 hover:underline"
            >
              맞춤 커리큘럼에서 복습 퀴즈·자료 보기
            </Link>
          </p>
        )}
        {error && (
          <p role="alert" className="text-body text-danger">
            {error.message}
          </p>
        )}
      </div>

      {concept.feedback && (
        <button
          type="button"
          disabled={busy}
          onClick={() => cancel.mutate(concept.feedback!.id)}
          className="self-start text-caption font-bold text-ink-soft underline-offset-4 hover:text-heading hover:underline"
        >
          선택 취소
        </button>
      )}
    </div>
  );
}

export default ConceptFeedbackBox;
