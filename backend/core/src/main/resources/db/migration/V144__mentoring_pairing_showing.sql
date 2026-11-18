-- A pairing shows on its community's Mentoring page once shown; pairings made before this were promised privacy,
-- so they ask both people first, and so does accepting a proposal sent before this.
ALTER TABLE public.mentoring_pairing
    ADD COLUMN visibility character varying(7) NOT NULL DEFAULT 'ASKING'
        CHECK (visibility IN ('SHOWN', 'ASKING', 'PRIVATE')),
    ADD COLUMN mentor_said_yes boolean NOT NULL DEFAULT false,
    ADD COLUMN mentee_said_yes boolean NOT NULL DEFAULT false;
ALTER TABLE public.mentoring_pairing ALTER COLUMN visibility DROP DEFAULT;

ALTER TABLE public.mentoring_proposal ADD COLUMN pairs_shown boolean NOT NULL DEFAULT false;
ALTER TABLE public.mentoring_proposal ALTER COLUMN pairs_shown DROP DEFAULT;

CREATE INDEX idx_mentoring_pairing_shown ON public.mentoring_pairing (organization_id, community_id)
    WHERE ended_at IS NULL AND visibility = 'SHOWN';
