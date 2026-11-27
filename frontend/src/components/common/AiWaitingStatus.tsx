import { useState, type ReactNode } from 'react';
import type { OrbState } from 'thinking-orbs';
import AiWaiting from './AiWaiting';
import { nextDoneMessage } from './aiWaitingMessage';

interface AiWaitingStatusProps {
  // AI 응답을 기다리는 중인지
  waiting: boolean;
  // 대기가 실패로 끝났는지. 실패면 완료 문구를 읽지 않는다(오류는 ErrorText가 읽는다)
  failed?: boolean;
  // 대기가 성공으로 끝나면 화면 낭독기에 읽어 줄 문구. 끝나면 다른 화면으로 가는 곳은 비운다
  doneText?: string;
  state?: OrbState;
  // 대기 상자(AiWaiting)에 붙일 클래스
  className?: string;
  // 대기 중에 보여 줄 안내 글
  children: ReactNode;
}

// 늘 DOM에 있는 AI 대기 live 영역. 대기 중에는 그 안에 대기 상자(AiWaiting)를 그려 대기 시작을 읽고,
// 끝나면 상자를 치우고 완료 문구만 화면 밖(sr-only)에 남겨 읽는다. 영역이 대기 중에만 생기면
// 화면 낭독기가 시작·완료를 놓친다(새로 붙은 live 영역은 읽지 않는 경우가 많다)
function AiWaitingStatus({
  waiting,
  failed = false,
  doneText,
  state,
  className,
  children,
}: AiWaitingStatusProps) {
  const [prevWaiting, setPrevWaiting] = useState(waiting);
  const [message, setMessage] = useState('');
  // 대기 상태가 바뀐 그 렌더에서 문구를 고른다(효과로 하면 한 번 더 그린다)
  if (waiting !== prevWaiting) {
    setPrevWaiting(waiting);
    setMessage(
      nextDoneMessage(
        { waiting: prevWaiting, message },
        { waiting, failed },
        doneText,
      ),
    );
  }

  return (
    <div aria-live="polite" className={waiting ? undefined : 'sr-only'}>
      {waiting ? (
        <AiWaiting state={state} className={className}>
          {children}
        </AiWaiting>
      ) : (
        message
      )}
    </div>
  );
}

export default AiWaitingStatus;
