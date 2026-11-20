-- 서비스 전체에서 하루에 부른 AI 모델 호출 수 (비용 상한, app.ai-daily-limit)
-- 사용자·과목과 연결하지 않는다: 과목 삭제·탈퇴·재가입으로 줄어들지 않게 한다. 개인정보는 없다
-- 날짜는 app.ai-daily-limit.zone(기본 한국 시간) 기준, 하루에 한 줄
CREATE TABLE ai_daily_usage (
    usage_date DATE PRIMARY KEY,
    call_count INT  NOT NULL
);
