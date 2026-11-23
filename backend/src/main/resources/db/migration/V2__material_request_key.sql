-- 자료 올리기 요청 키 (#210). 화면이 올리기마다 만든 키(UUID)를 함께 보내고,
-- 같은 사용자가 같은 키로 다시 보내면(더블클릭·연타·응답을 못 받은 재시도) 새로 저장하지 않고 이미 올린 자료를 돌려준다.
-- 키 없이 올린 자료(NULL)는 몇 개든 둘 수 있다 (UNIQUE는 NULL끼리 겹친다고 보지 않는다)
ALTER TABLE material ADD COLUMN request_key VARCHAR(64) NULL;
ALTER TABLE material ADD CONSTRAINT uk_material_request UNIQUE (user_id, request_key);
