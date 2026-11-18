-- A pairing's meetings live in mentoring_meeting since V142 copied each next meeting there, so the pairing no longer
-- holds one of its own.

DROP INDEX public.idx_mentoring_pairing_next_meeting;

ALTER TABLE public.mentoring_pairing
    DROP CONSTRAINT mentoring_pairing_next_meeting_check,
    DROP COLUMN next_meeting_on,
    DROP COLUMN next_meeting_set_by,
    DROP COLUMN next_meeting_set_at;
