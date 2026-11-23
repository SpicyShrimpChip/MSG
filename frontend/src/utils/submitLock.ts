// 저장 버튼 연타·더블클릭으로 같은 요청이 두 번 가지 않게 하는 잠금 (#210).
// 버튼의 disabled={isPending}는 화면을 다시 그린 뒤에야 걸려서, 그 전에 들어온 두 번째 클릭이 또 보낸다.
// 그래서 요청을 보내기 직전에 이 잠금을 바로(동기로) 걸고, 요청이 끝나면 푼다
export interface SubmitLock {
  // 잠겨 있지 않으면 잠그고 true, 이미 잠겨 있으면 false (이번 클릭은 보내지 않는다)
  tryAcquire(): boolean;
  release(): void;
  isHeld(): boolean;
}

export function createSubmitLock(): SubmitLock {
  let held = false;
  return {
    tryAcquire() {
      if (held) return false;
      held = true;
      return true;
    },
    release() {
      held = false;
    },
    isHeld() {
      return held;
    },
  };
}

// 잠금을 얻었을 때만 send를 부른다. 보냈으면 true.
// send가 바로 예외를 던지면 요청이 시작되지 않았으므로 잠금을 풀고 예외를 그대로 넘긴다
export function sendOnce<TArgs extends unknown[]>(
  lock: SubmitLock,
  send: (...args: TArgs) => void,
  ...args: TArgs
): boolean {
  if (!lock.tryAcquire()) return false;
  try {
    send(...args);
  } catch (error) {
    lock.release();
    throw error;
  }
  return true;
}
