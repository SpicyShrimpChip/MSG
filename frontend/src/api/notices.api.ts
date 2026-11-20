import type { Notice, NoticeDetail, NoticeInput } from '@/types';
import {
  mockCreateNotice,
  mockDeleteNotice,
  mockGetNotice,
  mockGetNotices,
  mockUpdateNotice,
} from '@/mocks';
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { request } from './request';

// React Query에서 공지를 담는 키. 상세는 [...NOTICES_QUERY_KEY, id]
export const NOTICES_QUERY_KEY = ['notices'] as const;

export function getNotices(): Promise<Notice[]> {
  if (isMockData()) return mockGetNotices();
  return request<Notice[]>({ method: 'GET', url: '/notices' });
}

export function getNotice(id: number): Promise<NoticeDetail> {
  if (isMockData()) return mockGetNotice(id);
  return request<NoticeDetail>({ method: 'GET', url: `/notices/${id}` });
}

// 아래 셋은 관리자만 쓸 수 있다 (서버가 /api/admin/** 에서 역할을 매번 확인한다)
export function createNotice(input: NoticeInput): Promise<NoticeDetail> {
  if (isMockData()) return mockCreateNotice(input);
  return request<NoticeDetail>({
    method: 'POST',
    url: '/admin/notices',
    data: input,
  });
}

export function updateNotice(
  id: number,
  input: NoticeInput,
): Promise<NoticeDetail> {
  if (isMockData()) return mockUpdateNotice(id, input);
  return request<NoticeDetail>({
    method: 'PUT',
    url: `/admin/notices/${id}`,
    data: input,
  });
}

export function deleteNotice(id: number): Promise<void> {
  if (isMockData()) return mockDeleteNotice(id);
  return request<void>({ method: 'DELETE', url: `/admin/notices/${id}` });
}
