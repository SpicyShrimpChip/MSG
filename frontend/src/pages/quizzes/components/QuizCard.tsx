import { Link } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { Button, LIST_ROW_CLASS } from '@/components';
import { quizKindLabel } from '@/constants';
import type { QuizSummary } from '@/types';

// 퀴즈 목록·자료 상세 퀴즈 탭이 함께 쓰는 한 줄 (LIST_PANEL_CLASS 목록 안에 놓인다)
function QuizCard({ quiz }: { quiz: QuizSummary }) {
  const solved = quiz.lastCorrectCount !== null;
  const kindLabel = quizKindLabel(quiz);
  return (
    <li className={LIST_ROW_CLASS}>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="font-semibold text-heading">{quiz.title}</p>
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-caption text-ink-muted">
          {kindLabel && (
            <>
              <span>{kindLabel}</span>
              <span aria-hidden="true">·</span>
            </>
          )}
          <span>{quiz.questionCount}문제</span>
          {quiz.aiGenerated && (
            <>
              <span aria-hidden="true">·</span>
              <span className="inline-flex items-center gap-1 text-ai">
                <Sparkles size={12} aria-hidden="true" />
                AI가 만든 문제
              </span>
            </>
          )}
          <span aria-hidden="true">·</span>
          {solved ? (
            <span className="text-ink-soft">
              {quiz.lastScore !== null && quiz.lastMaxScore !== null
                ? `지난번 채점 끝난 문제 ${quiz.lastScore}/${quiz.lastMaxScore}점`
                : `지난번 ${quiz.lastCorrectCount}/${quiz.questionCount} 맞힘`}
              {!!quiz.lastPendingCount &&
                ` · 채점 대기 ${quiz.lastPendingCount}`}
              {!!quiz.lastReviewCount && ` · 확인 필요 ${quiz.lastReviewCount}`}
            </span>
          ) : (
            <span>아직 안 풀었어요</span>
          )}
        </p>
      </div>
      {/* 아직 안 푼 퀴즈만 주 버튼으로 둬서 파란 버튼이 줄마다 반복되지 않게 한다 */}
      <div className="flex items-center gap-4">
        {solved && (
          <Link
            to={`/quizzes/${quiz.id}/result`}
            className="text-body font-semibold text-ink-soft underline-offset-4 hover:text-heading hover:underline"
          >
            결과 보기
          </Link>
        )}
        <Button
          to={`/quizzes/${quiz.id}`}
          variant={solved ? 'secondary' : 'primary'}
        >
          {solved ? '다시 풀기' : '풀기'}
        </Button>
      </div>
    </li>
  );
}

export default QuizCard;
