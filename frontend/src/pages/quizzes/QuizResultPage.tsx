import { useParams } from 'react-router-dom';
import { useSuspenseQueries } from '@tanstack/react-query';
import { CircleCheck, CircleX, ListChecks, Sparkles } from 'lucide-react';
import { QUIZZES_QUERY_KEY, getLatestQuizResult, getQuiz } from '@/api';
import {
  AiNote,
  AsyncBoundary,
  Button,
  Card,
  Chip,
  EmptyState,
  ScoreBar,
  SubPage,
} from '@/components';
import { formatDate } from '@/utils';

function QuizResultContent({ quizId }: { quizId: number }) {
  const [{ data: quiz }, { data: result }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [...QUIZZES_QUERY_KEY, quizId],
        queryFn: () => getQuiz(quizId),
      },
      {
        queryKey: [...QUIZZES_QUERY_KEY, quizId, 'result'],
        queryFn: () => getLatestQuizResult(quizId),
      },
    ],
  });

  if (!result) {
    return (
      <EmptyState
        icon={<ListChecks size={24} aria-hidden="true" />}
        title="아직 푼 기록이 없어요"
        description="퀴즈를 끝까지 풀면 결과가 여기에 표시돼요."
        action={{ label: '퀴즈 풀기', to: `/quizzes/${quizId}` }}
      />
    );
  }

  const percent = Math.round((result.correctCount / result.totalCount) * 100);
  const date = formatDate(result.submittedAt);

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Card as="section" aria-label="점수" className="flex flex-col gap-3 p-6">
        <h2 className="text-lg font-extrabold text-heading">{quiz.title}</h2>
        <p className="text-body text-ink">
          {result.totalCount}문제 중{' '}
          <strong className="text-2xl font-extrabold text-primary">
            {result.correctCount}문제
          </strong>{' '}
          맞혔어요 ({percent}%)
        </p>
        <ScoreBar value={percent} />
        {date && (
          <p className="text-caption text-ink-muted">{date}에 푼 결과</p>
        )}
      </Card>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-extrabold text-heading">문제별 결과</h2>
        <p className="flex items-center gap-1.5 text-caption text-ai">
          <Sparkles size={14} aria-hidden="true" />
          AI 표시가 있는 내용은 AI가 작성했어요
        </p>
      </div>

      <ol className="flex flex-col gap-4">
        {quiz.questions.map((question, index) => {
          const item = result.results.find(
            (entry) => entry.questionId === question.id,
          );
          if (!item) return null;
          const Icon = item.correct ? CircleCheck : CircleX;
          return (
            <Card as="li" key={question.id} className="flex flex-col gap-3 p-5">
              <div className="flex items-start justify-between gap-3">
                <p className="font-bold text-heading">
                  <span className="mr-2 text-primary">Q{index + 1}.</span>
                  {question.text}
                </p>
                <span
                  className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-caption font-medium ${
                    item.correct
                      ? 'bg-strength-soft text-strength'
                      : 'bg-weakness-soft text-weakness'
                  }`}
                >
                  <Icon size={14} aria-hidden="true" />
                  {item.correct ? '맞힘' : '틀림'}
                </span>
              </div>
              <dl className="grid gap-1 text-body sm:grid-cols-[auto_1fr] sm:gap-x-4">
                <dt className="text-ink-muted">내 답</dt>
                <dd className="text-ink">
                  {question.choices[item.choiceIndex] ?? '고르지 않음'}
                </dd>
                {!item.correct && (
                  <>
                    <dt className="text-ink-muted">정답</dt>
                    <dd className="font-medium text-heading">
                      {question.choices[item.correctChoiceIndex]}
                    </dd>
                  </>
                )}
              </dl>
              {item.concepts.length > 0 && (
                <p className="flex flex-wrap items-center gap-1.5 text-caption text-ink-muted">
                  관련 개념
                  {item.concepts.map((concept) => (
                    <Chip key={concept.conceptId}>{concept.name}</Chip>
                  ))}
                </p>
              )}
              {item.aiExplanation && (
                <AiNote title="해설">{item.aiExplanation}</AiNote>
              )}
            </Card>
          );
        })}
      </ol>

      <p className="text-body text-ink-soft">
        맞힘·틀림을 관련 개념의 숙련도 근거로 쓰고 학습 대시보드에 보여 주는
        기능은 준비 중이에요.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button to={`/quizzes/${quizId}`}>다시 풀기</Button>
        <Button to="/analysis" variant="secondary">
          학습 대시보드 보기
        </Button>
        <Button to="/quizzes" variant="secondary">
          퀴즈 목록
        </Button>
      </div>
    </div>
  );
}

function QuizResultPage() {
  const { quizId } = useParams();
  return (
    <SubPage title="퀴즈 결과">
      <AsyncBoundary>
        <QuizResultContent quizId={Number(quizId)} />
      </AsyncBoundary>
    </SubPage>
  );
}

export default QuizResultPage;
