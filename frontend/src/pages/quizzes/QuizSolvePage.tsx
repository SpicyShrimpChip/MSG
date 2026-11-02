import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { LoaderCircle } from 'lucide-react';
import { QUIZZES_QUERY_KEY, getQuiz, submitQuiz } from '@/api';
import { AsyncBoundary, Button, Card, SubPage, ErrorText } from '@/components';

function QuizForm({ quizId }: { quizId: number }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { data: quiz } = useSuspenseQuery({
    queryKey: [...QUIZZES_QUERY_KEY, quizId],
    queryFn: () => getQuiz(quizId),
  });
  // 문제 id → 고른 보기 번호
  const [answers, setAnswers] = useState<Record<number, number>>({});

  // 채점은 서버가 한다. 받은 결과를 결과 화면 캐시에 넣고 목록의 지난 점수를 새로 받는다
  const mutation = useMutation({
    mutationFn: () =>
      submitQuiz(
        quizId,
        Object.entries(answers).map(([questionId, choiceIndex]) => ({
          questionId: Number(questionId),
          choiceIndex,
        })),
      ),
    onSuccess: (result) => {
      queryClient.setQueryData(
        [...QUIZZES_QUERY_KEY, quizId, 'result'],
        result,
      );
      void queryClient.invalidateQueries({
        queryKey: QUIZZES_QUERY_KEY,
        exact: true,
      });
      navigate(`/quizzes/${quizId}/result`);
    },
  });

  const answered = Object.keys(answers).length;
  const total = quiz.questions.length;

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (answered < total) return;
    mutation.mutate();
  };

  return (
    <form onSubmit={handleSubmit} className="flex max-w-3xl flex-col gap-6">
      <h2 className="text-2xl font-extrabold text-heading">{quiz.title}</h2>
      <ol className="flex flex-col gap-4">
        {quiz.questions.map((question, index) => (
          <Card as="li" key={question.id} className="p-5">
            <fieldset className="flex flex-col gap-3">
              <legend className="mb-3 font-bold text-heading">
                <span className="mr-2 text-primary">Q{index + 1}.</span>
                {question.text}
              </legend>
              {question.choices.map((choice, choiceIndex) => {
                const checked = answers[question.id] === choiceIndex;
                return (
                  <label
                    key={choice}
                    className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-body ${
                      checked
                        ? 'border-primary bg-primary-soft text-heading'
                        : 'border-line text-ink hover:bg-surface-muted'
                    }`}
                  >
                    <input
                      type="radio"
                      name={`question-${question.id}`}
                      checked={checked}
                      onChange={() =>
                        setAnswers((prev) => ({
                          ...prev,
                          [question.id]: choiceIndex,
                        }))
                      }
                    />
                    {choice}
                  </label>
                );
              })}
            </fieldset>
          </Card>
        ))}
      </ol>

      <ErrorText error={mutation.error} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body text-ink-soft" aria-live="polite">
          {answered < total
            ? `${total}문제 중 ${answered}문제 답했어요. 모두 답하면 제출할 수 있어요.`
            : '모두 답했어요.'}
        </p>
        <Button
          type="submit"
          size="lg"
          disabled={answered < total || mutation.isPending}
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
    </form>
  );
}

function QuizSolvePage() {
  const { quizId } = useParams();
  return (
    <SubPage title="퀴즈 풀기" description="보기 하나를 골라 주세요">
      <AsyncBoundary>
        <QuizForm quizId={Number(quizId)} />
      </AsyncBoundary>
    </SubPage>
  );
}

export default QuizSolvePage;
