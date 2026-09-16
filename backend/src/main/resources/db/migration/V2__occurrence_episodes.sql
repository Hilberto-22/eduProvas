ALTER TABLE occurrence ADD COLUMN counted BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX occurrence_counted_idx ON occurrence(attempt_id,created_at) WHERE counted;

