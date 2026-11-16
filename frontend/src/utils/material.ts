import { MATERIAL_EXTENSIONS, MATERIAL_MAX_SIZE_MB } from '@/constants';

// 지원 형식 안내 문구 (예: "PDF, TXT, Markdown")
export const MATERIAL_FORMAT_LABEL = MATERIAL_EXTENSIONS.map((ext) =>
  ext === 'md' ? 'Markdown' : ext.toUpperCase(),
).join(', ');

// 올릴 파일의 형식·크기를 검사한다. 문제가 없으면 undefined, 있으면 사용자에게 보일 문구
// 서버도 같은 검사를 해야 한다 (브라우저 검사는 우회할 수 있다)
export function validateMaterialFile(
  file: Pick<File, 'name' | 'size'>,
): string | undefined {
  const ext = file.name.includes('.')
    ? (file.name.split('.').pop()?.toLowerCase() ?? '')
    : '';
  if (!(MATERIAL_EXTENSIONS as readonly string[]).includes(ext)) {
    return `${MATERIAL_FORMAT_LABEL} 파일만 올릴 수 있어요.`;
  }
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  if (file.size > MATERIAL_MAX_SIZE_MB * 1024 * 1024) {
    return `파일은 ${MATERIAL_MAX_SIZE_MB}MB까지 올릴 수 있어요.`;
  }
  return undefined;
}

// 본문을 읽을 수 있는 형식인지 (서버 MaterialTextExtractor와 같다). 올릴 수 있는 형식은 모두 읽지만,
// PDF는 글이 들어 있는 PDF만 읽고 스캔본은 OCR이 없어 서버가 "본문 없음"으로 알려 준다
export function isReadable(fileName: string): boolean {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
  return (MATERIAL_EXTENSIONS as readonly string[]).includes(ext);
}
