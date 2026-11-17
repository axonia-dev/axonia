-- Words wait for the end of a pairing, so nobody asks for them any more. The mentor's envelope remembers when it was
-- opened, and changed words seal it again. The hourly reminder sweep reads each organization's ongoing pairings by
-- their next meeting's day.

ALTER TABLE public.mentoring_pairing DROP COLUMN words_asked_at;

ALTER TABLE public.mentoring_words ADD COLUMN opened_at timestamp with time zone;

CREATE INDEX idx_mentoring_pairing_next_meeting
    ON public.mentoring_pairing (organization_id, next_meeting_on)
    WHERE ended_at IS NULL;
