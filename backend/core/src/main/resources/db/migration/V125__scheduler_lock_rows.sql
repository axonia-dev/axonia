-- Quartz inserts its lock rows on first use. Two instances starting together on a fresh database
-- would both insert them inside their registration transactions, and the loser's transaction aborts.
INSERT INTO scheduler.qrtz_locks (sched_name, lock_name)
VALUES ('axonia', 'STATE_ACCESS'), ('axonia', 'TRIGGER_ACCESS');
