interface DoneState {
  waiting: boolean;
  message: string;
}

interface NextWaiting {
  waiting: boolean;
  failed: boolean;
}

// AI 대기 live 영역(AiWaitingStatus)에 대기가 끝난 뒤 남길 문구를 고른다.
// - 대기 중에는 비운다(보이는 대기 상자 자체가 live 영역 안에서 읽힌다)
// - 대기가 성공으로 끝나면 완료 문구. 실패면 비운다(오류는 ErrorText의 role="alert"가 읽는다)
// - 대기 상태가 그대로면 이전 문구를 둔다(처음 그릴 때 이미 끝난 상태는 읽지 않는다)
export function nextDoneMessage(
  prev: DoneState,
  next: NextWaiting,
  doneText?: string,
): string {
  if (next.waiting) return '';
  if (prev.waiting) return next.failed || !doneText ? '' : doneText;
  return prev.message;
}
