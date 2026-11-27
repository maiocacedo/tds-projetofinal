CREATE TABLE house_removal_requests (
    id VARCHAR(36) PRIMARY KEY,
    house_id VARCHAR(36) NOT NULL,
    target_user_id VARCHAR(36) NOT NULL,
    proposed_by_id VARCHAR(36) NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT fk_removal_req_house FOREIGN KEY (house_id) REFERENCES houses(id),
    CONSTRAINT fk_removal_req_target FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_removal_req_proposer FOREIGN KEY (proposed_by_id) REFERENCES users(id)
);

CREATE INDEX idx_removal_req_house_status ON house_removal_requests (house_id, status);
CREATE INDEX idx_removal_req_target_status ON house_removal_requests (target_user_id, status);

CREATE TABLE house_removal_votes (
    id VARCHAR(36) PRIMARY KEY,
    removal_id VARCHAR(36) NOT NULL,
    voter_id VARCHAR(36) NOT NULL,
    vote VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_removal_vote_request FOREIGN KEY (removal_id) REFERENCES house_removal_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_removal_vote_voter FOREIGN KEY (voter_id) REFERENCES users(id),
    CONSTRAINT uk_removal_vote_unique UNIQUE (removal_id, voter_id)
);

CREATE INDEX idx_removal_vote_voter ON house_removal_votes (voter_id);
