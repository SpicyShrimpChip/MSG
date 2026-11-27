import { describe, expect, it } from 'vitest';
import { storyStepText } from './storyAnnouncement';

describe('storyStepText', () => {
  it('단계 번호·이름·제목·설명을 한 문장으로, 제목의 줄바꿈은 공백으로', () => {
    expect(
      storyStepText(
        {
          title: '대화 가져오기',
          headline: '흩어진 AI 대화를\n과목별로 모아요',
          body: '파일을 읽어요.',
        },
        0,
        6,
      ),
    ).toBe(
      '1단계 · 전체 6단계, 대화 가져오기. 흩어진 AI 대화를 과목별로 모아요 파일을 읽어요.',
    );
  });
});
