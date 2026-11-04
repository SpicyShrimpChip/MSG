import { describe, expect, it } from 'vitest';
import type { QuizQuestion } from '@/types';
import { answeredCount, toAnswers } from './quizDraft';

const question = (id: number, type: QuizQuestion['type']): QuizQuestion => ({
  id,
  type,
  text: '문제',
  choices: type === 'multiple_choice' ? ['A', 'B'] : [],
  scenario: null,
  role: null,
  guide: null,
  criteria: [],
  maxScore: 1,
});

const questions = [
  question(1, 'multiple_choice'),
  question(2, 'short_answer'),
  question(3, 'essay'),
];

describe('quizDraft', () => {
  it('객관식은 보기 번호, 주관식·서술형은 앞뒤 공백을 뺀 글로 보낸다', () => {
    expect(
      toAnswers(questions, {
        choices: { 1: 0 },
        texts: { 2: ' PAT ', 3: '설명' },
      }),
    ).toEqual([
      { questionId: 1, choiceIndex: 0 },
      { questionId: 2, text: 'PAT' },
      { questionId: 3, text: '설명' },
    ]);
  });

  it('빈 글은 답하지 않은 것으로 보고 보내지 않는다', () => {
    const draft = { choices: {}, texts: { 2: '   ', 3: '' } };
    expect(toAnswers(questions, draft)).toEqual([]);
    expect(answeredCount(questions, draft)).toBe(0);
  });

  it('객관식 0번 보기도 답한 것으로 센다', () => {
    expect(answeredCount(questions, { choices: { 1: 0 }, texts: {} })).toBe(1);
  });
});
