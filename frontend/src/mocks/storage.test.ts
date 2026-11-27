import { afterEach, describe, expect, it, vi } from 'vitest';
import { persisted } from './storage';

const memoryStorage = () => {
  const data = new Map<string, string>();
  return {
    data,
    getItem: vi.fn((key: string) => data.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => void data.set(key, value)),
    removeItem: vi.fn((key: string) => void data.delete(key)),
  };
};

const brokenStorage = () => ({
  getItem: vi.fn(() => {
    throw new Error('저장소를 쓸 수 없음');
  }),
  setItem: vi.fn(() => {
    throw new Error('저장소를 쓸 수 없음');
  }),
  removeItem: vi.fn(),
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('persisted', () => {
  it('저장된 값이 없으면 initial, 있으면 revive한 값을 준다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    const empty = persisted<number[]>('k-empty', { initial: () => [0] });
    expect(empty.get()).toEqual([0]);

    storage.data.set('k-saved', JSON.stringify([1, 2]));
    const saved = persisted<number[]>('k-saved', {
      initial: () => [0],
      revive: (value) => [...value, 3],
    });
    expect(saved.get()).toEqual([1, 2, 3]);
  });

  it('set한 값을 저장소에 JSON으로 쓴다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    const store = persisted<number[]>('k-set', { initial: () => [] });
    store.set([5]);
    expect(storage.data.get('k-set')).toBe('[5]');
    expect(store.get()).toEqual([5]);
  });

  it('cache면 한 번만 읽는다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    const store = persisted<number[]>('k-cache', { initial: () => [] });
    store.get();
    store.get();
    expect(storage.getItem).toHaveBeenCalledTimes(1);
  });

  it('cache가 false면 매번 읽어 지운 뒤에는 initial로 돌아간다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    const store = persisted<number[]>('k-nocache', {
      initial: () => [],
      cache: false,
    });
    store.set([1]);
    expect(store.get()).toEqual([1]);
    storage.removeItem('k-nocache');
    expect(store.get()).toEqual([]);
  });

  it('불러오기만 해서는 저장소를 읽지 않는다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    persisted<number[]>('k-lazy', { initial: () => [] });
    persisted<number[]>('k-lazy2', { initial: () => [], cache: false });
    expect(storage.getItem).not.toHaveBeenCalled();
  });

  it('저장소가 예외를 던지면 initial이고 set한 값은 메모리에 남는다', () => {
    vi.stubGlobal('localStorage', brokenStorage());
    const cached = persisted<number[]>('k-broken', { initial: () => [0] });
    expect(cached.get()).toEqual([0]);
    cached.set([7]);
    expect(cached.get()).toEqual([7]);

    const uncached = persisted<number[]>('k-broken2', {
      initial: () => [0],
      cache: false,
    });
    expect(uncached.get()).toEqual([0]);
    uncached.set([8]);
    expect(uncached.get()).toEqual([8]);
  });

  it('JSON이 깨지면 initial이다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    storage.data.set('k-bad', '{깨진');
    expect(persisted('k-bad', { initial: () => [0] }).get()).toEqual([0]);
    expect(
      persisted('k-bad', { initial: () => [0], cache: false }).get(),
    ).toEqual([0]);
  });

  it('저장값이 빈 문자열이거나 revive가 예외를 던지면 initial이다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    storage.data.set('k-blank', '');
    expect(persisted('k-blank', { initial: () => [0] }).get()).toEqual([0]);
    storage.data.set('k-revive', '[1]');
    const store = persisted<number[]>('k-revive', {
      initial: () => [0],
      revive: () => {
        throw new Error('깨진 데이터');
      },
    });
    expect(store.get()).toEqual([0]);
  });

  it('cache가 false에서 쓰기만 실패하면 저장돼 있던 값을 읽는다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    storage.data.set('k-readonly', '[1]');
    storage.setItem.mockImplementation(() => {
      throw new Error('용량 초과');
    });
    const store = persisted<number[]>('k-readonly', {
      initial: () => [],
      cache: false,
    });
    store.set([2]);
    expect(store.get()).toEqual([1]);
  });

  it('cache면 다른 곳이 저장소를 지워도 메모리 값을 쓴다', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    const store = persisted<number[]>('k-kept', { initial: () => [] });
    store.set([3]);
    storage.removeItem('k-kept');
    expect(store.get()).toEqual([3]);
  });

  it('localStorage가 아예 없어도 깨지지 않는다', () => {
    vi.stubGlobal('localStorage', undefined);
    const store = persisted<number[]>('k-none', { initial: () => [0] });
    expect(store.get()).toEqual([0]);
    store.set([1]);
    expect(store.get()).toEqual([1]);
  });
});
