import { useParams } from 'react-router-dom';
import {
  useMutation,
  useQueryClient,
  useSuspenseQueries,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { ListChecks, RefreshCw, Sparkles } from 'lucide-react';
import {
  QUIZZES_QUERY_KEY,
  STUDY_QUERY_KEY,
  getConceptStudies,
  getLatestQuizResult,
  getQuiz,
  regradeQuiz,
} from '@/api';
import {
  AiNote,
  AsyncBoundary,
  Button,
  Card,
  EmptyState,
  ErrorText,
  ScoreBar,
  StudyStatusBadge,
  SubPage,
} from '@/components';
import { QUESTION_TYPE_LABELS, quizModeLabel } from '@/constants';
import type {
  AssessmentStatus,
  Quiz,
  QuizResult,
  SuggestionKind,
} from '@/types';
import { conceptStudyLink, formatDate } from '@/utils';
import QuestionResultCard from './components/QuestionResultCard';

const percentOf = (score: number, max: number) =>
  max === 0 ? 0 : Math.round((score / max) * 100);

// 결과 판단(코드 규칙) 문구. 모드마다 무엇을 판단하는지가 다르다
const STATUS_TITLE: Record<
  'exam_prep' | 'self_development' | 'work' | 'none',
  Record<AssessmentStatus, string>
> = {
  exam_prep: {
    ready: '시험 범위 핵심 개념을 잘 풀었어요',
    not_ready: '복습이 필요한 개념이 있어요',
    needs_check: '판단하기에 근거가 부족해요',
  },
  self_development: {
    ready: '다음 단계로 넘어갈 준비가 된 것으로 보여요',
    not_ready: '다음 단계 전에 보완이 필요해요',
    needs_check: '추가 확인이 필요해요',
  },
  work: {
    ready: '업무 상황에서 평가 기준을 잘 채웠어요',
    not_ready: '평가 기준 중 보완할 부분이 있어요',
    needs_check: '추가 확인이 필요해요',
  },
  none: {
    ready: '잘 풀었어요',
    not_ready: '보완이 필요한 개념이 있어요',
    needs_check: '판단하기에 근거가 부족해요',
  },
};

const SUGGESTION_LABEL: Record<SuggestionKind, string> = {
  next: '다음 단계',
  prerequisite: '보완할 선수 지식',
  related: '연관 분야',
};

function Assessment({ quiz, result }: { quiz: Quiz; result: QuizResult }) {
  const { assessment } = result;
  const weak = assessment.byConcept.filter((concept) => concept.weak);
  // 준비됐으면 다음 단계·연관 분야를, 아니면 선수 지식을 먼저 보여 준다
  const order: SuggestionKind[] =
    assessment.status === 'ready'
      ? ['next', 'related', 'prerequisite']
      : ['prerequisite', 'next', 'related'];
  const suggestions = [...result.suggestions].sort(
    (a, b) => order.indexOf(a.kind) - order.indexOf(b.kind),
  );
  return (
    <Card
      as="section"
      aria-labelledby="quiz-assessment"
      className="flex flex-col gap-4 p-6"
    >
      <h2 id="quiz-assessment" className="text-lg font-extrabold text-heading">
        {STATUS_TITLE[quiz.mode ?? 'none'][assessment.status]}
      </h2>
      <ul className="flex list-inside list-disc flex-col gap-1 text-body text-ink">
        {assessment.reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
      <p className="text-caption text-ink-muted">
        채점 결과를 서버 코드가 정한 기준으로 판단했어요. 숙련도 점수가 아니고,
        문항이 적거나 채점이 끝나지 않으면 단정하지 않아요.
      </p>

      {weak.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="font-bold text-heading">
            {quiz.mode === 'exam_prep' ? '틀린 개념 복습하기' : '보완할 개념'}
          </p>
          <div className="flex flex-wrap gap-2">
            {weak.map((concept) => (
              <Button
                key={concept.conceptId}
                variant="secondary"
                to={`/quizzes/new?courseId=${quiz.courseId}&mode=${quiz.mode ?? 'exam_prep'}&concept=${encodeURIComponent(concept.name)}`}
              >
                {concept.name} 다시 풀기
              </Button>
            ))}
          </div>
        </div>
      )}

      {suggestions.length > 0 && (
        <AiNote title="다음에 공부할 개념 제안">
          <ul className="flex flex-col gap-1.5">
            {suggestions.map((item) => (
              <li key={`${item.kind}-${item.concept}`}>
                <span className="font-bold">
                  [{SUGGESTION_LABEL[item.kind]}] {item.concept}
                </span>
                {item.aiReason && ` — ${item.aiReason}`}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-caption text-ink-soft">
            AI가 문제를 만들 때 함께 제안한 개념이에요. 실제 학습 결과는 위의
            판단을 보세요.
          </p>
        </AiNote>
      )}
    </Card>
  );
}

function Breakdown({ result }: { result: QuizResult }) {
  const { byType, byConcept } = result.assessment;
  return (
    <Card
      as="section"
      aria-labelledby="quiz-breakdown"
      className="flex flex-col gap-5 p-6"
    >
      <h2 id="quiz-breakdown" className="text-lg font-extrabold text-heading">
        유형별·개념별 결과
      </h2>
      {byType.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-3">
          {byType.map((type) => (
            <li key={type.type} className="flex flex-col gap-1.5">
              <p className="text-body font-bold text-heading">
                {QUESTION_TYPE_LABELS[type.type]}{' '}
                <span className="font-medium text-ink-muted">
                  {type.questionCount}문제
                </span>
              </p>
              <ScoreBar value={percentOf(type.score, type.maxScore)} />
              <p className="text-caption text-ink-soft">
                {type.gradedCount > 0
                  ? `${type.score}/${type.maxScore}점 (채점 끝난 ${type.gradedCount}문제)`
                  : '채점이 끝난 문제가 없어요'}
              </p>
            </li>
          ))}
        </ul>
      )}
      {byConcept.length > 0 && (
        <ul className="flex flex-col gap-3">
          {byConcept.map((concept, index) => (
            <li key={concept.conceptId} className="flex flex-col gap-1.5">
              <p className="flex flex-wrap items-center justify-between gap-2 text-body">
                <span className="font-bold text-heading">{concept.name}</span>
                <span className="text-caption text-ink-soft">
                  {concept.questionCount}문제 · {concept.score}/
                  {concept.maxScore}점
                  {concept.pendingCount + concept.reviewCount > 0 &&
                    ` · 대기·확인 ${concept.pendingCount + concept.reviewCount}`}
                  {concept.weak && (
                    <span className="ml-2 font-bold text-weakness">
                      ! 보완 필요
                    </span>
                  )}
                </span>
              </p>
              <ScoreBar
                value={percentOf(concept.score, concept.maxScore)}
                level={concept.weak ? 'weakness' : 'normal'}
                delay={index * 0.05}
              />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

// 개념 확인 퀴즈 결과: 그 개념의 학습 상태(서버 코드 규칙)와 개념·커리큘럼으로 돌아가는 길
function CheckTopicResult({ quiz }: { quiz: Quiz }) {
  const topic = quiz.checkTopic!;
  const { data: studies } = useSuspenseQuery({
    queryKey: [...STUDY_QUERY_KEY, topic.materialId],
    queryFn: () => getConceptStudies(topic.materialId),
  });
  const study = studies.find((item) => item.conceptId === topic.topicId);
  return (
    <Card
      as="section"
      aria-label="개념 학습 상태"
      className="flex flex-col gap-3 p-5"
    >
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-extrabold text-heading">
          ‘{topic.name}’ 학습 상태
        </h2>
        {study && <StudyStatusBadge status={study.status} />}
      </div>
      <p className="text-body text-ink">
        {study
          ? study.reason
          : '이 개념을 자료에서 다시 뽑아 지금은 학습 상태를 찾을 수 없어요.'}
      </p>
      {study?.status === 'review_needed' && (
        <p className="text-body text-ink-soft">
          아래 문제별 결과에서 틀린 부분과 개선 방향을 확인하고, 개념 설명을
          다시 읽은 뒤 "다시 학습 완료"를 눌러 주세요.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button to={conceptStudyLink(topic.materialId, topic.topicId)}>
          {study?.status === 'review_needed'
            ? '개념 다시 학습하기'
            : '개념으로 돌아가기'}
        </Button>
        <Button
          variant="secondary"
          to={`/curriculum?courseId=${quiz.courseId}`}
        >
          커리큘럼으로 돌아가기
        </Button>
      </div>
    </Card>
  );
}

function QuizResultContent({ quizId }: { quizId: number }) {
  const queryClient = useQueryClient();
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
  const regrade = useMutation({
    mutationFn: (submissionId: number) => regradeQuiz(quizId, submissionId),
    onSuccess: (next) => {
      queryClient.setQueryData([...QUIZZES_QUERY_KEY, quizId, 'result'], next);
      void queryClient.invalidateQueries({
        queryKey: QUIZZES_QUERY_KEY,
        exact: true,
      });
    },
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

  const percent = percentOf(result.score, result.maxScore);
  const date = formatDate(result.submittedAt);
  const modeLabel = quizModeLabel(quiz.mode);

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Card as="section" aria-label="점수" className="flex flex-col gap-3 p-6">
        <h2 className="text-lg font-extrabold text-heading">
          {quiz.title}
          {modeLabel && (
            <span className="ml-2 text-body font-medium text-ink-muted">
              {modeLabel}
            </span>
          )}
        </h2>
        <p className="text-body text-ink">
          채점이 끝난 문제 점수{' '}
          <strong className="text-2xl font-extrabold text-primary">
            {result.score}/{result.maxScore}점
          </strong>{' '}
          ({percent}%) · {result.totalCount}문제 중 {result.correctCount}문제
          만점
        </p>
        <ScoreBar value={percent} />
        {(result.pendingCount > 0 || result.reviewCount > 0) && (
          <p className="text-body text-ink-soft">
            {result.pendingCount > 0 &&
              `AI 채점을 기다리는 답 ${result.pendingCount}개`}
            {result.pendingCount > 0 && result.reviewCount > 0 && ', '}
            {result.reviewCount > 0 &&
              `AI 판단이 엇갈려 확인이 필요한 답 ${result.reviewCount}개`}
            는 점수에 넣지 않았어요.
          </p>
        )}
        {result.pendingCount > 0 && (
          <div className="flex flex-col gap-2">
            <Button
              variant="secondary"
              className="self-start"
              disabled={regrade.isPending}
              onClick={() => regrade.mutate(result.submissionId)}
            >
              <RefreshCw size={16} aria-hidden="true" />
              {regrade.isPending
                ? '다시 채점하는 중…'
                : '대기 중인 답 다시 채점하기'}
            </Button>
            <ErrorText error={regrade.error} />
          </div>
        )}
        {date && (
          <p className="text-caption text-ink-muted">{date}에 푼 결과</p>
        )}
      </Card>

      {quiz.checkTopic && (
        <AsyncBoundary>
          <CheckTopicResult quiz={quiz} />
        </AsyncBoundary>
      )}
      <Assessment quiz={quiz} result={result} />
      <Breakdown result={result} />

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
          return (
            <QuestionResultCard
              key={question.id}
              index={index}
              question={question}
              item={item}
            />
          );
        })}
      </ol>

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
