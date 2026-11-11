-- A member taking no questions for a while: their declarations are kept but read as absent by every reader but them.
CREATE TABLE public.expertise_pause (
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    paused_at timestamp with time zone NOT NULL,
    CONSTRAINT expertise_pause_pkey PRIMARY KEY (organization_id, user_id)
);

ALTER TABLE public.expertise_pause ENABLE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON public.expertise_pause
    USING (((organization_id)::text = current_setting('app.current_org'::text, true)))
    WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));
