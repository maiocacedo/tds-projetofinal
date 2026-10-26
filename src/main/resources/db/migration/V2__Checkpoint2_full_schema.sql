ALTER TABLE users ADD COLUMN name VARCHAR(255) NULL;

ALTER TABLE houses ADD COLUMN rent_amount DECIMAL(10,2) NULL;
ALTER TABLE houses ADD COLUMN allows_pets BOOLEAN DEFAULT TRUE;
ALTER TABLE houses ADD COLUMN allows_smokers BOOLEAN DEFAULT FALSE;
ALTER TABLE houses ADD COLUMN expected_organization_level INT DEFAULT 3;
ALTER TABLE houses ADD COLUMN visitor_tolerance INT DEFAULT 3;
ALTER TABLE houses ADD COLUMN has_furniture BOOLEAN DEFAULT FALSE;
ALTER TABLE houses ADD COLUMN basic_rules TEXT NULL;
ALTER TABLE houses ADD COLUMN voting_rule VARCHAR(20) NOT NULL DEFAULT 'UNANIMITY';

CREATE TABLE user_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    budget DECIMAL(10,2) NULL,
    has_pets BOOLEAN DEFAULT FALSE,
    is_smoker BOOLEAN DEFAULT FALSE,
    profession VARCHAR(255) NULL,
    bio TEXT NULL,
    allergies VARCHAR(255) NULL,
    organization_level INT NULL,
    visitor_tolerance INT NULL,
    has_furniture BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE match_requests (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    house_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_match_requests_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_match_requests_house FOREIGN KEY (house_id) REFERENCES houses(id)
);

CREATE TABLE match_votes (
    id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    voter_id VARCHAR(36) NOT NULL,
    vote VARCHAR(20) NOT NULL,
    CONSTRAINT fk_match_votes_request FOREIGN KEY (request_id) REFERENCES match_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_votes_voter FOREIGN KEY (voter_id) REFERENCES users(id),
    CONSTRAINT uk_match_votes_request_voter UNIQUE (request_id, voter_id)
);

CREATE TABLE expenses (
    id VARCHAR(36) PRIMARY KEY,
    house_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    due_date DATE NOT NULL,
    attachment_url VARCHAR(500) NULL,
    split_type VARCHAR(30) NOT NULL DEFAULT 'EQUAL',
    CONSTRAINT fk_expenses_house FOREIGN KEY (house_id) REFERENCES houses(id)
);

CREATE TABLE expense_splits (
    id VARCHAR(36) PRIMARY KEY,
    expense_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    amount_due DECIMAL(10,2) NOT NULL,
    proposed_amount_due DECIMAL(10,2) NULL,
    is_paid BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_expense_splits_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_splits_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE expense_split_votes (
    id VARCHAR(36) PRIMARY KEY,
    expense_id VARCHAR(36) NOT NULL,
    voter_id VARCHAR(36) NOT NULL,
    vote VARCHAR(20) NOT NULL,
    CONSTRAINT fk_expense_split_votes_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_split_votes_voter FOREIGN KEY (voter_id) REFERENCES users(id),
    CONSTRAINT uk_expense_split_votes_expense_voter UNIQUE (expense_id, voter_id)
);

CREATE TABLE chores (
    id VARCHAR(36) PRIMARY KEY,
    house_id VARCHAR(36) NOT NULL,
    description VARCHAR(255) NOT NULL,
    chore_type VARCHAR(20) NOT NULL,
    current_assignee_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_reset_date DATE NOT NULL,
    CONSTRAINT fk_chores_house FOREIGN KEY (house_id) REFERENCES houses(id),
    CONSTRAINT fk_chores_assignee FOREIGN KEY (current_assignee_id) REFERENCES users(id)
);

CREATE TABLE groceries (
    id VARCHAR(36) PRIMARY KEY,
    house_id VARCHAR(36) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    assignee_id VARCHAR(36) NULL,
    is_purchased BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_groceries_house FOREIGN KEY (house_id) REFERENCES houses(id),
    CONSTRAINT fk_groceries_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)
);
