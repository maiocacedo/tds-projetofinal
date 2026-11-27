ALTER TABLE chores ADD COLUMN frequency VARCHAR(20) NOT NULL DEFAULT 'ONCE';
ALTER TABLE chores ADD COLUMN completions_this_cycle INT NOT NULL DEFAULT 0;
ALTER TABLE chores ADD COLUMN delayed_this_cycle BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chores ADD COLUMN linked_to_groceries BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chores ADD COLUMN rotation_order TEXT NULL;
ALTER TABLE chores ADD COLUMN cycle_number INT NOT NULL DEFAULT 1;
ALTER TABLE chores ADD COLUMN weeks_in_cycle INT NOT NULL DEFAULT 0;
ALTER TABLE chores ADD COLUMN last_closed_week VARCHAR(10) NULL;
ALTER TABLE chores ADD COLUMN reopened_by_id VARCHAR(36) NULL;
ALTER TABLE chores ADD COLUMN reopened_at TIMESTAMP NULL;
ALTER TABLE chores ADD COLUMN temporary_assignee_id VARCHAR(36) NULL;

CREATE TABLE chore_swaps (
    id VARCHAR(36) PRIMARY KEY,
    house_id VARCHAR(36) NOT NULL,
    requester_chore_id VARCHAR(36) NOT NULL,
    target_chore_id VARCHAR(36) NOT NULL,
    requester_id VARCHAR(36) NOT NULL,
    target_user_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cycle_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chore_swaps_house FOREIGN KEY (house_id) REFERENCES houses(id),
    CONSTRAINT fk_chore_swaps_requester_chore FOREIGN KEY (requester_chore_id) REFERENCES chores(id) ON DELETE CASCADE,
    CONSTRAINT fk_chore_swaps_target_chore FOREIGN KEY (target_chore_id) REFERENCES chores(id) ON DELETE CASCADE,
    CONSTRAINT fk_chore_swaps_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_chore_swaps_target_user FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE INDEX idx_chore_swaps_house_status ON chore_swaps (house_id, status);
CREATE INDEX idx_chore_swaps_requester_chore ON chore_swaps (requester_chore_id);
CREATE INDEX idx_chore_swaps_target_chore ON chore_swaps (target_chore_id);
