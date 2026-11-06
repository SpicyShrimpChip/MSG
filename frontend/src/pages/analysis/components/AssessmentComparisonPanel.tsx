import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useSuspenseQuery } from '@tanstack/react-query';
import {
  ArrowRight,
  CircleAlert,
  CircleCheck,
  ClipboardCheck,
  Info,
  Table2,
} from 'lucide-react';
import { ASSESSMENTS_QUERY_KEY, getAssessmentComparison } from '@/api';
import { Button, Card, Notice } from '@/components';
import { cn } from '@/utils';
import type { ConceptDeltaStatus } from '@/types';
import {
  comparisonRows as rows,
  deltaText,
  groupConcepts,
  scorePercent as percent,
  submitted,
} from '@/utils/assessment';

// 서버 상태(AssessmentService.status)를 글로. 색만으로 알리지 않도록 표에도 글자를 쓴다
const STATUS_LABEL: Record<ConceptDeltaStatus, string> = {
  improved: '올랐어요',
  declined: '내려갔어요',
  unchanged: '그대로예요',
  still_weak: '아직 부족해요',
  weak: '부족해요',
  ok: '기준 이상',
  not_taken: '채점된 답 없음',
};

// 막대 하나: 왼쪽(0) 기준, 데이터 끝만 둥글게. 값 글자는 막대 색이 아니라 글자 색으로
function Bar({
  value,
  tone,
  text,
}: {
  value: number | null;
  tone: 'pre' | 'post';
  text: string;
}) {
  return (
    <div className="flex items-center gap-2">
      <span className="relative block h-2.5 flex-1">
        {value !== null && (
          <span
            className={cn(
              'absolute inset-y-0 left-0 rounded-r',
              tone === 'pre' ? 'bg-chart-pre' : 'bg-chart-post',
            )}
            style={{ width: `${Math.max(value, 1)}%` }}
          />
        )}
      </span>
      <span className="w-28 shrink-0 text-right text-caption text-ink-soft tabular-nums">
        {value === null ? text : `${value}% · ${text}`}
      </span>
    </div>
  );
}

function Legend() {
  return (
    <div className="flex flex-wrap items-center gap-4 text-caption text-ink-soft">
      <span className="flex items-center gap-1.5">
        <span
          className="block size-2.5 rounded-sm bg-chart-pre"
          aria-hidden="true"
        />
        사전평가
      </span>
      <span className="flex items-center gap-1.5">
        <span
          className="block size-2.5 rounded-sm bg-chart-post"
          aria-hidden="true"
        />
        사후평가
      </span>
    </div>
  );
}

