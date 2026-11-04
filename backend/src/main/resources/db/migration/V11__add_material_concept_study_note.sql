-- 자료 핵심 개념의 학습용 설명 (AI가 자료 본문 발췌를 근거로 쓴 글). 개념을 뽑은 뒤 "자세한 설명 만들기"로 채운다.
-- 개념 이름·근거 문장(V10)은 그대로 두고 설명만 더한다. 개념을 다시 뽑으면 행이 바뀌므로 설명도 함께 사라진다.
-- context_quote는 서버가 자료 본문에 실제로 있는지 확인한 문장만 저장한다(확인 못 하면 NULL).
-- H2(PostgreSQL 모드)와 PostgreSQL에서 모두 돈다

ALTER TABLE material_concept ADD COLUMN explanation VARCHAR(1200) NULL;
ALTER TABLE material_concept ADD COLUMN material_context VARCHAR(500) NULL;
ALTER TABLE material_concept ADD COLUMN context_quote VARCHAR(300) NULL;
ALTER TABLE material_concept ADD COLUMN context_location VARCHAR(40) NULL;
ALTER TABLE material_concept ADD COLUMN importance VARCHAR(500) NULL;
ALTER TABLE material_concept ADD COLUMN example VARCHAR(500) NULL;
ALTER TABLE material_concept ADD COLUMN note_provider VARCHAR(10) NULL;
ALTER TABLE material_concept ADD COLUMN note_model VARCHAR(100) NULL;
ALTER TABLE material_concept ADD COLUMN note_created_at TIMESTAMP(6) NULL;
