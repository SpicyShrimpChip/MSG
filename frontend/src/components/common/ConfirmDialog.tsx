import { useEffect, useRef } from 'react';
import Button from './Button';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

// 삭제·철회처럼 되돌리기 어려운 동작 전에 한 번 더 묻는 창.
// 브라우저 기본 <dialog>를 써서 포커스 가두기와 Esc 닫기를 그대로 얻는다.
function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby="confirm-dialog-title"
      aria-describedby={description ? 'confirm-dialog-description' : undefined}
      // Esc로 닫히면 부모 상태도 함께 닫는다
      onCancel={(e) => {
        e.preventDefault();
        onCancel();
      }}
      // 어두운 바깥 영역을 누르면 닫는다
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-sm rounded-2xl border border-line-strong bg-surface-raised p-0 text-ink backdrop:bg-overlay/50"
    >
      <div className="flex flex-col gap-4 p-6">
        <h2
          id="confirm-dialog-title"
          className="text-lg font-semibold text-heading"
        >
          {title}
        </h2>
        {description && (
          <p
            id="confirm-dialog-description"
            className="text-body text-ink-soft"
          >
            {description}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>
            취소
          </Button>
          <Button onClick={onConfirm}>{confirmLabel}</Button>
        </div>
      </div>
    </dialog>
  );
}

export default ConfirmDialog;
