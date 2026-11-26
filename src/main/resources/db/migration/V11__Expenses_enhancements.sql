ALTER TABLE expenses ADD COLUMN paid_by_id VARCHAR(36) NULL;
ALTER TABLE expenses ADD COLUMN created_by_id VARCHAR(36) NULL;
ALTER TABLE expenses ADD COLUMN recurring_day INT NULL;
ALTER TABLE expenses ADD COLUMN recurrence_active BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE expenses ADD COLUMN recurrence_source_id VARCHAR(36) NULL;
ALTER TABLE expenses ADD COLUMN recurrence_month VARCHAR(7) NULL;
ALTER TABLE expenses ADD CONSTRAINT uk_expenses_recurrence_month UNIQUE (recurrence_source_id, recurrence_month);

ALTER TABLE expense_splits ADD COLUMN paid_at TIMESTAMP NULL;
ALTER TABLE expense_splits ADD COLUMN received_confirmed_at TIMESTAMP NULL;
ALTER TABLE expense_splits ADD COLUMN reopened_by_id VARCHAR(36) NULL;
ALTER TABLE expense_splits ADD COLUMN reopened_at TIMESTAMP NULL;
