-- 학습자료 기반 사전평가·사후평가. 기존 퀴즈 구조(quiz·문제·제출·채점)를 그대로 쓰고 평가 종류와 연결만 더한다.
-- 기존 행은 바꾸지 않는다(일반 퀴즈는 assessment_kind NULL). H2(PostgreSQL 모드)와 PostgreSQL에서 모두 돈다

-- PRE(사전평가) / POST(사후평가). 일반 퀴즈는 NULL
ALTER TABLE quiz ADD COLUMN assessment_kind VARCHAR(10) NULL;
-- 사후평가가 비교하는 사전평가. 사전평가를 지우면 연결만 끊긴다
ALTER TABLE quiz ADD COLUMN pre_assessment_id BIGINT NULL;
ALTER TABLE quiz ADD CONSTRAINT fk_quiz_pre_assessment FOREIGN KEY (pre_assessment_id)
    REFERENCES quiz (id) ON DELETE SET NULL;
-- 학습자료 본문(발췌)을 AI에 보내 문제를 만들었는지. 기존 퀴즈는 FALSE
ALTER TABLE quiz ADD COLUMN material_content_used BOOLEAN DEFAULT FALSE NOT NULL;

CREATE INDEX idx_quiz_assessment ON quiz (user_id, material_id, assessment_kind);
CREATE INDEX idx_quiz_pre_assessment ON quiz (pre_assessment_id);
