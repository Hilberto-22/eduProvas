CREATE TABLE app_user (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, email VARCHAR(254) UNIQUE NOT NULL,
 password_hash TEXT NOT NULL, role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','PROFESSOR','ALUNO'))
);
CREATE TABLE school_class (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, teacher_id UUID NOT NULL REFERENCES app_user(id)
);
CREATE TABLE enrollment (
 class_id UUID REFERENCES school_class(id), student_id UUID REFERENCES app_user(id),
 PRIMARY KEY(class_id, student_id)
);
CREATE TABLE assessment (
 id UUID PRIMARY KEY, title VARCHAR(200) NOT NULL, teacher_id UUID NOT NULL REFERENCES app_user(id)
);
CREATE TABLE question (
 id UUID PRIMARY KEY, assessment_id UUID NOT NULL REFERENCES assessment(id),
 prompt TEXT NOT NULL, kind VARCHAR(20) NOT NULL CHECK(kind IN ('OBJETIVA','DISCURSIVA')),
 points NUMERIC(8,2) NOT NULL CHECK(points > 0), position INTEGER NOT NULL,
 UNIQUE(assessment_id, position)
);
CREATE TABLE alternative (
 id UUID PRIMARY KEY, question_id UUID NOT NULL REFERENCES question(id),
 label TEXT NOT NULL, correct BOOLEAN NOT NULL DEFAULT FALSE, position INTEGER NOT NULL,
 UNIQUE(question_id, position), UNIQUE(id,question_id)
);
CREATE TABLE exam_session (
 id UUID PRIMARY KEY, assessment_id UUID NOT NULL REFERENCES assessment(id),
 class_id UUID NOT NULL REFERENCES school_class(id), code VARCHAR(16) UNIQUE NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'RASCUNHO' CHECK(status IN ('RASCUNHO','PUBLICADA')),
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 1 AND 480),
 max_violations INTEGER NOT NULL CHECK(max_violations BETWEEN 1 AND 100),
 violation_action VARCHAR(16) NOT NULL CHECK(violation_action IN ('REGISTRAR','FINALIZAR')),
 CHECK(ends_at > starts_at)
);
CREATE TABLE attempt (
 id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES exam_session(id), student_id UUID NOT NULL REFERENCES app_user(id),
 status VARCHAR(16) NOT NULL DEFAULT 'EM_ANDAMENTO' CHECK(status IN ('EM_ANDAMENTO','FINALIZADA')),
 started_at TIMESTAMPTZ NOT NULL, deadline TIMESTAMPTZ NOT NULL, submitted_at TIMESTAMPTZ,
 finish_reason VARCHAR(32), last_seen TIMESTAMPTZ NOT NULL, UNIQUE(session_id,student_id)
);
CREATE TABLE answer (
 attempt_id UUID NOT NULL REFERENCES attempt(id), question_id UUID NOT NULL REFERENCES question(id),
 alternative_id UUID, text_value TEXT, score NUMERIC(8,2) CHECK(score >= 0), feedback TEXT,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(attempt_id,question_id),
 FOREIGN KEY(alternative_id,question_id) REFERENCES alternative(id,question_id)
);
CREATE TABLE occurrence (
 id UUID PRIMARY KEY, attempt_id UUID NOT NULL REFERENCES attempt(id),
 kind VARCHAR(32) NOT NULL CHECK(kind IN ('FOCO_PERDIDO','ABA_OCULTA','SAIDA_FULLSCREEN')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX attempt_deadline_idx ON attempt(deadline) WHERE status='EM_ANDAMENTO';
CREATE INDEX occurrence_attempt_idx ON occurrence(attempt_id);
CREATE INDEX question_assessment_idx ON question(assessment_id);
