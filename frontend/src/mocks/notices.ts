import type { Notice, NoticeDetail, NoticeInput } from '@/types';
import { ApiError } from '@/api/request';
import { formatDate } from '@/utils';
import {
  NOTICE_CONTENT_MAX_LENGTH,
  NOTICE_TITLE_MAX_LENGTH,
} from '@/constants';
import { mockDelay } from './delay';

// 목업 전용 저장소. 새로고침해도 관리자 화면에서 쓴 공지가 남도록 브라우저에 저장한다 (실제 데이터는 서버 DB에 저장)
const STORAGE_KEY = 'ai-lms-mock-notices';

// 백엔드 시드 R__sample_notices.sql과 같은 내용
const SAMPLE: NoticeDetail[] = [
  {
    id: 3,
    title: 'AI 대화 가져오기를 열었어요',
    content:
      'ChatGPT, Claude에서 내보낸 대화 파일을 가져올 수 있어요. 파일은 내 브라우저에서 읽고, 공부한 대화 중 내가 고른 것만 개인정보를 가린 뒤 보내요. 메인 화면의 "AI 대화 가져오기"에서 시작해 보세요.',
    createdAt: '2026-10-24',
    updatedAt: null,
  },
  {
    id: 2,
    title: '개인정보 처리 방식을 안내해 드려요',
    content:
      'AI 대화 파일은 내 브라우저에서만 읽고, 내가 고른 대화만 개인정보를 가린 뒤 서버로 보내요. 자세한 내용은 이용안내의 개인정보 처리방침에서 볼 수 있어요.',
    createdAt: '2026-10-20',
    updatedAt: null,
  },
  {
    id: 1,
    title: '서비스를 시작했어요',
    content:
      'AI 학습 도우미 서비스를 시작했어요. 학습 자료와 AI 대화 기록을 함께 분석해 개념별 강점과 보완할 점을 보여 드려요.',
    createdAt: '2026-10-15',
    updatedAt: null,
  },
];

function load(): NoticeDetail[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as NoticeDetail[]) : SAMPLE;
  } catch {
    return SAMPLE;
  }
}

function save(notices: NoticeDetail[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(notices));
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 이번 화면에서만 유지한다
  }
}

// 처음 쓸 때 읽는다 (VITE_USE_MOCK=false면 앱 시작 때 저장소를 건드리지 않음)
let cache: NoticeDetail[] | null = null;
const getAll = () => (cache ??= load());
const setAll = (next: NoticeDetail[]) => {
  cache = next;
  save(next);
};

const NOT_FOUND = new ApiError('공지사항을 찾을 수 없어요.', 404);

// 서버 검증(NoticeRequest)과 같은 문구·기준: 앞뒤 공백을 지우고, 비었거나 길면 400
function validate(input: NoticeInput): NoticeInput {
  const title = input.title.trim();
  const content = input.content.trim();
  if (!title) throw new ApiError('제목을 입력해 주세요.', 400);
  if (title.length > NOTICE_TITLE_MAX_LENGTH) {
    throw new ApiError('제목은 100자까지 입력할 수 있어요.', 400);
  }
  if (!content) throw new ApiError('본문을 입력해 주세요.', 400);
  if (content.length > NOTICE_CONTENT_MAX_LENGTH) {
    throw new ApiError('본문은 5000자까지 입력할 수 있어요.', 400);
  }
  return { title, content };
}

const toNotice = ({ id, title, createdAt }: NoticeDetail): Notice => ({
  id,
  title,
  createdAt,
});

// 최신 공지가 위로: 작성일 내림차순, 같은 날이면 id 큰 순 (서버와 같다)
export function mockGetNotices(): Promise<Notice[]> {
  const list = [...getAll()]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
    .map(toNotice);
  return mockDelay(list);
}

export function mockGetNotice(id: number): Promise<NoticeDetail> {
  const notice = getAll().find((item) => item.id === id);
  return notice ? mockDelay(notice) : Promise.reject(NOT_FOUND);
}

export function mockCreateNotice(input: NoticeInput): Promise<NoticeDetail> {
  try {
    const all = getAll();
    const notice: NoticeDetail = {
      id: Math.max(0, ...all.map((item) => item.id)) + 1,
      ...validate(input),
      createdAt: formatDate(new Date().toISOString()) ?? '',
      updatedAt: null,
    };
    setAll([notice, ...all]);
    return mockDelay(notice);
  } catch (error) {
    return Promise.reject(error);
  }
}

export function mockUpdateNotice(
  id: number,
  input: NoticeInput,
): Promise<NoticeDetail> {
  try {
    const all = getAll();
    const found = all.find((item) => item.id === id);
    if (!found) return Promise.reject(NOT_FOUND);
    const updated: NoticeDetail = {
      ...found,
      ...validate(input),
      updatedAt: new Date().toISOString(),
    };
    setAll(all.map((item) => (item.id === id ? updated : item)));
    return mockDelay(updated);
  } catch (error) {
    return Promise.reject(error);
  }
}

export function mockDeleteNotice(id: number): Promise<void> {
  const all = getAll();
  if (!all.some((item) => item.id === id)) return Promise.reject(NOT_FOUND);
  setAll(all.filter((item) => item.id !== id));
  return mockDelay(undefined);
}
