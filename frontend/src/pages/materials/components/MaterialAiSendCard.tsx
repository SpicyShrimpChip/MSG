import type { ReactNode } from 'react';
import { Lock } from 'lucide-react';
import {
  AiWaitingStatus,
  Button,
  Card,
  Checkbox,
  ErrorText,
  Notice,
} from '@/components';

interface MaterialAiSendCardProps {
  title: string;
  // 무엇을 얼마나 외부 AI로 보내는지 (자물쇠 안내 상자)
  notice: ReactNode;
  // 막힌 이유가 있으면 체크 칸 대신 이유만 보여 주고 버튼을 막는다 (utils/material.ts)
  blockedReason: string | null;
  confirmed: boolean;
  onConfirmedChange: (checked: boolean) => void;
  confirmLabel: ReactNode;
  error: Error | null;
  pending: boolean;
  failed: boolean;
  pendingText: ReactNode;
  doneText: string;
  // 대기 알림을 새로 그려야 할 때 바꾸는 key (요약은 목적마다)
  waitingKey?: string;
  submitLabel: string;
  onSubmit: () => void;
  // 목업 안내 (목업이 아니면 null)
  mockNote: ReactNode | null;
}

// 자료 상세의 "AI로 본문 보내기" 카드: 핵심 개념 뽑기·요약 만들기가 같이 쓴다.
// 보내기 전에 요청마다 확인 체크를 받고, 막힌 이유가 있으면 체크 칸을 숨긴다
function MaterialAiSendCard({
  title,
  notice,
  blockedReason,
  confirmed,
  onConfirmedChange,
  confirmLabel,
  error,
  pending,
  failed,
  pendingText,
  doneText,
  waitingKey,
  submitLabel,
  onSubmit,
  mockNote,
}: MaterialAiSendCardProps) {
  return (
    <Card as="section" className="flex flex-col gap-3 p-5">
      <h3 className="text-body font-semibold text-heading">{title}</h3>
      <Notice icon={Lock} size="caption" iconClassName="text-primary">
        {notice}
      </Notice>
      {blockedReason ? (
        <p className="text-body text-ink-soft">{blockedReason}</p>
      ) : (
        <Checkbox checked={confirmed} onChange={onConfirmedChange}>
          {confirmLabel}
        </Checkbox>
      )}
      <ErrorText error={error} />
      <AiWaitingStatus
        key={waitingKey}
        waiting={pending}
        failed={failed}
        doneText={doneText}
      >
        {pendingText}
      </AiWaitingStatus>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          disabled={blockedReason !== null || !confirmed || pending}
          onClick={onSubmit}
        >
          {submitLabel}
        </Button>
        {mockNote && (
          <span className="text-caption text-ink-muted">{mockNote}</span>
        )}
      </div>
    </Card>
  );
}

export default MaterialAiSendCard;
