CREATE TABLE residence_history (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    house_id VARCHAR(36) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    left_at TIMESTAMP NULL,
    CONSTRAINT fk_residence_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_residence_history_house FOREIGN KEY (house_id) REFERENCES houses(id)
);

CREATE INDEX idx_residence_history_user ON residence_history (user_id);

INSERT INTO residence_history (id, user_id, house_id, joined_at)
SELECT CONCAT('seed-', u.id), u.id, u.house_id, COALESCE(u.created_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.house_id IS NOT NULL;
