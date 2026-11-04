import type { QuizAnswer, QuizQuestion } from '@/types';

// 풀던 답 (문제 id → 고른 보기 / 쓴 글). 이 탭의 sessionStorage에만 둔다:
// 새로고침해도 남고, 탭을 닫거나 제출하면 사라진다. 서버에는 제출할 때만 보낸다
export interface Draft {
  choices: Record<number, number>;
  texts: Record<number, string>;
}

const key = (quizId: number) => `ai-lms-quiz-draft:${quizId}`;
const EMPTY: Draft = { choices: {}, texts: {} };

export function loadDraft(quizId: number): Draft {
  try {
    const saved = sessionStorage.getItem(key(quizId));
    if (!saved) return EMPTY;
    const parsed = JSON.parse(saved) as Partial<Draft>;
    return { choices: parsed.choices ?? {}, texts: parsed.texts ?? {} };
  } catch {
    return EMPTY;
  }
}

export function saveDraft(quizId: number, draft: Draft): void {
  try {
    sessionStorage.setItem(key(quizId), JSON.stringify(draft));
  } catch {
    // 저장소를 쓸 수 없으면 이 화면에서만 유지한다
  }
}

export function clearDraft(quizId: number): void {
  try {
    sessionStorage.removeItem(key(quizId));
  } catch {
    // 무시
  }
}

const hasText = (text: string | undefined) => !!text && text.trim() !== '';

export function answeredCount(questions: QuizQuestion[], draft: Draft) {
  return questions.filter((q) =>
    q.type === 'multiple_choice'
      ? draft.choices[q.id] !== undefined
      : hasText(draft.texts[q.id]),
  ).length;
}

// 답한 문제만 보낸다 (빈 문제는 서버가 틀린 것으로 채점한다). 객관식은 choiceIndex, 나머지는 text
export function toAnswers(
  questions: QuizQuestion[],
  draft: Draft,
): QuizAnswer[] {
  const answers: QuizAnswer[] = [];
  for (const question of questions) {
    if (question.type === 'multiple_choice') {
      const choiceIndex = draft.choices[question.id];
      if (choiceIndex !== undefined)
        answers.push({ questionId: question.id, choiceIndex });
    } else if (hasText(draft.texts[question.id])) {
      answers.push({
        questionId: question.id,
        text: draft.texts[question.id].trim(),
      });
    }
  }
  return answers;
}
