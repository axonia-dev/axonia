-- A member's name exactly as the directory asserted it, kept beside the shown display_name, which an Organization's
-- declared name patterns may tidy into "{given} {family}". given_name is what a pattern read, null when none matched.
-- received_name stays nullable: a row written by a release that predates it reads its display_name as received.

ALTER TABLE public.identity_user
    ADD COLUMN received_name character varying(255),
    ADD COLUMN given_name character varying(255);

UPDATE public.identity_user SET received_name = display_name;

-- An Organization's ordered name patterns, such as "{family}, {given}-NORTHWIND"; the first that matches wins.
CREATE TABLE public.identity_name_pattern (
    organization_id character varying(255) NOT NULL,
    position integer NOT NULL,
    pattern character varying(255) NOT NULL,
    CONSTRAINT identity_name_pattern_pkey PRIMARY KEY (organization_id, position)
);

ALTER TABLE public.identity_name_pattern ENABLE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON public.identity_name_pattern
    USING (((organization_id)::text = current_setting('app.current_org'::text, true)))
    WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));
