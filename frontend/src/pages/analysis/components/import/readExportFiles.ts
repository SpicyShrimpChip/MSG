import {
  mergeParseResults,
  parseConversationExport,
  type FileParseResult,
  type MergedImport,
} from '@/utils';
import {
  isKnownNonConversationFile,
  readExportZip,
} from '@/utils/conversation/archive';

// 고른 파일들을 브라우저 안에서만 읽는다. 서버로는 아무것도 보내지 않는다.
// zip은 압축을 풀지 않고 올려도 되고, 안에서 대화 기록 파일만 꺼내 읽는다.
// 큰 zip도 필요한 부분만 잘라 읽어서 통째로 메모리에 올리지 않는다
// TODO: 파일이 수십 MB를 넘으면 화면이 잠깐 멈출 수 있다. 실제 파일로 확인한 뒤 필요하면 Web Worker로 옮긴다
export async function readExportFiles(files: File[]): Promise<MergedImport> {
  const results: FileParseResult[] = [];
  // 대화가 없다고 알려진 파일 (조용히 건너뛴다)
  const skippedFiles: string[] = [];
  // 끝이 잘린 zip과 거기서 읽은 대화 파일 수
  const truncatedZips: { fileName: string; readCount: number }[] = [];

  for (const file of files) {
    if (file.name.toLowerCase().endsWith('.zip')) {
      const zip = await readExportZip(file);
      if (!zip.ok) {
        results.push({ fileName: file.name, result: zip });
        continue;
      }
      let readCount = 0;
      for (const entry of zip.files) {
        const result = parseConversationExport(entry.name, entry.text);
        if (result.ok) readCount += result.conversations.length;
        results.push({ fileName: file.name, result });
      }
      if (zip.truncated) truncatedZips.push({ fileName: file.name, readCount });
      continue;
    }
    if (isKnownNonConversationFile(file.name)) {
      skippedFiles.push(file.name);
      continue;
    }
    results.push({
      fileName: file.name,
      result: parseConversationExport(file.name, await file.text()),
    });
  }

  const merged = mergeParseResults(results);
  const count = merged.conversations.length;
  const notices: MergedImport['notices'] = [];

  for (const { fileName, readCount } of truncatedZips) {
    notices.push({
      fileName,
      message: `파일이 잘려 있어요. 대화 ${readCount}개는 읽었어요. 빠진 대화가 있을 수 있어요.`,
    });
  }
  if (skippedFiles.length > 0) {
    notices.push(
      count > 0
        ? {
            fileName: skippedFiles.join(', '),
            message: '대화가 들어 있지 않은 파일이라 건너뛰었어요.',
          }
        : {
            fileName: skippedFiles.join(', '),
            message:
              '이 파일에는 대화가 없어요. ChatGPT는 conversations-000.json(또는 zip), Claude는 conversations-000.zip을 올려 주세요.',
          },
    );
  }
  return { ...merged, notices };
}
