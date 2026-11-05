import type { MasteryRecord, MasteryStage } from '@/types';
import { MASTERY_LABEL, UNDERSTANDING_LABEL } from './analysis';

// 숙련도 기록(변화 추이)을 화면 문장으로 바꾼다. 값은 서버 규칙 계산 결과 그대로 쓰고 새로 계산하지 않는다

// 단계 높낮이. 근거 부족은 높낮이에 넣지 않는다(이해 부족과 다르다)
const STAGE_RANK: Partial<Record<MasteryStage, number>> = {
  needs_work: 1,
  normal: 2,
  strong: 3,
};

// 11/5 같은 짧은 날짜 (사용자 기기 시간대)
export function shortDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

export interface MasteryChange {
  date: string;
  stage: MasteryStage;
  change: string;
  reasons: string[];
}

// 기록 한 줄을 바로 앞 기록과 비교해 무엇이 바뀌었는지 쓴다
export function describeMasteryChange(
  previous: MasteryRecord | null,
  current: MasteryRecord,
): MasteryChange {
  const label = MASTERY_LABEL[current.level];
  let change: string;
  if (!previous) change = `첫 기록 · ${label}`;
  else if (previous.level !== current.level) {
    change = `${MASTERY_LABEL[previous.level]} → ${label}`;
  } else change = `${label} 그대로`;

  const reasons: string[] = [];
  if (current.quizGradedCount > 0) {
    const added = current.quizGradedCount - (previous?.quizGradedCount ?? 0);
    const rate =
      current.quizRate === null
        ? null
        : `얻은 점수 ${Math.round(current.quizRate * 100)}%`;
    if (previous && added > 0) {
      const detail = [`지금까지 ${current.quizGradedCount}문제`, rate]
        .filter(Boolean)
        .join(', ');
      reasons.push(`퀴즈 ${added}문제 더 채점(${detail})`);
    } else {
      reasons.push(
        `퀴즈 ${current.quizGradedCount}문제${rate ? `(${rate})` : ''}`,
      );
    }
  }
  if (current.conversationCount > 0 && current.conversationStatus) {
    reasons.push(
      `대화 ${current.conversationCount}개 모델 판단 “${UNDERSTANDING_LABEL[current.conversationStatus]}”`,
    );
  }
  if (reasons.length === 0) reasons.push('반영한 퀴즈·대화 없음');
  if (previous && previous.formulaVersion !== current.formulaVersion) {
    reasons.push(
      `산식 v${previous.formulaVersion} → v${current.formulaVersion} (앞 기록과 바로 비교하기 어려워요)`,
    );
  } else {
    reasons.push(`산식 v${current.formulaVersion}`);
  }

  return {
    date: shortDate(current.recordedAt),
    stage: current.level,
    change,
    reasons,
  };
}

// 바로 앞 기록보다 단계가 오르거나 내려갔으면 한 줄로 알린다. 근거 부족이 끼거나 산식이 바뀌었거나 같으면 null
export function describeLevelTrend(history: MasteryRecord[]): string | null {
  if (history.length < 2) return null;
  const previous = history[history.length - 2];
  const current = history[history.length - 1];
  if (previous.formulaVersion !== current.formulaVersion) return null;
  const from = STAGE_RANK[previous.level];
  const to = STAGE_RANK[current.level];
  if (from === undefined || to === undefined || from === to) return null;
  const steps = Math.abs(to - from);
  const word = steps === 1 ? '한' : '두';
  return `${shortDate(previous.recordedAt)}보다 ${word} 단계 ${to > from ? '올랐어요' : '내려갔어요'}`;
}
