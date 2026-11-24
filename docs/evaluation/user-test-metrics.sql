-- 사용자 테스트 객관 지표 집계 (읽기 전용 SELECT만, 데이터를 바꾸지 않는다).
-- 설문은 외부 설문 도구로 받고, 이 쿼리는 테스트 계정별 사용 기록만 센다(이름·학번·연락처를 쓰지 않는다).
-- 참여자 ↔ 계정 연결표(참여 코드, 집단 A·B·C, user_id)는 운영자 PC에만 두고 이 결과와 합칠 때만 쓴다.
-- 사용: psql "$DB_URL" -c "\copy (아래 SELECT) to 'user-metrics.csv' with csv header"
-- :since 대신 테스트 시작 시각을 넣는다 (예: '2026-12-01 00:00:00')
SELECT
    u.id AS user_id,
    (SELECT COUNT(*) FROM course c WHERE c.user_id = u.id) AS courses,
    (SELECT COUNT(*) FROM material m WHERE m.user_id = u.id) AS materials,
    (SELECT COUNT(*) FROM material_summary s WHERE s.user_id = u.id AND s.summary_json IS NOT NULL) AS summaries,
    (SELECT COUNT(*) FROM conversation cv WHERE cv.user_id = u.id) AS imported_conversations,
    (SELECT COUNT(*) FROM analysis_run r WHERE r.user_id = u.id AND r.status IN ('COMPLETED', 'PARTIAL')) AS analysis_runs,
    (SELECT COUNT(*) FROM quiz q WHERE q.user_id = u.id AND q.generated_provider IS NOT NULL) AS ai_quizzes,
    (SELECT COUNT(*) FROM quiz q WHERE q.user_id = u.id AND q.material_content_used = TRUE) AS material_based_quizzes,
    (SELECT COUNT(*) FROM quiz q WHERE q.user_id = u.id AND q.check_concept_key IS NOT NULL) AS check_quizzes,
    (SELECT COUNT(*) FROM concept_study_progress p WHERE p.user_id = u.id) AS studied_concepts,
    -- 최신 사전평가·그 사후평가의 최근 제출 점수 (채점이 끝난 답만, 서버가 센 값)
    (SELECT s.score FROM quiz q JOIN quiz_submission s ON s.quiz_id = q.id
       WHERE q.user_id = u.id AND q.assessment_kind = 'PRE' ORDER BY q.id DESC, s.id DESC LIMIT 1) AS pre_score,
    (SELECT s.max_score FROM quiz q JOIN quiz_submission s ON s.quiz_id = q.id
       WHERE q.user_id = u.id AND q.assessment_kind = 'PRE' ORDER BY q.id DESC, s.id DESC LIMIT 1) AS pre_max_score,
    (SELECT s.score FROM quiz q JOIN quiz_submission s ON s.quiz_id = q.id
       WHERE q.user_id = u.id AND q.assessment_kind = 'POST' ORDER BY q.id DESC, s.id DESC LIMIT 1) AS post_score,
    (SELECT s.max_score FROM quiz q JOIN quiz_submission s ON s.quiz_id = q.id
       WHERE q.user_id = u.id AND q.assessment_kind = 'POST' ORDER BY q.id DESC, s.id DESC LIMIT 1) AS post_max_score
FROM users u
ORDER BY u.id;
