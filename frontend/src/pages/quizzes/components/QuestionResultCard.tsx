import {
  CircleAlert,
  CircleCheck,
  CircleHelp,
  CircleX,
  Clock,
  type LucideIcon,
} from 'lucide-react';
import { AiNote, Card, Chip, Notice } from '@/components';
import { QUESTION_TYPE_LABELS } from '@/constants';
import type { QuizQuestion, QuizResultItem, RubricLevel } from '@/types';
import { cn } from '@/utils';

const LEVEL_LABEL: Record<RubricLevel, string> = {
  met: '충족',
  partial: '부분 충족',
  not_met: '미충족',
};

const METHOD_LABEL = {
  code: '서버 코드 채점',
  match: '허용 답 일치 (서버 코드)',
  ai: 'AI 판단 + 서버 코드 합산',
} as const;

// 문제 하나의 채점 상태 표시. 색만으로 알리지 않도록 아이콘·글자를 함께 쓴다
function statusBadge(item: QuizResultItem): {
  label: string;
  icon: LucideIcon;
  className: string;
} {
  if (item.gradingStatus === 'pending')
    return {
      label: '채점 대기',
      icon: Clock,
      className: 'bg-surface-muted text-ink-soft',
    };
  if (item.gradingStatus === 'needs_review')
    return {
      label: '확인 필요',
      icon: CircleHelp,
      className: 'bg-surface-muted text-ink-soft',
    };
  if (item.correct)
    return {
      label: item.type === 'essay' ? '만점' : '맞힘',
      icon: CircleCheck,
      className: 'bg-strength-soft text-strength',
    };
  if (item.score > 0)
    return {
      label: '부분 점수',
      icon: CircleAlert,
      className: 'bg-weakness-soft text-weakness',
    };
  return {
    label: '틀림',
    icon: CircleX,
    className: 'bg-weakness-soft text-weakness',
  };
}

interface QuestionResultCardProps {
  index: number;
  question: QuizQuestion;
  item: QuizResultItem;
}

