import { describe, expect, it } from 'vitest';
import { kakaoStartUrl, safeReturnTo, socialErrorMessage } from './socialLogin';

describe('safeReturnTo', () => {
  it.each(['/analysis', '/quizzes/3?tab=a', '/'])(
    '사이트 안의 경로 %s 는 그대로 둔다',
    (path) => {
      expect(safeReturnTo(path)).toBe(path);
    },
  );

  it.each([
    null,
    undefined,
    '',
    '//evil.com',
    '/\\evil.com',
    'https://evil.com',
    'javascript:alert(1)',
    '/a\nb',
    '/a\\b',
    'analysis',
    ' /analysis',
  ])('다른 사이트나 이상한 값 %j 은 첫 화면으로 바꾼다', (raw) => {
    expect(safeReturnTo(raw)).toBe('/');
  });

  it('200자를 넘으면 첫 화면으로 바꾼다', () => {
    expect(safeReturnTo('/' + 'a'.repeat(199))).toHaveLength(200);
    expect(safeReturnTo('/' + 'a'.repeat(200))).toBe('/');
  });
});

describe('kakaoStartUrl', () => {
  it('돌아갈 경로를 인코딩해 붙인다', () => {
    expect(kakaoStartUrl('/quizzes/3?tab=a')).toBe(
      '/api/auth/kakao/start?returnTo=%2Fquizzes%2F3%3Ftab%3Da',
    );
  });

  it('위험한 경로는 첫 화면으로 바꿔 붙인다', () => {
    expect(kakaoStartUrl('//evil.com')).toBe(
      '/api/auth/kakao/start?returnTo=%2F',
    );
    expect(kakaoStartUrl()).toBe('/api/auth/kakao/start?returnTo=%2F');
  });
});

describe('socialErrorMessage', () => {
  it('취소했거나 코드가 없으면 문구를 보여 주지 않는다', () => {
    expect(socialErrorMessage('cancelled')).toBeNull();
    expect(socialErrorMessage(null)).toBeNull();
    expect(socialErrorMessage('')).toBeNull();
  });

  it('코드마다 정한 문구를 돌려준다', () => {
    expect(socialErrorMessage('state')).toBe(
      '로그인 확인 시간이 지났어요. 다시 시도해 주세요.',
    );
    expect(socialErrorMessage('email_required')).toBe(
      '카카오 계정의 이메일 제공에 동의해야 가입할 수 있어요.',
    );
    expect(socialErrorMessage('email_unverified')).toBe(
      '카카오 계정의 이메일 인증을 마친 뒤 다시 시도해 주세요.',
    );
    expect(socialErrorMessage('rejoin_restricted')).toBe(
      '탈퇴한 이메일은 30일 동안 다시 가입할 수 없어요.',
    );
    expect(socialErrorMessage('link_conflict')).toBe(
      '이 이메일에는 다른 카카오 계정이 연결되어 있어요.',
    );
  });

  it('모르는 코드·장애는 일반 문구', () => {
    const general =
      '카카오 로그인 중 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.';
    expect(socialErrorMessage('failed')).toBe(general);
    expect(socialErrorMessage('unavailable')).toBe(general);
    expect(socialErrorMessage('<script>')).toBe(general);
  });
});
