-- B6: a resident accepted by another house gets the vacancy reserved for 72 h (AWAITING_MOVE).
ALTER TABLE match_requests ADD COLUMN move_deadline TIMESTAMP NULL;

CREATE INDEX idx_match_requests_status_deadline ON match_requests (status, move_deadline);
