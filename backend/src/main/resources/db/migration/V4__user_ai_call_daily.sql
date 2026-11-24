-- 사용자별 하루 AI 모델 호출 수 (app.ai-daily-limit.user-calls). 분석·퀴즈 만들기·AI 채점·자료 개념 뽑기·개념 설명을 모두 합쳐 센다.
-- 계정 몇 개로 서비스 전체 하루 상한(ai_daily_usage)을 다 써 버리지 못하게 한 사람의 몫을 정한다. 다시 부르기도 한 번으로 센다.
-- 날짜는 app.ai-daily-limit.zone(기본 한국 시간) 기준, 사용자·날짜마다 한 줄. 개인정보·본문은 없다.
-- users에 외래 키를 걸지 않는다: 모델을 부르는 스레드가 새 트랜잭션으로 이 줄을 만드는데, 같은 사용자 행을 잠근 요청이 그 끝을
-- 기다리면 외래 키 확인 잠금과 엇갈릴 수 있다. 대신 탈퇴할 때 지우고(WithdrawalService), 지난 날짜 줄은 주기 작업이 지운다
CREATE TABLE user_ai_call_daily (
    user_id    BIGINT NOT NULL,
    usage_date DATE   NOT NULL,
    call_count INT    NOT NULL,
    PRIMARY KEY (user_id, usage_date)
);

CREATE INDEX idx_user_ai_call_daily_usage_date ON user_ai_call_daily (usage_date);
