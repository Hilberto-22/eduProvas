-- Run with psql against an EXCLUSIVE TEST DATABASE after Flyway V4.
-- Inserts synthetic data and compares the same queries with and without V4.
-- The final rollback restores both the data and the indexes.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO app_user(id,name,email,password_hash,role)
SELECT md5('query-professor-'||n)::uuid,'Professor '||n,'query-'||n||'@benchmark.invalid','unused','PROFESSOR'
FROM generate_series(1,1000) n;
INSERT INTO school_class(id,name,teacher_id)
SELECT md5('query-class-'||n)::uuid,'Class '||lpad(n::text,6,'0'),md5('query-professor-'||((n-1)%1000+1))::uuid
FROM generate_series(1,50000) n;
INSERT INTO assessment(id,title,teacher_id)
SELECT md5('query-assessment-'||n)::uuid,'Assessment '||lpad(n::text,6,'0'),md5('query-professor-'||((n-1)%1000+1))::uuid
FROM generate_series(1,50000) n;
INSERT INTO exam_session(id,assessment_id,class_id,code,starts_at,ends_at,duration_minutes,max_violations,violation_action)
SELECT md5('query-session-'||n)::uuid,md5('query-assessment-'||n)::uuid,md5('query-class-'||n)::uuid,
       'BENCH'||n,now(),now()+interval '1 day',60,3,'REGISTRAR'
FROM generate_series(1,50000) n;
INSERT INTO app_user(id,name,email,password_hash,role)
SELECT md5('query-student-'||n)::uuid,'Student '||n,'query-student-'||n||'@benchmark.invalid','unused','ALUNO'
FROM generate_series(1,1000) n;
INSERT INTO attempt(id,session_id,student_id,started_at,deadline,last_seen,status)
SELECT md5('query-attempt-'||n)::uuid,md5('query-session-'||n)::uuid,
       md5('query-student-'||((n-1)%1000+1))::uuid,now()-n*interval '1 minute',now(),now(),'FINALIZADA'
FROM generate_series(1,50000) n;
ANALYZE;

\echo WITH_V4_CLASSES
EXPLAIN (ANALYZE, BUFFERS) SELECT c.id,c.name,c.teacher_id,
 (SELECT count(*) FROM enrollment e WHERE e.class_id=c.id) AS students
 FROM school_class c WHERE teacher_id=md5('query-professor-1')::uuid ORDER BY name,c.id LIMIT 25;
\echo WITH_V4_ASSESSMENTS
EXPLAIN (ANALYZE, BUFFERS) SELECT a.id,a.title,a.teacher_id,
 EXISTS(SELECT 1 FROM exam_session s WHERE s.assessment_id=a.id) AS locked
 FROM assessment a WHERE teacher_id=md5('query-professor-1')::uuid ORDER BY title,a.id LIMIT 25;
\echo WITH_V4_HISTORY
EXPLAIN (ANALYZE, BUFFERS) SELECT t.id,t.status,t.deadline,a.title FROM attempt t
 JOIN exam_session s ON s.id=t.session_id JOIN assessment a ON a.id=s.assessment_id
 WHERE t.student_id=md5('query-student-1')::uuid ORDER BY t.started_at DESC,t.id LIMIT 25;

DROP INDEX school_class_teacher_name_idx;
DROP INDEX assessment_teacher_title_idx;
DROP INDEX exam_session_assessment_idx;
DROP INDEX attempt_student_started_idx;

\echo WITHOUT_V4_CLASSES
EXPLAIN (ANALYZE, BUFFERS) SELECT c.id,c.name,c.teacher_id,
 (SELECT count(*) FROM enrollment e WHERE e.class_id=c.id) AS students
 FROM school_class c WHERE teacher_id=md5('query-professor-1')::uuid ORDER BY name,c.id LIMIT 25;
\echo WITHOUT_V4_ASSESSMENTS
EXPLAIN (ANALYZE, BUFFERS) SELECT a.id,a.title,a.teacher_id,
 EXISTS(SELECT 1 FROM exam_session s WHERE s.assessment_id=a.id) AS locked
 FROM assessment a WHERE teacher_id=md5('query-professor-1')::uuid ORDER BY title,a.id LIMIT 25;
\echo WITHOUT_V4_HISTORY
EXPLAIN (ANALYZE, BUFFERS) SELECT t.id,t.status,t.deadline,a.title FROM attempt t
 JOIN exam_session s ON s.id=t.session_id JOIN assessment a ON a.id=s.assessment_id
 WHERE t.student_id=md5('query-student-1')::uuid ORDER BY t.started_at DESC,t.id LIMIT 25;
ROLLBACK;