// 문제별 결과. 점수·채점 상태는 서버 코드가 정한 값(파랑), ai로 시작하는 글만 보라색 AI 상자
function QuestionResultCard({
  index,
  question,
  item,
}: QuestionResultCardProps) {
  const badge = statusBadge(item);
  const Icon = badge.icon;
  return (
    <Card as="li" className="flex flex-col gap-3 p-5">
      <div className="flex items-start justify-between gap-3">
        <p className="font-bold text-heading">
          <span className="mr-2 text-primary">Q{index + 1}.</span>
          <span className="mr-2 text-caption font-medium text-ink-muted">
            [{QUESTION_TYPE_LABELS[item.type]}]
          </span>
          {question.text}
        </p>
        <span
          className={cn(
            'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-caption font-medium',
            badge.className,
          )}
        >
          <Icon size={14} aria-hidden="true" />
          {badge.label}
          {item.gradingStatus === 'graded' &&
            item.maxScore > 1 &&
            ` ${item.score}/${item.maxScore}점`}
        </span>
      </div>

      {question.scenario && (
        <p className="rounded-xl bg-surface-muted px-4 py-3 text-caption leading-relaxed text-ink-soft">
          <span className="font-bold text-heading">업무 상황 </span>
          {question.scenario}
          {question.role && ` (내 역할: ${question.role})`}
        </p>
      )}

      <dl className="grid gap-1 text-body sm:grid-cols-[auto_1fr] sm:gap-x-4">
        <dt className="text-ink-muted">내 답</dt>
        <dd className="whitespace-pre-line text-ink">
          {item.type === 'multiple_choice'
            ? (question.choices[item.choiceIndex] ?? '고르지 않음')
            : (item.textAnswer ?? '쓰지 않음')}
        </dd>
        {item.type === 'multiple_choice' &&
          !item.correct &&
          item.correctChoiceIndex !== null && (
            <>
              <dt className="text-ink-muted">정답</dt>
              <dd className="font-medium text-heading">
                {question.choices[item.correctChoiceIndex]}
              </dd>
            </>
          )}
        {item.type === 'short_answer' && item.acceptedAnswers.length > 0 && (
          <>
            <dt className="text-ink-muted">인정하는 답</dt>
            <dd className="font-medium text-heading">
              {item.acceptedAnswers.join(', ')}
            </dd>
          </>
        )}
        <dt className="text-ink-muted">채점</dt>
        <dd className="text-ink-soft">
          {METHOD_LABEL[item.gradingMethod]}
          {item.gradingMethod === 'ai' &&
            item.aiCalledCount > 0 &&
            (item.aiJudgedCount === 1
              ? ' · AI 1개만 판단 (다수결 아님)'
              : ` · 모델 ${item.aiCalledCount}개 중 ${item.aiJudgedCount}개가 판단`)}
        </dd>
      </dl>

      {item.gradingMessage && (
        <Notice icon={CircleHelp} size="caption">
          {item.gradingMessage}
        </Notice>
      )}

      {item.rubric.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[480px] text-left text-caption">
            <caption className="mb-2 text-left text-body font-bold text-heading">
              평가 기준별 결과
            </caption>
            <thead className="bg-surface-muted text-ink-soft">
              <tr>
                <th className="px-3 py-2 font-medium">기준</th>
                <th className="px-3 py-2 font-medium">
                  {item.aiJudgedCount === 1
                    ? '판단 (AI 1개)'
                    : '판단 (다수 의견)'}
                </th>
                <th className="px-3 py-2 font-medium">점수</th>
                <th className="px-3 py-2 font-medium">모델 의견</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {item.rubric.map((row) => (
                <tr key={row.criterion}>
                  <td className="px-3 py-2 text-heading">{row.criterion}</td>
                  <td className="px-3 py-2 text-ink">
                    {row.level ? LEVEL_LABEL[row.level] : '의견 갈림'}
                  </td>
                  <td className="px-3 py-2 font-bold text-primary">
                    {row.earned === null ? '-' : row.earned}/{row.points}
                  </td>
                  <td className="px-3 py-2 text-ink-soft">
                    {row.judgedCount === 1
                      ? 'AI 1개 판단'
                      : row.judgedCount > 0
                        ? `${row.judgedCount}개 중 ${row.agreeCount}개 같음`
                        : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {item.rubric.some((row) => row.aiReason) && (
            <div className="mt-3">
              <AiNote title="기준별 판단 근거">
                <ul className="flex flex-col gap-1">
                  {item.rubric
                    .filter((row) => row.aiReason)
                    .map((row) => (
                      <li key={row.criterion}>
                        <span className="font-bold">{row.criterion}: </span>
                        {row.aiReason}
                      </li>
                    ))}
                </ul>
              </AiNote>
            </div>
          )}
        </div>
      )}

      {item.aiFeedback && (
        <AiNote title="답변 피드백">
          <dl className="grid gap-1 sm:grid-cols-[auto_1fr] sm:gap-x-4">
            {(
              [
                ['잘한 부분', item.aiFeedback.strengths],
                ['빠뜨린 부분', item.aiFeedback.missing],
                ['개선할 점', item.aiFeedback.improvements],
                ['실무에서 더 고려할 점', item.aiFeedback.practical],
              ] as const
            )
              .filter(([, text]) => text)
              .map(([label, text]) => (
                <div key={label} className="contents">
                  <dt className="font-bold">{label}</dt>
                  <dd>{text}</dd>
                </div>
              ))}
          </dl>
        </AiNote>
      )}

      {item.aiModelAnswer && (
        <AiNote title="참고 모범 답안">
          <p className="whitespace-pre-line">{item.aiModelAnswer}</p>
          <p className="mt-2 text-caption text-ink-soft">
            여러 타당한 답이 있을 수 있어요. 표현이 달라도 평가 기준을 채우면
            점수를 받아요.
          </p>
        </AiNote>
      )}

      {item.concepts.length > 0 && (
        <p className="flex flex-wrap items-center gap-1.5 text-caption text-ink-muted">
          관련 개념
          {item.concepts.map((concept) => (
            <Chip key={concept.conceptId}>{concept.name}</Chip>
          ))}
        </p>
      )}
      {item.aiExplanation && <AiNote title="해설">{item.aiExplanation}</AiNote>}
    </Card>
  );
}

export default QuestionResultCard;
