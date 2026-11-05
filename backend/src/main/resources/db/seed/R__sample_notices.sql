-- 로컬 확인용 샘플 공지 (프론트 목업 frontend/src/mocks/notices.ts와 같다). 운영(prod) 프로필에서는 실행하지 않는다.
-- 반복 실행(R__) 마이그레이션이라 내용이 바뀌면 다시 돈다. 같은 제목이 있으면 넣지 않는다
INSERT INTO notice (title, created_at)
SELECT '서비스를 시작했어요', '2026-10-15'
WHERE NOT EXISTS (SELECT 1 FROM notice WHERE title = '서비스를 시작했어요');

INSERT INTO notice (title, created_at)
SELECT '개인정보 처리 방식을 안내해 드려요', '2026-10-20'
WHERE NOT EXISTS (SELECT 1 FROM notice WHERE title = '개인정보 처리 방식을 안내해 드려요');

-- 대화 가져오기가 생겨 옛 "준비 중" 샘플 공지를 새 제목·본문으로 바꾼다 (관리자가 고치지 않은 샘플 그대로인 것만).
-- 제목만 바꿔 넣으면 옛 행이 남으므로 지우거나 새로 넣지 않고 같은 행을 고친다
UPDATE notice SET title = 'AI 대화 가져오기를 열었어요', content = 'ChatGPT, Claude에서 내보낸 대화 파일을 가져올 수 있어요. 파일은 내 브라우저에서 읽고, 공부한 대화 중 내가 고른 것만 개인정보를 가린 뒤 보내요. 메인 화면의 "AI 대화 가져오기"에서 시작해 보세요.'
WHERE title = 'AI 대화 가져오기 기능을 준비하고 있어요'
  AND (content IS NULL OR content = 'ChatGPT, Claude와 공부한 대화를 가져와 개념별로 분석하는 기능을 준비하고 있어요. 준비가 끝나면 이곳에서 알려 드릴게요.');

INSERT INTO notice (title, created_at)
SELECT 'AI 대화 가져오기를 열었어요', '2026-10-24'
WHERE NOT EXISTS (SELECT 1 FROM notice WHERE title = 'AI 대화 가져오기를 열었어요');

-- 본문은 V3에서 추가됐다. 이미 들어 있는 샘플 공지에도 채운다 (본문이 비어 있는 것만)
UPDATE notice SET content = 'AI 학습 도우미 서비스를 시작했어요. 학습 자료와 AI 대화 기록을 함께 분석해 개념별 강점과 보완할 점을 보여 드려요.'
WHERE title = '서비스를 시작했어요' AND content IS NULL;

UPDATE notice SET content = 'AI 대화 파일은 내 브라우저에서만 읽고, 내가 고른 대화만 개인정보를 가린 뒤 서버로 보내요. 자세한 내용은 이용안내의 개인정보 처리방침에서 볼 수 있어요.'
WHERE title = '개인정보 처리 방식을 안내해 드려요' AND content IS NULL;

UPDATE notice SET content = 'ChatGPT, Claude에서 내보낸 대화 파일을 가져올 수 있어요. 파일은 내 브라우저에서 읽고, 공부한 대화 중 내가 고른 것만 개인정보를 가린 뒤 보내요. 메인 화면의 "AI 대화 가져오기"에서 시작해 보세요.'
WHERE title = 'AI 대화 가져오기를 열었어요' AND content IS NULL;
