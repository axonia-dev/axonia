-- A journal's topics live on a board of three columns, To do, In progress and Done. ONGOING and LONG_TERM both become
-- IN_PROGRESS, and every column is numbered densely from 0 in its old order. Two people moving stickers at once may pass
-- through a duplicate slot inside one transaction, so the slot is unique only at commit.

ALTER TABLE public.mentoring_topic DROP CONSTRAINT mentoring_topic_state_check;

ALTER TABLE public.mentoring_topic ALTER COLUMN state TYPE character varying(11);

UPDATE public.mentoring_topic SET state = 'IN_PROGRESS' WHERE state IN ('ONGOING', 'LONG_TERM');

ALTER TABLE public.mentoring_topic
    ADD CONSTRAINT mentoring_topic_state_check CHECK (state IN ('TODO', 'IN_PROGRESS', 'DONE'));

UPDATE public.mentoring_topic t
   SET "position" = slots.slot
  FROM (SELECT id, row_number() OVER (PARTITION BY pairing_id, state ORDER BY "position", id) - 1 AS slot
          FROM public.mentoring_topic) slots
 WHERE slots.id = t.id AND t."position" <> slots.slot;

ALTER TABLE public.mentoring_topic
    ADD CONSTRAINT uq_mentoring_topic_slot UNIQUE (pairing_id, state, "position") DEFERRABLE INITIALLY DEFERRED;
