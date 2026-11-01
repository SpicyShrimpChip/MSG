import { useState } from 'react';
import { CircleAlert, Download, FileText, Info, Lock } from 'lucide-react';
import { Button, Card, FileDropZone, Notice } from '@/components';
import type { ClaudeManifest, MergedImport } from '@/utils';
import { readExportFiles } from './readExportFiles';

interface FileStepProps {
  imported: MergedImport | null;
  onLoaded: (imported: MergedImport) => void;
  onNext: () => void;
}

// 시각을 "11월 1일 오후 7:08"처럼 (내 컴퓨터 시간대 기준)
function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

// 파일 이름은 작은 회색 글씨로 따로. 길면 말줄임표로 자른다
function FileName({ name }: { name: string }) {
  return (
    <p className="flex min-w-0 items-center gap-1.5 text-caption text-ink-muted">
      <FileText size={14} className="shrink-0" aria-hidden="true" />
      <span className="truncate" title={name}>
        {name}
      </span>
    </p>
  );
}

// Claude 다운로드 목록 파일을 올렸을 때: 오류가 아니라 대화 기록 zip을 받는 안내
function ManifestNotice({
  fileName,
  title,
  message,
  manifest,
}: {
  fileName: string;
  title: string;
  message: string;
  manifest: ClaudeManifest;
}) {
  // 안내가 처음 뜬 시각 기준으로 만료를 판단한다
  const [shownAt] = useState(() => Date.now());
  const expired =
    manifest.expiresAt !== undefined &&
    Date.parse(manifest.expiresAt) <= shownAt;

  return (
    <div className="flex flex-col gap-2 rounded-xl border border-line-strong bg-surface-raised p-4">
      <FileName name={fileName} />
      <p className="font-bold text-heading">{title}</p>
      {expired ? (
        <p className="text-body text-ink-soft">
          이 목록의 링크는 만든 지 24시간이 지나 쓸 수 없어요. claude.ai 설정의
          개인정보 보호에서 데이터 내보내기를 다시 요청해 주세요.
        </p>
      ) : manifest.downloadUrl ? (
        <>
          <p className="text-body text-ink-soft">{message}</p>
          <div className="flex flex-col items-start gap-1.5 pt-1">
            <Button href={manifest.downloadUrl}>
              <Download size={18} aria-hidden="true" />
              {manifest.fileName} 받기
            </Button>
            <p className="text-caption text-ink-muted">
              {manifest.expiresAt &&
                `${formatDateTime(manifest.expiresAt)}까지 받을 수 있어요 · `}
              한 번 열면 다시 안 열릴 수 있으니 받기가 끝날 때까지 기다려
              주세요. 링크는 claude.ai로 바로 연결돼요.
            </p>
          </div>
        </>
      ) : (
        <p className="text-body text-ink-soft">
          이 파일에서 대화 기록 링크를 찾지 못했어요. 내보내기 메일의 링크를
          열어 {manifest.fileName}을 받은 뒤 그 zip을 올려 주세요.
        </p>
      )}
    </div>
  );
}

function FileStep({ imported, onLoaded, onNext }: FileStepProps) {
  const [reading, setReading] = useState(false);

  const handleFiles = async (files: File[]) => {
    if (files.length === 0) return;
    setReading(true);
    try {
      onLoaded(await readExportFiles(files));
    } finally {
      setReading(false);
    }
  };

  const count = imported?.conversations.length ?? 0;

  return (
    <Card
      as="section"
      aria-labelledby="import-file"
      className="flex flex-col gap-5 p-6 md:p-8"
    >
      <h2 id="import-file" className="text-xl font-extrabold text-heading">
        대화 내보내기 파일 고르기
      </h2>

      <Notice icon={Lock} iconClassName="text-primary">
        <p>
          파일은 서버로 올라가지 않아요. 압축 파일도 이 브라우저 안에서 열고,
          공부한 대화만 골라 이메일·전화번호 같은 개인정보를 가린 뒤 내가 선택한
          대화만 보내요.
        </p>
      </Notice>

      <FileDropZone
        id="conversation-file"
        accept=".zip,.json,application/zip,application/json"
        multiple
        busy={reading}
        title={
          reading
            ? '파일을 읽는 중이에요'
            : '내보낸 zip 파일을 여기에 놓거나 눌러서 고르세요'
        }
        description="ChatGPT, Claude에서 받은 zip을 압축 풀지 않고 올려도 돼요. 압축을 푼 conversations.json도 괜찮아요."
        onFiles={(files) => void handleFiles(files)}
      />

      <div aria-live="polite" className="flex flex-col gap-3">
        {imported?.errors.map((error) =>
          error.manifest ? (
            <ManifestNotice
              key={`${error.fileName}:${error.title}`}
              fileName={error.fileName}
              title={error.title}
              message={error.message}
              manifest={error.manifest}
            />
          ) : (
            <div
              key={`${error.fileName}:${error.title}`}
              role="alert"
              className="flex flex-col gap-1.5 rounded-xl border border-danger p-4"
            >
              <FileName name={error.fileName} />
              <p className="flex items-center gap-2 font-bold text-heading">
                <CircleAlert
                  size={18}
                  className="shrink-0 text-danger"
                  aria-hidden="true"
                />
                {error.title}
              </p>
              <p className="text-body text-ink-soft">{error.message}</p>
            </div>
          ),
        )}
        {imported?.notices.map((notice) => (
          <Notice
            key={`${notice.fileName}:${notice.message}`}
            icon={Info}
            size="caption"
          >
            <div className="flex min-w-0 flex-col gap-1">
              <FileName name={notice.fileName} />
              <p>{notice.message}</p>
            </div>
          </Notice>
        ))}
        {count > 0 && (
          <div className="flex flex-col gap-1 rounded-xl bg-primary-soft p-4">
            <p className="font-bold text-heading">대화 {count}개를 읽었어요.</p>
            {(imported!.skippedCount > 0 || imported!.duplicateCount > 0) && (
              <p className="text-body text-ink-soft">
                {imported!.skippedCount > 0 &&
                  `글이 없는 대화 ${imported!.skippedCount}개는 건너뛰었어요. `}
                {imported!.duplicateCount > 0 &&
                  `여러 파일에 같은 대화 ${imported!.duplicateCount}개가 있어 하나로 합쳤어요.`}
              </p>
            )}
          </div>
        )}
      </div>

      <div className="flex justify-end">
        <Button onClick={onNext} disabled={count === 0 || reading}>
          다음: 공부한 대화 고르기
        </Button>
      </div>
    </Card>
  );
}

export default FileStep;
