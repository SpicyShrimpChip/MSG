import { Link } from 'react-router-dom';
import type { ConceptMastery } from '@/types';
import { UNDERSTANDING_LABEL } from '@/utils';
import MasteryMeter from './MasteryMeter';

interface MasteryEvidenceProps {
  courseId: number;
  concept: string;
  mastery: ConceptMastery;
}

// 고른 개념의 숙련도 단계와 반영한 근거. 서버가 규칙(app.mastery)으로 계산한 값을 그대로 보여 준다
function MasteryEvidence({ courseId, concept, mastery }: MasteryEvidenceProps) {
  const insufficient = mastery.level === 'insufficient_evidence';
  const hasQuiz = mastery.quizGradedCount > 0;
  const hasConversation =
    mastery.conversationCount > 0 && mastery.conversationStatus !== null;

  return (
    <div className="flex flex-col gap-3 border-b border-line pb-5">
      <div className="flex flex-col gap-1">
        <h3 className="font-extrabold text-heading">숙련도</h3>
        <p className="text-caption text-ink-muted">
          AI가 아니라 정해진 규칙으로 계산했어요.
        </p>
      </div>

      <div className="rounded-xl bg-primary-soft px-4 py-3.5">
        <MasteryMeter level={mastery.level} size="lg" />
      </div>

      <div className="flex flex-col gap-2">
        <p className="text-caption font-bold text-ink-soft">반영한 근거</p>
        {hasQuiz || hasConversation ? (
          <ul className="flex flex-col gap-1.5">
            {hasQuiz && (
              <li className="flex flex-wrap justify-between gap-x-3 gap-y-1 rounded-lg bg-surface-muted px-3 py-2.5 text-body">
                <span className="font-bold text-heading">
                  퀴즈 채점 {mastery.quizGradedCount}문제
                </span>
                {mastery.quizRate !== null && (
                  <span className="text-ink-soft">
                    얻은 점수 {Math.round(mastery.quizRate * 100)}%
                  </span>
                )}
              </li>
            )}
            {hasConversation && mastery.conversationStatus && (
              <li className="flex flex-wrap justify-between gap-x-3 gap-y-1 rounded-lg bg-surface-muted px-3 py-2.5 text-body">
                <span className="font-bold text-heading">
                  AI 대화 {mastery.conversationCount}개
                </span>
                <span className="text-ink-soft">
                  모델 판단 '{UNDERSTANDING_LABEL[mastery.conversationStatus]}'
                </span>
              </li>
            )}
          </ul>
        ) : (
          <p className="text-body text-ink-muted">
            아직 반영한 퀴즈·대화가 없어요.
          </p>
        )}
      </div>

      {insufficient && (
        <p className="rounded-lg border border-dashed border-line-strong bg-surface-muted px-3 py-2.5 text-body leading-relaxed text-ink-soft">
          근거 부족은 모른다는 뜻이 아니라 판단할 기록이 아직 적다는 뜻이에요.
          {mastery.quizNeeded !== null && (
            <>
              {' '}
              <Link
                to={`/quizzes/new?courseId=${courseId}&mode=self_development&concept=${encodeURIComponent(concept)}`}
                className="font-bold text-primary underline-offset-4 hover:underline"
              >
                퀴즈 {mastery.quizNeeded}문제 더 풀면 단계가 나와요
              </Link>
            </>
          )}
        </p>
      )}

      <p className="text-caption text-ink-muted">
        퀴즈 결과를 대화보다 무겁게 반영해요. 반영 비율과 기준은 초기값이라
        사용자 테스트 뒤 조정해요. (산식 버전 {mastery.formulaVersion})
      </p>
    </div>
  );
}

export default MasteryEvidence;
