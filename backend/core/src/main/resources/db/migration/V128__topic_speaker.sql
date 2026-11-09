-- Who will present a backlog topic, apart from who asked for it. A topic's kind is OFFER exactly when it has a speaker,
-- so the kind column goes. Only a PROPOSED topic holds rows here: a scheduled or done topic reads its speakers through
-- its slot's presenters.
CREATE TABLE public.topic_speaker (
    topic_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT topic_speaker_pkey PRIMARY KEY (topic_id, user_id),
    CONSTRAINT topic_speaker_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.topic(id) ON DELETE CASCADE
);

-- An erased creator's proposer is their erasure surrogate: nobody will honour that offer, so it carries no speaker.
INSERT INTO public.topic_speaker (topic_id, user_id)
SELECT t.id, t.proposer_user_id FROM public.topic t
WHERE t.kind = 'OFFER' AND t.status = 'PROPOSED'
  AND NOT EXISTS (SELECT 1 FROM public.erasure_receipt er
                  WHERE er.organization_id = t.organization_id AND er.surrogate_id = t.proposer_user_id);

ALTER TABLE public.topic DROP COLUMN kind;
