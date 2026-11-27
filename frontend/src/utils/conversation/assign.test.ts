import { describe, expect, it } from 'vitest';
import type { Course } from '@/types';
import { assignToCourses } from './assign';
import { mergeParseResults } from './merge';
import type { ParsedConversation } from './types';

function conversation(
  id: string,
  text: string,
  createdAt?: string,
): ParsedConversation {
  return {
    id,
    source: 'claude',
    title: `대화 ${id}`,
    createdAt,
    messages: [{ role: 'user', text }],
  };
}

const security: Course = {
  id: 1,
  name: '정보보호개론',
  semester: '2026-2',
  keywords: ['암호화', '해시', 'TLS'],
};
const os: Course = {
  id: 2,
  name: '운영체제',
  semester: '2026-2',
  keywords: ['프로세스', '교착 상태', '해시'],
};

describe('assignToCourses', () => {
  it('키워드가 걸린 과목으로 대화를 나눈다', () => {
    const { assigned, unmatchedCount } = assignToCourses(
      [
        conversation('a', '대칭키 암호화가 뭐야?'),
        conversation('b', '프로세스와 스레드 차이'),
        conversation('c', '오늘 저녁 메뉴 추천'),
      ],
      [security, os],
    );
    expect(assigned.map((item) => item.matches[0].courseId)).toEqual([1, 2]);
    expect(unmatchedCount).toBe(1);
  });

  it('여러 과목에 걸리면 키워드가 더 많이 걸린 과목이 먼저다', () => {
    const { assigned } = assignToCourses(
      [conversation('a', '해시 테이블과 프로세스 스케줄링, 교착 상태')],
      [security, os],
    );
    expect(assigned[0].matches.map((match) => match.courseId)).toEqual([2, 1]);
    expect(assigned[0].matches[0].matchedKeywords).toEqual([
      '프로세스',
      '교착 상태',
      '해시',
    ]);
  });

  it('걸린 수가 같으면 과목 목록에서 앞선 과목이 먼저다', () => {
    const { assigned } = assignToCourses(
      [conversation('a', '해시 함수')],
      [security, os],
    );
    expect(assigned[0].matches.map((match) => match.courseId)).toEqual([1, 2]);
  });

  it('과목이 없거나 키워드가 없으면 아무것도 고르지 않는다', () => {
    const items = [conversation('a', '암호화')];
    expect(assignToCourses(items, []).unmatchedCount).toBe(1);
    expect(
      assignToCourses(items, [{ ...security, keywords: [] }]).assigned,
    ).toEqual([]);
  });

  it('키워드의 대소문자와 앞뒤 공백을 무시한다', () => {
    const items = [conversation('a', 'RSA 키 길이는?')];
    expect(
      assignToCourses(items, [{ ...security, keywords: [' rsa '] }]).assigned,
    ).toHaveLength(1);
  });

  it('키워드가 공백뿐이면 아무것도 고르지 않는다', () => {
    const items = [conversation('a', '암호화')];
    expect(
      assignToCourses(items, [{ ...security, keywords: ['  ', ''] }]).assigned,
    ).toEqual([]);
  });

  it('제목과 메시지 사이도 낱말 경계로 본다', () => {
    const item = {
      ...conversation('a', 'shipping은 상관없어요'),
      title: '네트워크 질문 IP',
    };
    const course = { ...security, keywords: ['IP'] };
    expect(assignToCourses([item], [course]).assigned).toHaveLength(1);
    expect(
      assignToCourses([{ ...item, title: '배송 질문' }], [course]).assigned,
    ).toEqual([]);
  });
});

describe('mergeParseResults', () => {
  it('여러 파일을 합치고 같은 대화는 하나로 센다', () => {
    const merged = mergeParseResults([
      {
        fileName: 'conversations-000.json',
        result: {
          ok: true,
          source: 'claude',
          conversations: [
            conversation('a', '1', '2026-10-01T00:00:00.000Z'),
            conversation('b', '2', '2026-10-03T00:00:00.000Z'),
          ],
          skippedCount: 1,
        },
      },
      {
        fileName: 'conversations-001.json',
        result: {
          ok: true,
          source: 'claude',
          conversations: [
            conversation('b', '2', '2026-10-03T00:00:00.000Z'),
            conversation('c', '3'),
          ],
          skippedCount: 2,
        },
      },
    ]);
    // 최신이 위로, 날짜 없는 대화는 맨 뒤로
    expect(merged.conversations.map((item) => item.id)).toEqual([
      'b',
      'a',
      'c',
    ]);
    expect(merged.duplicateCount).toBe(1);
    expect(merged.skippedCount).toBe(3);
    expect(merged.errors).toEqual([]);
  });

  it('읽지 못한 파일은 이유와 함께 따로 모은다', () => {
    const merged = mergeParseResults([
      {
        fileName: 'notes.json',
        result: {
          ok: false,
          title: '대화 기록 파일이 아니에요',
          message: '형식이 달라요.',
        },
      },
    ]);
    expect(merged.conversations).toEqual([]);
    expect(merged.errors).toEqual([
      {
        fileName: 'notes.json',
        title: '대화 기록 파일이 아니에요',
        message: '형식이 달라요.',
        manifest: undefined,
      },
    ]);
  });
});
