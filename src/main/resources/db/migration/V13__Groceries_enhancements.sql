ALTER TABLE groceries ADD COLUMN created_by_id VARCHAR(36) NULL;
ALTER TABLE groceries ADD COLUMN purchased_by_id VARCHAR(36) NULL;
ALTER TABLE groceries ADD COLUMN purchased_at TIMESTAMP NULL;
ALTER TABLE groceries ADD COLUMN previous_assignee_id VARCHAR(36) NULL;
