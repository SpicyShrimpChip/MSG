import { describe, expect, it } from 'vitest';
import {
  buildSocialSignupInput,
  initialSocialName,
  kakaoStartUrl,
  safeReturnTo,
  socialErrorMessage,
  socialLoginErrorPath,
} from './socialLogin';

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

describe('socialLoginErrorPath', () => {
  it('돌아갈 화면을 같이 넘긴다 (서버 콜백 실패 주소와 같은 모양)', () => {
    expect(socialLoginErrorPath('failed', '/quizzes/3?tab=a')).toBe(
      '/login?socialError=failed&returnTo=%2Fquizzes%2F3%3Ftab%3Da',
    );
  });

  it('첫 화면이거나 위험한 경로면 returnTo를 붙이지 않는다', () => {
    expect(socialLoginErrorPath('failed')).toBe('/login?socialError=failed');
    expect(socialLoginErrorPath('failed', '/')).toBe(
      '/login?socialError=failed',
    );
    expect(socialLoginErrorPath('failed', '//evil.com')).toBe(
      '/login?socialError=failed',
    );
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

describe('initialSocialName', () => {
  it('카카오 닉네임을 이름 칸의 처음 값으로 쓰고 30자로 자른다', () => {
    expect(initialSocialName('  카카오닉 ')).toBe('카카오닉');
    expect(initialSocialName('가'.repeat(40))).toHaveLength(30);
  });

  it('닉네임이 없으면 빈 칸', () => {
    expect(initialSocialName(null)).toBe('');
  });
});

describe('buildSocialSignupInput', () => {
  const base = {
    name: '  새 사용자 ',
    acknowledged: true,
    aiConsent: false,
    aiQuizConsent: true,
    profile: {
      learningPurpose: 'work' as const,
      jobField: 'data_analysis' as const,
      jobFieldOther: '무시됨',
    },
  };

  it('안내 확인 하나로 이용약관·개인정보를 함께 보내고 이름을 다듬는다', () => {
    expect(buildSocialSignupInput(base)).toEqual({
      name: '새 사용자',
      agreedTerms: true,
      agreedPrivacy: true,
      agreedAiAnalysis: false,
      agreedAiQuiz: true,
      learningPurpose: 'work',
      jobField: 'data_analysis',
      jobFieldOther: null,
    });
  });

  it('직무가 기타일 때만 직접 쓴 분야를 보낸다', () => {
    const input = buildSocialSignupInput({
      ...base,
      profile: {
        learningPurpose: 'work',
        jobField: 'other',
        jobFieldOther: ' 교육 ',
      },
    });
    expect(input.jobFieldOther).toBe('교육');
  });
});
