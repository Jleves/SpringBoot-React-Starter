ALTER TABLE users ADD COLUMN password_change_failures INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN password_change_window_start TIMESTAMP(6) NULL;
