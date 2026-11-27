// 체험 모드의 예시 데이터를 브라우저(localStorage)에 남겨 새로고침해도 유지한다.
// 키는 `msg-mock-*`로 짓는다: 체험을 시작·끝낼 때 store/useDemoStore.ts가 이 접두사로 지운다.
// 저장소를 쓸 수 없으면(사생활 보호 모드 등) 예외를 삼키고 이번 화면 동안 메모리에만 둔다
interface PersistedOptions<T> {
  // 저장된 값이 없거나 읽지 못할 때의 값
  initial: () => T;
  // 저장된 값을 되살릴 때 고칠 것 (예: 예시 설명 채우기)
  revive?: (saved: T) => T;
  // true(기본)면 처음 한 번만 읽고 그 뒤로는 메모리 값을 쓴다.
  // false면 매번 읽는다(체험을 다시 시작해 지운 뒤 옛 값이 남지 않게)
  cache?: boolean;
}

export function persisted<T>(
  key: string,
  { initial, revive = (saved) => saved, cache = true }: PersistedOptions<T>,
) {
  // 마지막으로 set한 값(cache면 처음 읽은 값도). 모듈을 불러오기만 해서는 저장소를 읽지 않는다
  // (VITE_USE_MOCK=false면 앱 시작 때 저장소를 건드리지 않음)
  let memory: T | undefined;

  const read = (): T => {
    try {
      const saved = localStorage.getItem(key);
      return saved ? revive(JSON.parse(saved) as T) : initial();
    } catch {
      return memory ?? initial();
    }
  };

  return {
    get(): T {
      if (!cache) return read();
      return (memory ??= read());
    },
    set(next: T) {
      memory = next;
      try {
        localStorage.setItem(key, JSON.stringify(next));
      } catch {
        // 저장소를 쓸 수 없으면 이번 화면에서만 유지한다
      }
    },
  };
}
