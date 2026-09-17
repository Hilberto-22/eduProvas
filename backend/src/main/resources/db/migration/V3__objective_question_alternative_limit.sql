-- Existing MVP data is preserved. New alternatives must use one of the five supported positions.
ALTER TABLE alternative
    ADD CONSTRAINT alternative_position_between_1_and_5
    CHECK (position BETWEEN 1 AND 5) NOT VALID;
