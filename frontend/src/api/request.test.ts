import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { ApiError, TIMEOUT_MESSAGE, toApiError, unwrap } from './request';

describe('unwrap', () => {
  it('성공 응답이면 data만 돌려준다', () => {
    expect(unwrap({ success: true, message: '', data: [1, 2] })).toEqual([
      1, 2,
    ]);
  });

  it('실패 응답이면 서버 메시지로 ApiError를 던진다', () => {
    expect(() =>
      unwrap({ success: false, message: '과목을 찾을 수 없어요.', data: null }),
    ).toThrow(new ApiError('과목을 찾을 수 없어요.'));
  });

  it('실패 응답에 메시지가 없으면 기본 문구를 쓴다', () => {
    expect(() => unwrap({ success: false, message: '', data: null })).toThrow(
      /요청을 처리하지 못했어요/,
    );
  });
});

describe('toApiError', () => {
  const config = { headers: new AxiosHeaders() };

  it('서버가 보낸 오류 메시지와 상태 코드를 담는다', () => {
    const error = new AxiosError('fail', 'ERR_BAD_REQUEST', config, null, {
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config,
      data: { success: false, message: '파일 형식을 확인해 주세요.' },
    });
    const result = toApiError(error);
    expect(result.message).toBe('파일 형식을 확인해 주세요.');
    expect(result.status).toBe(400);
  });

  it('응답이 없으면(네트워크 오류) 연결 안내 문구를 쓴다', () => {
    const error = new AxiosError('Network Error', 'ERR_NETWORK', config);
    expect(toApiError(error).message).toMatch(/서버에 연결하지 못했어요/);
  });

  it('시간 초과는 연결 문제가 아니라 아직 처리 중일 수 있다고 알린다', () => {
    const error = new AxiosError('timeout', 'ECONNABORTED', config);
    const result = toApiError(error);
    expect(result.message).toBe(TIMEOUT_MESSAGE);
    expect(result.status).toBeUndefined();
  });

  it('프록시가 낸 504(서버 응답 형식 없음)는 시간 초과와 같게 다룬다', () => {
    const error = new AxiosError('fail', 'ERR_BAD_RESPONSE', config, null, {
      status: 504,
      statusText: 'Gateway Timeout',
      headers: {},
      config,
      data: 'An error occurred with your deployment',
    });
    const result = toApiError(error);
    expect(result.message).toBe(TIMEOUT_MESSAGE);
    expect(result.status).toBeUndefined();
  });

  it('서버가 직접 보낸 504는 서버 메시지와 상태 코드를 그대로 담는다', () => {
    const error = new AxiosError('fail', 'ERR_BAD_RESPONSE', config, null, {
      status: 504,
      statusText: 'Gateway Timeout',
      headers: {},
      config,
      data: { success: false, message: '퀴즈를 만들지 못했어요.' },
    });
    const result = toApiError(error);
    expect(result.message).toBe('퀴즈를 만들지 못했어요.');
    expect(result.status).toBe(504);
  });

  it('알 수 없는 오류도 ApiError로 바꾼다', () => {
    expect(toApiError(new Error('boom'))).toBeInstanceOf(ApiError);
  });
});
