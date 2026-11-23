// 대화 가져올 때 가릴 단어(학교·회사·주소 등). 단어 목록 자체가 개인정보라서 서버에 보내지 않고
// 이 브라우저의 localStorage에만 계정별로 나눠 저장한다.
// 나중에 기기 간 동기화로 바꿀 때는 이 파일의 읽기·쓰기만 고치면 된다

type WordStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

const KEY_PREFIX = 'msg-mask-words:';

export const maskWordsKey = (userId: number) => `${KEY_PREFIX}${userId}`;

const defaultStorage = (): WordStorage | null => {
  try {
    return localStorage;
  } catch {
    return null;
  }
};

// 같은 탭의 다른 화면(마이페이지·가져오기)이 바로 따라오도록 바뀌면 알린다
const listeners = new Set<() => void>();
// useSyncExternalStore가 같은 값이면 같은 배열을 받도록 읽은 결과를 기억한다
const cache = new Map<string, { raw: string | null; words: string[] }>();
const EMPTY: string[] = [];

export const subscribeMaskWords = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

const notify = () => {
  for (const listener of listeners) listener();
};

// 앞뒤 공백을 지우고, 빈 단어와 겹치는 단어를 뺀다
const normalizeMaskWords = (words: string[]) => [
  ...new Set(words.map((word) => word.trim()).filter(Boolean)),
];

export const loadMaskWords = (
  userId: number,
  storage: WordStorage | null = defaultStorage(),
): string[] => {
  const key = maskWordsKey(userId);
  let raw: string | null = null;
  try {
    raw = storage?.getItem(key) ?? null;
  } catch {
    // 저장소를 쓸 수 없는 환경(비공개 창 등)에서는 저장한 단어가 없는 것으로 본다
  }
  const cached = cache.get(key);
  if (cached && cached.raw === raw) return cached.words;

  let words = EMPTY;
  if (raw) {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        words = normalizeMaskWords(
          parsed.filter((item): item is string => typeof item === 'string'),
        );
      }
    } catch {
      // 깨진 값은 무시한다
    }
  }
  cache.set(key, { raw, words });
  return words;
};

export const saveMaskWords = (
  userId: number,
  words: string[],
  storage: WordStorage | null = defaultStorage(),
) => {
  const next = normalizeMaskWords(words);
  try {
    if (next.length === 0) storage?.removeItem(maskWordsKey(userId));
    else storage?.setItem(maskWordsKey(userId), JSON.stringify(next));
  } catch {
    // 저장하지 못하면 이번 가져오기에서만 쓴다
  }
  notify();
};

// 탈퇴·AI 대화 분석 동의 철회 때 이 브라우저에 남은 단어를 지운다
export const clearMaskWords = (
  userId: number,
  storage: WordStorage | null = defaultStorage(),
) => saveMaskWords(userId, [], storage);
