import { useState, type ReactNode } from 'react';
import { Upload } from 'lucide-react';
import Spinner from './Spinner';

interface FileDropZoneProps {
  // 숨긴 입력칸의 id. 바깥 라벨(FormRow 등)이 이 id를 가리킬 수 있다
  id: string;
  accept: string;
  multiple?: boolean;
  // 파일을 읽는 중이면 아이콘을 돌리고 새 파일을 받지 않는다
  busy?: boolean;
  title: ReactNode;
  description: ReactNode;
  onFiles: (files: File[]) => void;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
}

// 파일을 끌어다 놓거나 눌러서 고르는 영역. 실제 입력칸은 화면에서 숨기고 라벨이 대신 눌린다
function FileDropZone({
  id,
  accept,
  multiple,
  busy = false,
  title,
  description,
  onFiles,
  ...aria
}: FileDropZoneProps) {
  const [dragging, setDragging] = useState(false);

  return (
    <>
      <label
        htmlFor={id}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (!busy) onFiles([...e.dataTransfer.files]);
        }}
        className={`flex cursor-pointer flex-col items-center gap-2.5 rounded-2xl border-2 border-dashed px-6 py-10 text-center hover:border-primary hover:bg-primary-soft ${
          dragging
            ? 'border-primary bg-primary-soft'
            : 'border-line-strong bg-surface-muted'
        }`}
      >
        <span className="flex size-12 items-center justify-center rounded-xl bg-primary text-on-primary">
          {busy ? (
            <Spinner size={22} />
          ) : (
            <Upload size={22} aria-hidden="true" />
          )}
        </span>
        <span className="font-bold text-heading">{title}</span>
        <span className="text-body text-ink-muted">{description}</span>
      </label>
      <input
        {...aria}
        id={id}
        type="file"
        accept={accept}
        multiple={multiple}
        disabled={busy}
        onChange={(e) => {
          onFiles(e.target.files ? [...e.target.files] : []);
          // 같은 파일을 다시 골라도 onChange가 오도록 비운다
          e.target.value = '';
        }}
        className="sr-only"
      />
    </>
  );
}

export default FileDropZone;
