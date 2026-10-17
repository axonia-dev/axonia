-- When a member last viewed My impact in a community: the start of the next replay of loves received.
CREATE TABLE public.impact_visit (
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    seen_at timestamp with time zone NOT NULL,
    CONSTRAINT impact_visit_pkey PRIMARY KEY (organization_id, user_id, community_id)
);

ALTER TABLE public.impact_visit ENABLE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON public.impact_visit
    USING (((organization_id)::text = current_setting('app.current_org'::text, true)))
    WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));
