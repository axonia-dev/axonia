-- A decline can be undone: the person who declined reopens the proposal within the decline's wait, and it lives a fresh
-- thirty days counted from its reopening.

ALTER TABLE public.mentoring_proposal ADD COLUMN reopened_at timestamp with time zone;
