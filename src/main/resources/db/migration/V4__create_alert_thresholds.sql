-- V4: Per-policy-type alert thresholds.
-- Configurable without code changes or app restart.
-- Cached in memory by AlertThresholdCache; refreshed on update.

CREATE TABLE alert_thresholds (
    policy_type     VARCHAR(50) NOT NULL PRIMARY KEY,
    days_before     INTEGER NOT NULL DEFAULT 30,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Seed defaults (per ADR-012: configurable, not hardcoded)
INSERT INTO alert_thresholds (policy_type, days_before) VALUES
    ('HEALTH', 60),
    ('CAR', 30),
    ('LIFE', 90),
    ('HOME', 60),
    ('TRAVEL', 30),
    ('OTHER', 30);