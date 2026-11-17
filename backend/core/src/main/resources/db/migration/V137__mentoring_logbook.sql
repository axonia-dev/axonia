-- Notes form a logbook of meetings. A note carries the day of the meeting it is about, read in its author's own zone,
-- and may name topics of its pairing. The next meeting moves from the notes to the pairing, which holds at most one,
-- with who set it and when; an ended pairing holds none.

ALTER TABLE public.mentoring_note ADD COLUMN meeting_on date;

UPDATE public.mentoring_note n
   SET meeting_on = (n.written_at AT TIME ZONE COALESCE(
           (SELECT z.name
              FROM public.identity_user u
              JOIN pg_timezone_names z ON z.name = COALESCE(u.declared_time_zone, u.observed_time_zone)
             WHERE u.organization_id = n.organization_id AND u.user_id = n.author_user_id),
           'UTC'))::date;

ALTER TABLE public.mentoring_note ALTER COLUMN meeting_on SET NOT NULL;

CREATE TABLE public.mentoring_note_topic (
    organization_id character varying(255) NOT NULL,
    note_id bigint NOT NULL REFERENCES public.mentoring_note(id) ON DELETE CASCADE,
    topic_id bigint NOT NULL REFERENCES public.mentoring_topic(id) ON DELETE CASCADE,
    CONSTRAINT mentoring_note_topic_pkey PRIMARY KEY (note_id, topic_id)
);

CREATE INDEX idx_mentoring_note_topic_topic_id ON public.mentoring_note_topic (topic_id);

ALTER TABLE public.mentoring_note_topic ENABLE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON public.mentoring_note_topic
    USING (((organization_id)::text = current_setting('app.current_org'::text, true)))
    WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));

ALTER TABLE public.mentoring_pairing
    ADD COLUMN next_meeting_on date,
    ADD COLUMN next_meeting_set_by character varying(255),
    ADD COLUMN next_meeting_set_at timestamp with time zone,
    ADD CONSTRAINT mentoring_pairing_next_meeting_check
        CHECK ((next_meeting_on IS NULL) = (next_meeting_set_at IS NULL));

UPDATE public.mentoring_pairing p
   SET next_meeting_on = latest.next_meeting_on,
       next_meeting_set_by = latest.author_user_id,
       next_meeting_set_at = latest.set_at
  FROM (SELECT DISTINCT ON (n.pairing_id) n.pairing_id, n.next_meeting_on, n.author_user_id,
               COALESCE(n.edited_at, n.written_at) AS set_at
          FROM public.mentoring_note n
         WHERE n.next_meeting_on IS NOT NULL
         ORDER BY n.pairing_id, COALESCE(n.edited_at, n.written_at) DESC, n.id DESC) latest
 WHERE latest.pairing_id = p.id AND p.ended_at IS NULL;

ALTER TABLE public.mentoring_note DROP COLUMN next_meeting_on;
