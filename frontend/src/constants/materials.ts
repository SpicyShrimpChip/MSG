// 서버 허용 형식과 같은 목록 (백엔드 MaterialFileFormat). 서버는 확장자와 함께 파일 내용도 확인한다
// TODO(미결): HWP·HWPX는 백엔드 텍스트 추출(hwplib·hwpxlib)을 정한 뒤 추가한다
export const MATERIAL_EXTENSIONS = ['pdf', 'pptx', 'txt', 'md'] as const;

// 서버 한도와 같은 값 (백엔드 Material.MAX_FILE_SIZE, spring.servlet.multipart.max-file-size)
export const MATERIAL_MAX_SIZE_MB = 20;

export const MATERIAL_TITLE_MAX_LENGTH = 60;

// 자료 본문을 외부 AI로 보내는 기능이 운영 설정으로 꺼져 있을 때 (핵심 개념 뽑기·사전·사후평가)
export const CONTENT_DISABLED_MESSAGE =
  '학습자료 본문을 외부 AI로 보내는 기능은 운영 설정으로 꺼져 있어요. 처리 근거와 안내가 준비되면 켜져요. 지금은 과목 키워드로만 평가를 만들어요.';
