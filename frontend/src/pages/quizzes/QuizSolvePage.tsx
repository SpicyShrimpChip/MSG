import { useEffect, useState, type SubmitEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { Briefcase, LoaderCircle, Sparkles } from 'lucide-react';
import {
  CURRICULUM_QUERY_KEY,
  QUIZZES_QUERY_KEY,
  STUDY_QUERY_KEY,
  getQuiz,
  submitQuiz,
} from '@/api';
import {
  AsyncBoundary,
  Button,
  Card,
  Chip,
  ConfirmDialog,
  ErrorText,
  FIELD_CLASS,
  Notice,
  SubPage,
} from '@/components';
import {
  QUESTION_TYPE_LABELS,
  jobFieldLabel,
  quizModeLabel,
} from '@/constants';
import type { QuizAnswer, QuizQuestion } from '@/types';
import { cn } from '@/utils';
import {
  answeredCount,
  loadDraft,
  saveDraft,
  clearDraft,
  toAnswers,
  type Draft,
} from './quizDraft';

const SHORT_MAX = 200;
const ESSAY_MAX = 5000;

function QuestionInput({
  question,
  draft,
  onChange,
}: {
  question: QuizQuestion;
  draft: Draft;
  onChange: (next: Draft) => void;
}) {
  if (question.type === 'multiple_choice') {
    return (
      <div className="flex flex-col gap-3">
        {question.choices.map((choice, choiceIndex) => {
          const checked = draft.choices[question.id] === choiceIndex;
          return (
            <label
              key={choice}
              className={cn(
                'flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-body',
                checked
                  ? 'border-primary bg-primary-soft text-heading'
                  : 'border-line text-ink hover:bg-surface-muted',
              )}
            >
              <input
                type="radio"
                name={`question-${question.id}`}
                checked={checked}
                onChange={() =>
                  onChange({
                    ...draft,
                    choices: { ...draft.choices, [question.id]: choiceIndex },
                  })
                }
              />
              {choice}
            </label>
          );
        })}
      </div>
    );
  }
  const essay = question.type === 'essay';
  const max = essay ? ESSAY_MAX : SHORT_MAX;
  const value = draft.texts[question.id] ?? '';
  const id = `answer-${question.id}`;
  const props = {
    id,
    value,
    maxLength: max,
    'aria-label': `${QUESTION_TYPE_LABELS[question.type]} 답`,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      onChange({
        ...draft,
        texts: { ...draft.texts, [question.id]: e.target.value },
      }),
  };
  return (
    <div className="flex flex-col gap-1.5">
      {essay ? (
        <textarea
          {...props}
          rows={8}
          placeholder="자신의 말로 여러 문장으로 써 주세요"
          className={cn(FIELD_CLASS, 'px-3 py-2 leading-relaxed')}
        />
      ) : (
        <input
          {...props}
          type="text"
          placeholder="단어나 짧은 문장으로 써 주세요"
          className={cn(FIELD_CLASS, 'h-11 px-3')}
        />
      )}
      <p className="self-end text-caption text-ink-muted">
        {value.length}/{max}자
      </p>
    </div>
  );
}

// 모의시험에서 푼 시간 (참고용, 시간 제한은 두지 않는다)
function ElapsedTime() {
  const [started] = useState(() => Date.now());
  const [now, setNow] = useState(started);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const seconds = Math.floor((now - started) / 1000);
  const label = `${Math.floor(seconds / 60)}분 ${String(seconds % 60).padStart(2, '0')}초`;
  return (
    <span className="text-caption text-ink-soft" aria-live="off">
      푼 시간 {label}
    </span>
  );
}

function QuizForm({ quizId }: { quizId: number }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { data: quiz } = useSuspenseQuery({
    queryKey: [...QUIZZES_QUERY_KEY, quizId],
    queryFn: () => getQuiz(quizId),
  });
  // 쓰던 답은 이 탭에서만 기억한다 (새로고침해도 남고, 제출하거나 탭을 닫으면 지운다)
  const [draft, setDraft] = useState<Draft>(() => loadDraft(quizId));
  const [confirming, setConfirming] = useState(false);
  useEffect(() => saveDraft(quizId, draft), [quizId, draft]);

  // 채점은 서버가 한다. 받은 결과를 결과 화면 캐시에 넣고 목록의 지난 점수를 새로 받는다
  const mutation = useMutation({
    mutationFn: (answers: QuizAnswer[]) => submitQuiz(quizId, answers),
    onSuccess: (result) => {
      clearDraft(quizId);
      queryClient.setQueryData(
        [...QUIZZES_QUERY_KEY, quizId, 'result'],
        result,
      );
      void queryClient.invalidateQueries({
        queryKey: QUIZZES_QUERY_KEY,
        exact: true,
      });
      // 확인 퀴즈 결과로 개념 학습 상태·커리큘럼 단계가 바뀐다
      void queryClient.invalidateQueries({ queryKey: STUDY_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: CURRICULUM_QUERY_KEY });
      navigate(`/quizzes/${quizId}/result`);
    },
  });

  const answered = answeredCount(quiz.questions, draft);
  const total = quiz.questions.length;
  const needsAi = quiz.questions.some((q) => q.type !== 'multiple_choice');
  const modeLabel = quizModeLabel(quiz.mode);
  const job = jobFieldLabel(quiz.jobField, quiz.jobFieldOther);

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (answered === 0) return;
    setConfirming(true);
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-extrabold text-heading">{quiz.title}</h2>
        <p className="flex flex-wrap items-center gap-2 text-caption text-ink-muted">
          {modeLabel && (
            <Chip tone="primary">
              {quiz.assessmentKind === 'pre'
                ? '사전평가'
                : quiz.assessmentKind === 'post'
                  ? '사후평가'
                  : quiz.mockExam
                    ? '모의시험'
                    : modeLabel}
            </Chip>
          )}
          {quiz.mockExam && <ElapsedTime />}
          {quiz.mode === 'work' && <Chip>{job ?? '일반 실무'}</Chip>}
          {quiz.examScope && <Chip>시험 범위: {quiz.examScope}</Chip>}
          {quiz.generatedBy && (
            <span className="inline-flex items-center gap-1 text-ai">
              <Sparkles size={14} aria-hidden="true" />
              AI({quiz.generatedBy.provider.toUpperCase()})가 만든 문제
            </span>
          )}
        </p>
        {quiz.mode === 'exam_prep' && (
          <p className="text-caption text-ink-soft">
            시험 범위와 핵심 개념으로 만든 예상 문제예요. 실제 출제를 보장하지
            않아요.
          </p>
        )}
        {quiz.generatedBy && quiz.materialContentUsed && (
          <p className="text-caption text-ink-soft">
            학습자료 본문(발췌)을 바탕으로 만든 문제예요.
          </p>
        )}
        {quiz.generatedBy && !quiz.materialContentUsed && (
          <p className="text-caption text-ink-soft">
            학습자료 본문은 읽지 않고 과목과 개념으로만 만든 문제예요.
          </p>
        )}
      </div>

      <ol className="flex flex-col gap-4">
        {quiz.questions.map((question, index) => (
          <Card as="li" key={question.id} className="flex flex-col gap-4 p-5">
            {question.scenario && (
              <Notice icon={Briefcase} iconClassName="text-primary">
                <p className="font-bold text-heading">업무 상황</p>
                <p className="whitespace-pre-line">{question.scenario}</p>
                {question.role && (
                  <p className="mt-1">
                    <span className="font-bold text-heading">내 역할: </span>
                    {question.role}
                  </p>
                )}
              </Notice>
            )}
            <fieldset className="flex flex-col gap-3">
              <legend className="mb-3 font-bold text-heading">
                <span className="mr-2 text-primary">Q{index + 1}.</span>
                <span className="mr-2 text-caption font-medium text-ink-muted">
                  [{QUESTION_TYPE_LABELS[question.type]}
                  {question.type === 'essay' && ` · ${question.maxScore}점`}]
                </span>
                {question.text}
              </legend>
              {question.guide && (
                <p className="text-caption text-ink-soft">
                  작성 안내: {question.guide}
                </p>
              )}
              {question.criteria.length > 0 && (
                <div className="flex flex-col gap-1 text-caption text-ink-soft">
                  <p className="font-bold text-heading">이렇게 평가해요</p>
                  <ul className="list-inside list-disc">
                    {question.criteria.map((criterion) => (
                      <li key={criterion}>{criterion}</li>
                    ))}
                  </ul>
                </div>
              )}
              <QuestionInput
                question={question}
                draft={draft}
                onChange={setDraft}
              />
            </fieldset>
          </Card>
        ))}
      </ol>

      {needsAi && (
        <p className="text-caption text-ink-soft">
          주관식·서술형 답은 채점을 위해 외부 AI로 보내요. 이메일·전화번호 같은
          개인정보는 쓰지 말아 주세요. 객관식은 서버 코드가 채점해요.
        </p>
      )}

      <ErrorText error={mutation.error} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body text-ink-soft" aria-live="polite">
          {total}문제 중 {answered}문제 답했어요.
          {answered < total &&
            answered > 0 &&
            ' 빈 문제는 틀린 것으로 채점해요.'}
        </p>
        <Button
          type="submit"
          size="lg"
          disabled={answered === 0 || mutation.isPending}
        >
          {mutation.isPending && (
            <LoaderCircle
              size={18}
              aria-hidden="true"
              className="animate-spin"
            />
          )}
          {mutation.isPending ? '채점하는 중…' : '제출하고 채점하기'}
        </Button>
      </div>

      <ConfirmDialog
        open={confirming}
        title="제출할까요?"
        description={
          answered < total
            ? `${total - answered}문제가 비어 있어요. 빈 문제는 틀린 것으로 채점해요.`
            : '제출하면 서버가 채점해요. 다시 풀면 새 기록으로 남아요.'
        }
        confirmLabel="제출하기"
        onConfirm={() => {
          setConfirming(false);
          mutation.mutate(toAnswers(quiz.questions, draft));
        }}
        onCancel={() => setConfirming(false)}
      />
    </form>
  );
}

function QuizSolvePage() {
  const { quizId } = useParams();
  return (
    <SubPage
      narrow="reading"
      title="퀴즈 풀기"
      description="문제를 풀고 제출하면 서버가 채점해요"
    >
      <AsyncBoundary>
        <QuizForm quizId={Number(quizId)} />
      </AsyncBoundary>
    </SubPage>
  );
}

export default QuizSolvePage;
