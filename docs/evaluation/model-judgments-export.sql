-- 세 모델 비교 실험용 내보내기 (읽기 전용 SELECT만, 데이터를 바꾸지 않는다).
-- 저장된 대화 분석 결과를 개념·모델 한 줄씩 꺼낸다. AI를 새로 부르지 않는다.
-- 사용: psql "$DB_URL" -v ON_ERROR_STOP=1 -c "\copy (아래 SELECT) to 'judgments.csv' with csv header"
--       (운영 DB에서는 읽기 계정으로, 내보낸 파일은 개인 PC에만 두고 공유하지 않는다)
-- 사용자 대화 원문·이메일은 꺼내지 않는다. run_id·concept_key만으로 전문가 정답표와 잇는다.
SELECT
    r.id                         AS run_id,
    r.prompt_version             AS prompt_version,
    c.concept_key                AS concept_key,
    c.outcome                    AS consensus_outcome,      -- UNANIMOUS / MAJORITY / DISAGREEMENT / SINGLE_MODEL / NO_RESULT
    c.status                     AS consensus_status,       -- 다수 의견 (없으면 빈 값)
    c.judged_count               AS judged_count,
    c.called_count               AS called_count,
    m.provider                   AS provider,               -- GPT / CLAUDE / GEMINI
    m.model                      AS model,
    m.status                     AS model_status,           -- SUCCEEDED / FAILED / INVALID_OUTPUT / NOT_CONFIGURED
    j.reported_status            AS reported_status,        -- 모델이 낸 상태
    j.status                     AS judged_status,          -- 서버 근거 규칙을 거친 상태 (비교에 쓴 값)
    CASE WHEN j.misconception IS NULL THEN 0 ELSE 1 END AS misconception_flag,
    (SELECT COUNT(*) FROM analysis_judgment_evidence e WHERE e.judgment_id = j.id) AS kept_evidence,
    m.dropped_evidence_count     AS dropped_evidence_model  -- 모델 답 전체에서 버린 근거 수 (개념별이 아님)
FROM analysis_concept_consensus c
JOIN analysis_run r ON r.id = c.run_id
JOIN analysis_model_result m ON m.run_id = r.id
LEFT JOIN analysis_concept_judgment j ON j.model_result_id = m.id AND j.concept_key = c.concept_key
WHERE r.status IN ('COMPLETED', 'PARTIAL')
ORDER BY r.id, c.concept_key, m.provider;
