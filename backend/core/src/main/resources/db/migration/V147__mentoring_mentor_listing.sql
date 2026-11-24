-- Whether a mentor is listed among the mentors of a community: a null state is the default, never touched; asked_at
-- and told_at record the one-time ask and the one-time notice that they are listed by default.
CREATE TABLE public.mentoring_mentor_listing (
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    state character varying(3) CHECK (state IN ('ON', 'OFF')),
    asked_at timestamp with time zone,
    told_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT mentoring_mentor_listing_pkey PRIMARY KEY (organization_id, community_id, user_id)
);

ALTER TABLE public.mentoring_mentor_listing ENABLE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON public.mentoring_mentor_listing
    USING (((organization_id)::text = current_setting('app.current_org'::text, true)))
    WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));
