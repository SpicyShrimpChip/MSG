// 서버 허용 형식과 같은 목록 (백엔드 MaterialFileFormat). 서버는 확장자와 함께 파일 내용도 확인한다
// TODO(미결): HWP·HWPX는 백엔드 텍스트 추출(hwplib·hwpxlib)을 정한 뒤 추가한다
export const MATERIAL_EXTENSIONS = ['pdf', 'pptx', 'txt', 'md'] as const;

// 서버 한도와 같은 값 (백엔드 Material.MAX_FILE_SIZE, spring.servlet.multipart.max-file-size)
export const MATERIAL_MAX_SIZE_MB = 20;

export const MATERIAL_TITLE_MAX_LENGTH = 60;
