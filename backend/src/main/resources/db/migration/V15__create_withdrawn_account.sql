-- 탈퇴 뒤 재가입 제한 (app.rejoin). 탈퇴·재가입을 반복해 사용자별 AI 이용 한도를 우회하는 것을 막는다
-- 이메일 원문은 남기지 않고, 정규화한 이메일에 비밀 키(REJOIN_HASH_SECRET)로 건 HMAC-SHA256 값(16진수 64자)만 둔다
-- users와 연결하지 않는다(탈퇴로 users 행은 이미 지워진다). 재가입 가능 시각이 지나면 주기 작업이 지운다
CREATE TABLE withdrawn_account (
    email_hash        VARCHAR(64)  PRIMARY KEY,
    withdrawn_at      TIMESTAMP(6) NOT NULL,
    rejoin_allowed_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_withdrawn_account_rejoin_allowed_at ON withdrawn_account (rejoin_allowed_at);
