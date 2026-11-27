CREATE TABLE point_ledger (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    house_id VARCHAR(36) NULL,
    reason VARCHAR(40) NOT NULL,
    amount INT NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    cycle_key VARCHAR(40) NOT NULL DEFAULT '',
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_point_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_point_ledger_origin UNIQUE (source_type, source_id, cycle_key)
);

CREATE INDEX idx_point_ledger_user_created ON point_ledger (user_id, created_at);
CREATE INDEX idx_point_ledger_house_created ON point_ledger (house_id, created_at);
