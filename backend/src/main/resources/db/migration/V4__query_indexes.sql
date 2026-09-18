-- Equality filters first; ordering columns support stable paginated reads.
CREATE INDEX school_class_teacher_name_idx ON school_class(teacher_id,name,id);
CREATE INDEX assessment_teacher_title_idx ON assessment(teacher_id,title,id);
CREATE INDEX exam_session_assessment_idx ON exam_session(assessment_id);
CREATE INDEX attempt_student_started_idx ON attempt(student_id,started_at DESC,id);
CREATE INDEX app_user_name_idx ON app_user(name,id);
CREATE INDEX occurrence_attempt_created_idx ON occurrence(attempt_id,created_at);
