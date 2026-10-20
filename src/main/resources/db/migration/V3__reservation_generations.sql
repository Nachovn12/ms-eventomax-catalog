-- Retain tombstones: deleting them would allow delayed requests to resurrect stock reservations.
CREATE TABLE reservation_generations (
    production_id BIGINT PRIMARY KEY,
    generation BIGINT NOT NULL CHECK (generation > 0),
    closed BOOLEAN NOT NULL
);
