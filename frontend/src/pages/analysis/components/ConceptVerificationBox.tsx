import { ShieldCheck } from 'lucide-react';
import { Button, VerificationBadge } from '@/components';
import type { ConceptResult } from '@/types';
import { conceptQuizLink } from '@/utils';

const COMPARISON_LABEL = {
  consistent: 'AI 판단과 확인 퀴즈 결과가 같은 방향',
  contradicts: 'AI 판단과 확인 퀴즈 결과가 다름',
  not_compared: '퀴즈 결과와 방향을 비교하지 않음',
  no_quiz: '확인 퀴즈 결과 없음',
} as const;

// 개념 하나의 검증 상태 (서버 코드 규칙). AI 판단(추정)과 실제 퀴즈 결과(평가)를 함께 보고,
// 검증 완료가 아니면 확인 퀴즈로 이어 준다. 모델 의견 일치는 정확도가 아니라서 % 숫자를 보여 주지 않는다
function ConceptVerificationBox({
  courseId,
  concept,
}: {
  courseId: number;
  concept: ConceptResult;
}) {
  const verification = concept.verification;
  if (!verification) return null;
  const misconceptions = concept.misconception?.flaggedCount ?? 0;
  return (
    <section
      aria-label="판단 검증"
      className="flex flex-col gap-3 rounded-xl border border-line p-4"
    >
      <div className="flex flex-wrap items-center gap-2">
        <ShieldCheck size={18} className="text-primary" aria-hidden="true" />
        <h3 className="font-semibold text-heading">판단 검증</h3>
        <VerificationBadge status={verification.status} />
      </div>
      <ul className="flex list-disc flex-col gap-1 pl-5 text-caption text-ink-soft">
        {verification.reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
      <p className="text-caption text-ink-muted">
        {COMPARISON_LABEL[verification.quizComparison]}
        {misconceptions > 0 &&
          ` · 오개념 후보: 모델 ${concept.misconception?.judgedCount ?? 0}개 중 ${misconceptions}개가 지적 (확정 아님)`}
      </p>
      {verification.recommendCheckQuiz && (
        <div>
          <Button
            variant="secondary"
            to={conceptQuizLink(courseId, concept.concept)}
          >
            확인 퀴즈로 실제 이해 확인하기
          </Button>
        </div>
      )}
    </section>
  );
}

export default ConceptVerificationBox;
