-- One composed roundup thumbnail per community hero, slice and accent, so a preview and the send reuse one stored
-- image. The media key stays random: the media route's security rests on an unguessable file name.

CREATE TABLE public.roundup_thumbnail (
    id bigserial PRIMARY KEY,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    hero_key character varying(512) NOT NULL,
    slice_index integer NOT NULL,
    accent_hex character(7) NOT NULL,
    media_key character varying(512) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_roundup_thumbnail UNIQUE (organization_id, community_id, hero_key, slice_index, accent_hex)
);

ALTER TABLE public.roundup_thumbnail ENABLE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON public.roundup_thumbnail
    USING (((organization_id)::text = current_setting('app.current_org'::text, true)))
    WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));