// 대시보드 맨 위: 최신 사전평가 vs 그 사후평가 (전체 → 개념별). 점수는 서버가 채점이 끝난 답만 더한 값이다
function AssessmentComparisonPanel({ courseId }: { courseId: number }) {
  const { data } = useSuspenseQuery({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'comparison', courseId],
    queryFn: () => getAssessmentComparison({ courseId }),
  });
  const [hovered, setHovered] = useState<string | null>(null);
  const [asTable, setAsTable] = useState(false);

  if (!data.pre) {
    return (
      <Card
        as="section"
        aria-labelledby="assessment-title"
        className="flex flex-col gap-3 p-6"
      >
        <h2
          id="assessment-title"
          className="text-lg font-extrabold text-heading"
        >
          사전·사후평가 비교
        </h2>
        <Notice icon={ClipboardCheck} iconClassName="text-primary">
          아직 이 과목의 사전평가가 없어요. 학습자료로 사전평가를 보면 어떤
          개념이 부족한지 먼저 알 수 있고, 공부한 뒤 사후평가와 비교해 드려요.
        </Notice>
        <Button to="/assessments" className="self-start">
          사전평가 보러 가기
        </Button>
      </Card>
    );
  }

  const preRate = submitted(data.pre)
    ? percent(data.pre.score, data.pre.maxScore)
    : null;
  const postRate = submitted(data.post)
    ? percent(data.post!.score, data.post!.maxScore)
    : null;
  // 변화량은 서버가 계산한 값 (채점이 끝난 답만)
  const delta = data.overallDelta;
  const all = rows(data);
  const groups = groupConcepts(data);
  const postDone = submitted(data.post);
  const reviewLink = (name: string) =>
    `/quizzes/new?courseId=${courseId}&mode=self_development&concept=${encodeURIComponent(name)}`;
  const focus = all.find((row) => row.key === hovered);
  const unsettled =
    (data.pre.pendingCount ?? 0) +
    (data.pre.reviewCount ?? 0) +
    (data.post?.pendingCount ?? 0) +
    (data.post?.reviewCount ?? 0);

  return (
    <Card
      as="section"
      aria-labelledby="assessment-title"
      className="flex flex-col gap-5 p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2
            id="assessment-title"
            className="text-lg font-extrabold text-heading"
          >
            사전·사후평가 비교
          </h2>
          <p className="text-caption text-ink-muted">
            {data.material ? `${data.material.title} · ` : ''}
            {data.basis}
          </p>
        </div>
        <Legend />
      </div>

      {/* 한눈에: 전체 점수 사전 → 사후 */}
      <div
        className="flex flex-wrap items-end gap-x-6 gap-y-2"
        aria-live="polite"
      >
        <p className="flex flex-col">
          <span className="text-caption text-ink-soft">사전평가</span>
          <span className="text-2xl font-extrabold text-heading tabular-nums">
            {preRate === null ? '미응시' : `${preRate}%`}
          </span>
        </p>
        <ArrowRight
          size={20}
          className="mb-1.5 text-ink-muted"
          aria-hidden="true"
        />
        <p className="flex flex-col">
          <span className="text-caption text-ink-soft">사후평가</span>
          <span className="text-2xl font-extrabold text-heading tabular-nums">
            {postRate === null ? '미응시' : `${postRate}%`}
          </span>
        </p>
        {delta !== null && (
          <p className="mb-1 text-body font-bold text-heading">
            {delta > 0
              ? `${delta}%p 올랐어요`
              : delta < 0
                ? `${-delta}%p 내려갔어요`
                : '변화 없음'}
          </p>
        )}
      </div>

      {!submitted(data.pre) && (
        <Notice icon={ClipboardCheck} size="caption">
          사전평가를 만들었지만 아직 풀지 않았어요.{' '}
          <Link
            to={`/quizzes/${data.pre.quizId}`}
            className="font-bold text-primary underline-offset-4 hover:underline"
          >
            사전평가 풀기
          </Link>
        </Notice>
      )}
      {submitted(data.pre) && !submitted(data.post) && (
        <Notice icon={ClipboardCheck} size="caption">
          {data.post
            ? '사후평가를 만들었지만 아직 풀지 않았어요. 풀면 여기에서 비교해 드려요.'
            : '아직 사후평가를 보지 않았어요. 공부한 뒤 같은 개념으로 사후평가를 보면 향상 정도를 비교해 드려요.'}{' '}
          <Link
            to={data.post ? `/quizzes/${data.post.quizId}` : '/assessments'}
            className="font-bold text-primary underline-offset-4 hover:underline"
          >
            {data.post ? '사후평가 풀기' : '사후평가 보러 가기'}
          </Link>
        </Notice>
      )}

      {asTable ? (
        <table className="w-full text-left text-body">
          <caption className="sr-only">개념별 사전·사후평가 점수</caption>
          <thead className="bg-surface-muted text-caption text-ink-soft">
            <tr>
              <th className="px-3 py-2 font-medium">개념</th>
              <th className="px-3 py-2 font-medium">사전평가</th>
              <th className="px-3 py-2 font-medium">사후평가</th>
              <th className="px-3 py-2 font-medium">변화</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {all.map((row) => (
              <tr key={row.key}>
                <td className="px-3 py-2 font-bold text-heading">
                  {row.label}
                </td>
                <td className="px-3 py-2 tabular-nums">
                  {row.pre === null
                    ? row.preText
                    : `${row.pre}% (${row.preText})`}
                </td>
                <td className="px-3 py-2 tabular-nums">
                  {row.post === null
                    ? row.postText
                    : `${row.post}% (${row.postText})`}
                </td>
                <td className="px-3 py-2 tabular-nums">
                  {row.delta === null ? '-' : deltaText(row.delta)}
                  {row.status && ` · ${STATUS_LABEL[row.status]}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="flex flex-col gap-1">
          {/* 마우스를 올리거나 누르거나(포커스) 키보드로 고른 줄의 값을 한 줄로 */}
          <p className="min-h-5 text-caption text-ink-soft" aria-live="polite">
            {focus
              ? `${focus.label}: 사전 ${focus.pre === null ? focus.preText : `${focus.pre}% (${focus.preText})`} → 사후 ${focus.post === null ? focus.postText : `${focus.post}% (${focus.postText})`}${focus.delta === null ? '' : ` · ${deltaText(focus.delta)}`}`
              : '줄을 누르면 점수를 자세히 보여 줘요'}
          </p>
          <ul className="flex flex-col">
            {all.map((row) => (
              <li
                key={row.key}
                tabIndex={0}
                onMouseEnter={() => setHovered(row.key)}
                onMouseLeave={() => setHovered(null)}
                onFocus={() => setHovered(row.key)}
                onBlur={() => setHovered(null)}
                className={cn(
                  'grid grid-cols-[6rem_1fr] items-center gap-3 rounded-lg px-2 py-2 outline-none sm:grid-cols-[8rem_1fr]',
                  hovered === row.key && 'bg-surface-muted',
                  row.key === 'overall' && 'border-b border-line pb-3',
                )}
              >
                <span className="flex min-w-0 flex-col">
                  <span
                    className={cn(
                      'truncate text-body text-heading',
                      row.key === 'overall' && 'font-extrabold',
                    )}
                    title={row.label}
                  >
                    {row.label}
                  </span>
                  {row.delta !== null && (
                    <span className="text-caption font-bold text-ink-soft tabular-nums">
                      {deltaText(row.delta)}
                    </span>
                  )}
                </span>
                <div className="flex flex-col gap-0.5">
                  <Bar value={row.pre} tone="pre" text={row.preText} />
                  <Bar value={row.post} tone="post" text={row.postText} />
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {(groups.improved.length > 0 || groups.needsReview.length > 0) && (
        <div className="grid gap-3 md:grid-cols-2">
          {postDone && (
            <div className="flex flex-col gap-2 rounded-xl border border-line p-4">
              <h3 className="flex items-center gap-1.5 text-body font-bold text-heading">
                <CircleCheck
                  size={16}
                  className="text-strength"
                  aria-hidden="true"
                />
                오른 개념 {groups.improved.length}개
              </h3>
              {groups.improved.length === 0 ? (
                <p className="text-caption text-ink-soft">
                  아직 오른 개념이 없어요.
                </p>
              ) : (
                <ul className="flex flex-col gap-1 text-body">
                  {groups.improved.map((concept) => (
                    <li key={concept.conceptId} className="tabular-nums">
                      {concept.name}{' '}
                      <span className="text-ink-soft">
                        {deltaText(concept.delta)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <div className="flex flex-col gap-2 rounded-xl border border-line p-4">
            <h3 className="flex items-center gap-1.5 text-body font-bold text-heading">
              <CircleAlert
                size={16}
                className="text-weakness"
                aria-hidden="true"
              />
              {postDone ? '아직 부족한 개념' : '사전평가에서 부족한 개념'}{' '}
              {groups.needsReview.length}개
            </h3>
            {groups.needsReview.length === 0 ? (
              <p className="text-caption text-ink-soft">
                기준 미만인 개념이 없어요.
              </p>
            ) : (
              <ul className="flex flex-col gap-1.5 text-body">
                {groups.needsReview.map((concept) => (
                  <li
                    key={concept.conceptId}
                    className="flex flex-wrap items-center gap-x-2"
                  >
                    <span>{concept.name}</span>
                    <span className="text-caption text-ink-soft tabular-nums">
                      {STATUS_LABEL[concept.status]}
                      {concept.delta !== null &&
                        ` (${deltaText(concept.delta)})`}
                    </span>
                    <Link
                      to={reviewLink(concept.name)}
                      className="text-caption font-bold text-primary underline-offset-4 hover:underline"
                    >
                      복습 퀴즈 만들기
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            <p className="text-caption text-ink-muted">
              정답률이 기준보다 낮은 개념이에요(서버 규칙). 복습한 뒤 사후평가를
              다시 보면 비교가 새로 바뀌어요.
            </p>
          </div>
        </div>
      )}

      {data.comparability && data.comparability.notes.length > 0 && (
        <Notice icon={Info} size="caption">
          <span className="font-bold">비교할 때 알아 둘 점</span>
          <ul className="mt-1 list-disc pl-4">
            {data.comparability.notes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </Notice>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-caption text-ink-muted">
          채점이 끝난 답만 점수에 넣어요(채점 대기는 틀린 답으로 세지 않아요).
          숙련도 점수가 아니에요.
          {unsettled > 0 &&
            ` 채점 대기·확인 필요 답 ${unsettled}개는 빠져 있어요.`}
        </p>
        <button
          type="button"
          onClick={() => setAsTable((value) => !value)}
          className="flex items-center gap-1 text-caption font-bold text-primary underline-offset-4 hover:underline"
        >
          <Table2 size={14} aria-hidden="true" />
          {asTable ? '그래프로 보기' : '표로 보기'}
        </button>
      </div>
    </Card>
  );
}

export default AssessmentComparisonPanel;
