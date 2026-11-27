-- B5: vacancies are independent from is_accepting_matches; pending_welcome drives the welcome screen (RF06).
ALTER TABLE houses ADD COLUMN vacancies INT NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN pending_welcome BOOLEAN NOT NULL DEFAULT FALSE;
