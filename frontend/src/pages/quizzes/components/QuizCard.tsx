import { Sparkles } from 'lucide-react';
import { Button, Card, Chip } from '@/components';
import { quizModeLabel } from '@/constants';
import type { QuizSummary } from '@/types';

// 퀴즈 목록·자료 상세 퀴즈 탭이 함께 쓰는 한 줄 카드
function QuizCard({ quiz }: { quiz: QuizSummary }) {
  const solved = quiz.lastCorrectCount !== null;
  return (
    <Card
      as="li"
      className="flex flex-wrap items-center justify-between gap-4 p-5"
    >
      <div className="flex flex-col gap-1.5">
        <p className="font-bold text-heading">{quiz.title}</p>
        <p className="flex flex-wrap items-center gap-2 text-caption text-ink-muted">
          {quizModeLabel(quiz.mode) && (
            <Chip>
              {quiz.assessmentKind === 'pre'
                ? '사전평가'
                : quiz.assessmentKind === 'post'
                  ? '사후평가'
                  : quiz.mockExam
                    ? '모의시험'
                    : quizModeLabel(quiz.mode)}
            </Chip>
          )}
          <span>{quiz.questionCount}문제</span>
          {quiz.aiGenerated && (
            <span className="inline-flex items-center gap-1 text-ai">
              <Sparkles size={12} aria-hidden="true" />
              AI가 만든 문제
            </span>
          )}
          {solved ? (
            <Chip tone="primary">
              {quiz.lastScore !== null && quiz.lastMaxScore !== null
                ? `지난번 채점 끝난 문제 ${quiz.lastScore}/${quiz.lastMaxScore}점`
                : `지난번 ${quiz.lastCorrectCount}/${quiz.questionCount} 맞힘`}
              {!!quiz.lastPendingCount &&
                ` · 채점 대기 ${quiz.lastPendingCount}`}
              {!!quiz.lastReviewCount && ` · 확인 필요 ${quiz.lastReviewCount}`}
            </Chip>
          ) : (
            <span>아직 안 풀었어요</span>
          )}
        </p>
      </div>
      <div className="flex gap-2">
        {solved && (
          <Button to={`/quizzes/${quiz.id}/result`} variant="secondary">
            결과 보기
          </Button>
        )}
        <Button to={`/quizzes/${quiz.id}`}>
          {solved ? '다시 풀기' : '풀기'}
        </Button>
      </div>
    </Card>
  );
}

export default QuizCard;
