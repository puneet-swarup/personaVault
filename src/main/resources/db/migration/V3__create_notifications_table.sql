-- V3: In-app notifications (renewal alerts, deadline reminders).
-- The notifications table is the canonical store for the in-app channel.
-- Other channels (email, SMS) will log delivery status in a future migration.

CREATE TABLE notifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    message         TEXT NOT NULL,
    policy_id       BIGINT REFERENCES policies(id),
    due_date        DATE,
    amount          DECIMAL(12,2),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Dashboard: "unread notifications" badge
CREATE INDEX idx_notifications_unread ON notifications (is_read) WHERE is_read = FALSE;

-- Dashboard: "what needs attention" sorted by due date
CREATE INDEX idx_notifications_due ON notifications (due_date);