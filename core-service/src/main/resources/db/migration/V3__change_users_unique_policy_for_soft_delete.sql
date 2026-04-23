-- Allow re-signup with the same login_id/email after soft delete.
-- Keep uniqueness only for active (not deleted) users.

ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_login_id;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_email;

CREATE UNIQUE INDEX uk_users_login_id_active
ON users (login_id)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_users_email_active
ON users (email)
WHERE deleted_at IS NULL;
