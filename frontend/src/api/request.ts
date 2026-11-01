import { isAxiosError, type AxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@/types';
import { api } from './axios';

// 화면에 그대로 보여 줄 수 있는 문구를 담은 API 오류.
// data: 실패 응답에 실린 data (예: 대화 가져오기 422의 저장하지 않은 대화 목록). 대부분은 없다
export class ApiError extends Error {
  status?: number;
  data?: unknown;

  constructor(message: string, status?: number, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

const DEFAULT_ERROR_MESSAGE =
  '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

// 공통 응답에서 data만 꺼낸다. success가 false면 서버 메시지로 오류를 던진다
export function unwrap<T>(body: ApiResponse<T>): T {
  if (!body.success) {
    throw new ApiError(body.message || DEFAULT_ERROR_MESSAGE);
  }
  return body.data;
}

// 통신 실패(4xx·5xx, 시간 초과, 네트워크)를 ApiError로 바꾼다
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (isAxiosError<Partial<ApiResponse<unknown>>>(error)) {
    if (!error.response) {
      return new ApiError(
        '서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.',
      );
    }
    return new ApiError(
      error.response.data?.message || DEFAULT_ERROR_MESSAGE,
      error.response.status,
      error.response.data?.data ?? undefined,
    );
  }
  return new ApiError(DEFAULT_ERROR_MESSAGE);
}

// 모든 API 함수는 이 함수로 요청한다. 성공하면 data만 돌려준다
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await api.request<ApiResponse<T>>(config);
    return unwrap(response.data);
  } catch (error) {
    throw toApiError(error);
  }
}
