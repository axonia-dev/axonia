-- =============================================================================
-- The schema of the open core at version 119, where the Self-hosted edition starts.
--
-- Generated, never written by hand and never edited: a database migrated through
-- the project's history up to version 119, reduced to the tables the open core
-- owns, and dumped with pg_dump (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2).
-- Every migration after it is numbered above 119 and lives in db/migration.
--
-- Structure:
--   1. the two database roles the application connects as, and their default privileges
--   2. tables, sequences, constraints, indexes, row-level security and policies
--   3. the sequences the history left advanced, the column grant of the
--      tenant-discovery role, and a closing privilege sweep
--
-- The file seeds no row. An Organization, its roles and its catalogues are written
-- by the application when the Organization is founded.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Roles. The migration runs as the schema owner, which keeps DDL rights and
--    bypasses row-level security by ownership. axonia_app is the runtime role and
--    is subject to every policy below. axonia_communication_read is the
--    tenant-discovery role: it lists the Organizations and reads nothing else.
--    Both passwords are Flyway placeholders and carry no default. Write no other
--    token of that shape in this file, not even in a comment: Flyway substitutes
--    them textually and refuses one it has no value for. Each is dollar-quoted
--    with a tag of its own, inside a block with a tag of its own, so a password
--    may hold quotes, backslashes and dollar signs; only its own tag would end it.
--    Flyway does not replace a placeholder that directly follows a dollar sign, so
--    one space separates it from the opening tag, and substr drops that space.
-- -----------------------------------------------------------------------------
DO $create_app_role$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'axonia_app') THEN
    EXECUTE format('CREATE ROLE axonia_app LOGIN PASSWORD %L',
      substr($app_password$ ${app-password}$app_password$, 2));
  END IF;
END
$create_app_role$;

GRANT USAGE ON SCHEMA public TO axonia_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO axonia_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO axonia_app;

DO $create_communication_read_role$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'axonia_communication_read') THEN
    EXECUTE format('CREATE ROLE axonia_communication_read LOGIN PASSWORD %L',
      substr($communication_read_password$ ${communication-read-password}$communication_read_password$, 2));
  END IF;
END
$create_communication_read_role$;

GRANT USAGE ON SCHEMA public TO axonia_communication_read;

-- -----------------------------------------------------------------------------
-- 2. Schema and row-level security.
-- -----------------------------------------------------------------------------
--
-- PostgreSQL database dump
--






--
-- Name: action_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_item (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    title character varying(255) NOT NULL,
    description text NOT NULL,
    raised_by_user_id character varying(255) NOT NULL,
    assignee_user_id character varying(255),
    due_date date,
    origin_session_id bigint,
    checkpoint_session_id bigint,
    outcome character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: action_item_checkpoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_item_checkpoint (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    action_item_id bigint NOT NULL,
    session_id bigint NOT NULL,
    outcome character varying(20) NOT NULL,
    decided_by_user_id character varying(255) NOT NULL,
    decided_at timestamp with time zone NOT NULL
);


--
-- Name: action_item_checkpoint_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.action_item_checkpoint_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: action_item_checkpoint_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.action_item_checkpoint_id_seq OWNED BY public.action_item_checkpoint.id;


--
-- Name: action_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.action_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: action_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.action_item_id_seq OWNED BY public.action_item.id;


--
-- Name: action_item_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_item_tag (
    action_item_id bigint NOT NULL,
    tag character varying(50) NOT NULL
);


--
-- Name: agent_connection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_connection (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    client_id character varying(2048) NOT NULL,
    client_name character varying(100) NOT NULL,
    client_host character varying(253) NOT NULL,
    scopes character varying(512) NOT NULL,
    resource character varying(2048) NOT NULL,
    redirect_uri character varying(2048) NOT NULL,
    code_challenge character varying(64) NOT NULL,
    activated_at timestamp with time zone,
    last_used_at timestamp with time zone,
    ends_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: agent_connection_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.agent_connection ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.agent_connection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: agent_connection_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_connection_token (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    connection_id bigint NOT NULL,
    kind character varying(16) NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_agent_connection_token_kind CHECK (kind IN ('CODE', 'ACCESS', 'REFRESH'))
);


--
-- Name: agent_connection_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.agent_connection_token ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.agent_connection_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: agent_tool_call; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_tool_call (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    token_id bigint,
    tool_name character varying(64) NOT NULL,
    outcome character varying(32) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    connection_id bigint,
    CONSTRAINT ck_agent_tool_call_one_credential CHECK ((num_nonnulls(token_id, connection_id) = 1))
);


--
-- Name: agent_tool_call_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.agent_tool_call_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: agent_tool_call_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.agent_tool_call_id_seq OWNED BY public.agent_tool_call.id;


--
-- Name: contribution_love; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contribution_love (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    recognized_contribution_id bigint NOT NULL,
    lover_user_id character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: appreciation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appreciation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appreciation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.appreciation_id_seq OWNED BY public.contribution_love.id;


--
-- Name: artifact; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifact (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    type_id bigint NOT NULL,
    title character varying(300) NOT NULL,
    description text NOT NULL,
    url character varying(2048) NOT NULL,
    contributor_user_id character varying(255) NOT NULL,
    provenance_session_id bigint,
    provenance_segment_id bigint,
    provenance_label character varying(500),
    status character varying(20) NOT NULL,
    search_document text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('english'::regconfig, search_document)) STORED,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    love_count integer DEFAULT 0 NOT NULL
);


--
-- Name: artifact_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifact_attachment (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    artifact_id bigint NOT NULL,
    session_id bigint NOT NULL,
    segment_id bigint,
    attached_by_user_id character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: artifact_attachment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artifact_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artifact_attachment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artifact_attachment_id_seq OWNED BY public.artifact_attachment.id;


--
-- Name: artifact_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artifact_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artifact_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artifact_id_seq OWNED BY public.artifact.id;


--
-- Name: artifact_opening; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifact_opening (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    artifact_id bigint NOT NULL,
    member_user_id character varying(255) NOT NULL,
    opened_on date NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: artifact_opening_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artifact_opening_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artifact_opening_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artifact_opening_id_seq OWNED BY public.artifact_opening.id;


--
-- Name: artifact_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifact_tag (
    artifact_id bigint NOT NULL,
    tag character varying(50) NOT NULL
);


--
-- Name: artifact_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifact_type (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    label character varying(60) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    icon character varying(60),
    accent_color character varying(7)
);


--
-- Name: artifact_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artifact_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artifact_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artifact_type_id_seq OWNED BY public.artifact_type.id;


--
-- Name: bookmark; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bookmark (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    saveable_type character varying(32) NOT NULL,
    saveable_id bigint NOT NULL,
    member_user_id character varying(255) NOT NULL,
    source_author_user_id character varying(255),
    helped boolean,
    helped_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: bookmark_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bookmark_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bookmark_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bookmark_id_seq OWNED BY public.bookmark.id;


--
-- Name: calendar_subscription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calendar_subscription (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    token_hash character varying(64) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    last_fetched_at timestamp with time zone,
    last_fetched_agent character varying(255),
    version bigint NOT NULL
);


--
-- Name: calendar_subscription_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calendar_subscription_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calendar_subscription_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calendar_subscription_id_seq OWNED BY public.calendar_subscription.id;


--
-- Name: communication_delivery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communication_delivery (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    message_id bigint NOT NULL,
    user_id character varying(255),
    status character varying(16) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    claimed_at timestamp with time zone,
    sent_at timestamp with time zone,
    suppressed_reason text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    channel character varying(8) DEFAULT 'EMAIL'::character varying NOT NULL,
    chat_destination_id bigint,
    read_at timestamp with time zone,
    CONSTRAINT communication_delivery_recipient_shape CHECK (((((channel)::text = 'EMAIL'::text) AND (user_id IS NOT NULL)) OR (((channel)::text = 'CHAT'::text) AND (user_id IS NULL)) OR (((channel)::text = 'IN_APP'::text) AND (user_id IS NOT NULL) AND (chat_destination_id IS NULL))))
);


--
-- Name: communication_delivery_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.communication_delivery ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.communication_delivery_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: communication_expired_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communication_expired_message (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    message_type character varying(32) NOT NULL,
    subject_ref character varying(255) NOT NULL,
    pending_count integer NOT NULL,
    sent_count integer NOT NULL,
    suppressed_count integer NOT NULL,
    unreachable_count integer NOT NULL,
    abandoned_count integer NOT NULL,
    expired_at timestamp with time zone NOT NULL
);


--
-- Name: communication_expired_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.communication_expired_message ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.communication_expired_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: communication_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communication_message (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    message_type character varying(32) NOT NULL,
    subject_ref character varying(255) NOT NULL,
    template character varying(255) NOT NULL,
    facts jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: communication_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.communication_message ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.communication_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: communication_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communication_preference (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    message_type character varying(32) NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: communication_preference_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.communication_preference ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.communication_preference_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: communication_production_watermark; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communication_production_watermark (
    organization_id character varying(255) NOT NULL,
    producer_key character varying(64) NOT NULL,
    produced_through timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: communities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communities (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    slug character varying(120) NOT NULL,
    name character varying(255) NOT NULL,
    description text NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    kind_id bigint NOT NULL,
    can_start_series boolean DEFAULT false NOT NULL,
    can_edit_structure boolean DEFAULT false NOT NULL,
    can_queue_topics boolean DEFAULT false NOT NULL,
    badge_key character varying(512),
    hero_key character varying(512),
    accent_color character varying(7),
    can_manage_schedule boolean DEFAULT false NOT NULL,
    topic_management_policy character varying(20) DEFAULT 'ORGANIZER_ONLY'::character varying NOT NULL,
    can_edit_community boolean DEFAULT false NOT NULL,
    can_manage_look_and_feel boolean DEFAULT false NOT NULL,
    can_manage_members boolean DEFAULT false NOT NULL,
    can_manage_contribution_policy boolean DEFAULT false NOT NULL,
    can_archive_community boolean DEFAULT false NOT NULL,
    can_curate_tags boolean DEFAULT false NOT NULL,
    can_view_health boolean DEFAULT false NOT NULL,
    can_invite_members boolean DEFAULT false NOT NULL,
    can_manage_communication boolean DEFAULT false NOT NULL,
    notify_session_upcoming boolean DEFAULT true NOT NULL,
    notify_session_wrap_up boolean DEFAULT true NOT NULL,
    session_upcoming_lead_days integer DEFAULT 3 NOT NULL,
    wrap_up_grace_hours integer DEFAULT 48 NOT NULL,
    hero_focus_x smallint,
    hero_focus_y smallint,
    badge_focus_x smallint,
    badge_focus_y smallint,
    can_credit_contributions boolean DEFAULT false NOT NULL,
    CONSTRAINT communities_grace_hours_sane CHECK (((wrap_up_grace_hours >= 0) AND (wrap_up_grace_hours <= 336))),
    CONSTRAINT communities_lead_days_sane CHECK (((session_upcoming_lead_days >= 0) AND (session_upcoming_lead_days <= 30)))
);


--
-- Name: communities_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.communities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: communities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.communities_id_seq OWNED BY public.communities.id;


--
-- Name: community_chat_destination; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.community_chat_destination (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    name character varying(80) NOT NULL,
    webhook_url text NOT NULL,
    broken boolean DEFAULT false NOT NULL,
    consecutive_failures integer DEFAULT 0 NOT NULL,
    last_posted_at timestamp with time zone,
    last_failure_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: community_chat_destination_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.community_chat_destination ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.community_chat_destination_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: community_kind; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.community_kind (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    label character varying(60) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: community_kind_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.community_kind_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: community_kind_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.community_kind_id_seq OWNED BY public.community_kind.id;


--
-- Name: community_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.community_session (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    series_id bigint,
    community_id bigint NOT NULL,
    status character varying(20) NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    duration_minutes integer,
    facilitator_user_id character varying(255),
    notes text NOT NULL,
    recording_url character varying(2048) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    occurrence_date date,
    title character varying(255),
    closed_at timestamp with time zone,
    wrap_up_release_at timestamp with time zone,
    upcoming_release_at timestamp with time zone,
    join_url character varying(2048),
    location character varying(512),
    joining_details character varying(2048),
    has_time boolean NOT NULL,
    time_zone character varying(64),
    CONSTRAINT ck_community_session_series_has_anchor CHECK (((series_id IS NULL) OR (occurrence_date IS NOT NULL)))
);


--
-- Name: community_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.community_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: community_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.community_session_id_seq OWNED BY public.community_session.id;


--
-- Name: contribution_reaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contribution_reaction (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    contribution_type character varying(32) NOT NULL,
    contribution_id bigint NOT NULL,
    reactor_user_id character varying(255) NOT NULL,
    emoji character varying(32),
    definition_id bigint,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint,
    CONSTRAINT ck_contribution_reaction_one_arm CHECK ((((emoji IS NOT NULL) AND (definition_id IS NULL)) OR ((emoji IS NULL) AND (definition_id IS NOT NULL))))
);


--
-- Name: contribution_reaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.contribution_reaction ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.contribution_reaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: directory_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.directory_group (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    scim_id character varying(64) NOT NULL,
    external_id character varying(255),
    display_name character varying(255) NOT NULL,
    reconciliation_key character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    confirmed_at timestamp with time zone
);


--
-- Name: directory_group_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.directory_group_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: directory_group_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.directory_group_id_seq OWNED BY public.directory_group.id;


--
-- Name: directory_group_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.directory_group_member (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    group_id bigint NOT NULL,
    user_scim_id character varying(64) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: directory_group_member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.directory_group_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: directory_group_member_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.directory_group_member_id_seq OWNED BY public.directory_group_member.id;


--
-- Name: erasure_receipt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.erasure_receipt (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    surrogate_id character varying(255) NOT NULL,
    erased_at timestamp with time zone NOT NULL,
    deleted_row_count bigint NOT NULL,
    cleared_row_count bigint NOT NULL,
    anonymised_row_count bigint NOT NULL,
    media_residue bigint NOT NULL
);


--
-- Name: erasure_receipt_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.erasure_receipt ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.erasure_receipt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: session_attendance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_attendance (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    session_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: event_attendance_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.event_attendance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: event_attendance_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.event_attendance_id_seq OWNED BY public.session_attendance.id;


--
-- Name: event_section; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_section (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    "position" integer NOT NULL,
    section_type_id bigint NOT NULL,
    title character varying(255),
    duration_minutes integer,
    notes text
);


--
-- Name: event_section_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.event_section_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: event_section_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.event_section_id_seq OWNED BY public.event_section.id;


--
-- Name: event_section_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_section_type (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    label character varying(60) NOT NULL,
    expects_presenter boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    icon character varying(60),
    accent_color character varying(7)
);


--
-- Name: event_section_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.event_section_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: event_section_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.event_section_type_id_seq OWNED BY public.event_section_type.id;


--
-- Name: event_series; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_series (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    title character varying(255) NOT NULL,
    cadence_count integer NOT NULL,
    cadence_unit character varying(10) NOT NULL,
    preferred_time time without time zone,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    starts_on date NOT NULL,
    duration_minutes integer,
    time_zone character varying(64) NOT NULL,
    join_url character varying(2048),
    location character varying(512),
    joining_details character varying(2048)
);


--
-- Name: event_series_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.event_series_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: event_series_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.event_series_id_seq OWNED BY public.event_series.id;


--
-- Name: event_series_section_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_series_section_type (
    series_id bigint NOT NULL,
    "position" integer NOT NULL,
    section_type_id bigint NOT NULL,
    title character varying(255),
    duration_minutes integer
);


--
-- Name: event_slot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_slot (
    id bigint NOT NULL,
    section_id bigint NOT NULL,
    "position" integer NOT NULL,
    topic_id bigint,
    title character varying(255),
    notes text,
    duration_minutes integer NOT NULL
);


--
-- Name: event_slot_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.event_slot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: event_slot_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.event_slot_id_seq OWNED BY public.event_slot.id;


--
-- Name: event_slot_presenter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_slot_presenter (
    slot_id bigint NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: event_slot_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_slot_tag (
    slot_id bigint NOT NULL,
    tag character varying(50) NOT NULL
);


--
-- Name: expertise_declaration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_declaration (
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    tag character varying(80) NOT NULL,
    declared_at timestamp with time zone NOT NULL
);


--
-- Name: expertise_dismissed_signal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_dismissed_signal (
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    tag character varying(80) NOT NULL
);


--
-- Name: expertise_hidden_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_hidden_tag (
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    tag character varying(80) NOT NULL
);


--
-- Name: expertise_named_area; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_named_area (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    name character varying(80) NOT NULL
);


--
-- Name: expertise_named_area_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expertise_named_area_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expertise_named_area_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expertise_named_area_id_seq OWNED BY public.expertise_named_area.id;


--
-- Name: expertise_named_area_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_named_area_tag (
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    named_area_id bigint NOT NULL,
    canonical_tag character varying(80) NOT NULL
);


--
-- Name: expertise_opt_out; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_opt_out (
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: expertise_signal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_signal (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    tag character varying(80) NOT NULL,
    signal_type character varying(20) NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    occurred_at timestamp with time zone NOT NULL
);


--
-- Name: expertise_signal_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expertise_signal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expertise_signal_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expertise_signal_id_seq OWNED BY public.expertise_signal.id;


--
-- Name: expertise_tag_alias; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_tag_alias (
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    variant_tag character varying(80) NOT NULL,
    canonical_tag character varying(80) NOT NULL
);


--
-- Name: expertise_tag_dismissal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expertise_tag_dismissal (
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    tag_low character varying(80) NOT NULL,
    tag_high character varying(80) NOT NULL
);


--
-- Name: identity_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.identity_user (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    user_id character varying(255),
    display_name character varying(255) NOT NULL,
    email character varying(320),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    active boolean DEFAULT true NOT NULL,
    external_id character varying(255),
    scim_id character varying(64) NOT NULL,
    business_unit character varying(255),
    department character varying(255),
    team character varying(255),
    avatar_media_key character varying(512),
    avatar_etag character varying(256),
    avatar_synced_at timestamp with time zone,
    last_seen_at timestamp with time zone,
    arrival character varying(20) NOT NULL,
    avatar_override_key text,
    observed_time_zone character varying(64),
    declared_time_zone character varying(64),
    CONSTRAINT identity_user_arrival_values CHECK (arrival IN ('FIRST_SIGN_IN', 'SYNCED'))
);


--
-- Name: identity_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.identity_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: identity_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.identity_user_id_seq OWNED BY public.identity_user.id;


--
-- Name: learning_intent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.learning_intent (
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    tag character varying(80) NOT NULL,
    declared_at timestamp with time zone NOT NULL
);


--
-- Name: memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.memberships (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    role character varying(20) NOT NULL,
    joined_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: memberships_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.memberships_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: memberships_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.memberships_id_seq OWNED BY public.memberships.id;


--
-- Name: onboarding_path; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.onboarding_path (
    subject character varying(255) NOT NULL,
    variant character varying(16) NOT NULL,
    community_slug character varying(255) NOT NULL,
    anchor_organization_id character varying(255) NOT NULL,
    anchor_community_id bigint NOT NULL,
    closed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: onboarding_step; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.onboarding_step (
    subject character varying(255) NOT NULL,
    ordinal integer NOT NULL,
    step_key character varying(64) NOT NULL,
    route character varying(512) NOT NULL,
    detail character varying(255),
    visited_at timestamp with time zone
);


--
-- Name: organization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization (
    name character varying(120) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    id character varying(255) NOT NULL,
    kind character varying(20) NOT NULL,
    lifecycle_state character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    closed_at timestamp with time zone,
    closed_by character varying(255),
    communications_enabled boolean DEFAULT true NOT NULL
);


--
-- Name: personal_access_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.personal_access_token (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    token_hash character varying(64) NOT NULL,
    name character varying(255) DEFAULT ''::character varying NOT NULL,
    scopes character varying(512) DEFAULT ''::character varying NOT NULL,
    last_used_at timestamp with time zone,
    revoked_at timestamp with time zone,
    expires_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: personal_access_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.personal_access_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: personal_access_token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.personal_access_token_id_seq OWNED BY public.personal_access_token.id;


--
-- Name: reaction_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reaction_definition (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    name character varying(32) NOT NULL,
    image_key character varying(512) NOT NULL,
    state character varying(16) NOT NULL,
    proposed_by_user_id character varying(255),
    approved_by_user_id character varying(255),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint
);


--
-- Name: reaction_definition_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.reaction_definition ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.reaction_definition_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: recognized_contribution; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recognized_contribution (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    contribution_type character varying(64) NOT NULL,
    contribution_id bigint NOT NULL,
    community_id bigint NOT NULL,
    title character varying(512) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: recognized_contribution_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.recognized_contribution_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recognized_contribution_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.recognized_contribution_id_seq OWNED BY public.recognized_contribution.id;


--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    name character varying(80) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    description character varying(255),
    system boolean DEFAULT false NOT NULL,
    slug character varying(40)
);


--
-- Name: role_binding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_binding (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    subject_type character varying(10) NOT NULL,
    subject_id character varying(255) NOT NULL,
    role_id bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    subject_label character varying(255)
);


--
-- Name: role_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.role_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_assignment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.role_assignment_id_seq OWNED BY public.role_binding.id;


--
-- Name: role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.role_id_seq OWNED BY public.role.id;


--
-- Name: role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permission (
    role_id bigint NOT NULL,
    permission character varying(60) NOT NULL
);


--
-- Name: roundup_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roundup_settings (
    organization_id character varying(255) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: scim_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scim_token (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    token_hash character varying(64) NOT NULL,
    label character varying(255) DEFAULT ''::character varying NOT NULL,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    revoked_at timestamp with time zone,
    expires_at timestamp with time zone
);


--
-- Name: scim_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.scim_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: scim_token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.scim_token_id_seq OWNED BY public.scim_token.id;


--
-- Name: topic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.topic (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    community_id bigint NOT NULL,
    kind character varying(20) NOT NULL,
    title character varying(255) NOT NULL,
    description text NOT NULL,
    proposer_user_id character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: topic_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.topic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: topic_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.topic_id_seq OWNED BY public.topic.id;


--
-- Name: topic_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.topic_tag (
    topic_id bigint NOT NULL,
    tag character varying(50) NOT NULL
);


--
-- Name: topic_vote; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.topic_vote (
    id bigint NOT NULL,
    organization_id character varying(255) NOT NULL,
    topic_id bigint NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: topic_vote_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.topic_vote_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: topic_vote_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.topic_vote_id_seq OWNED BY public.topic_vote.id;


--
-- Name: action_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item ALTER COLUMN id SET DEFAULT nextval('public.action_item_id_seq'::regclass);


--
-- Name: action_item_checkpoint id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item_checkpoint ALTER COLUMN id SET DEFAULT nextval('public.action_item_checkpoint_id_seq'::regclass);


--
-- Name: agent_tool_call id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_tool_call ALTER COLUMN id SET DEFAULT nextval('public.agent_tool_call_id_seq'::regclass);


--
-- Name: artifact id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact ALTER COLUMN id SET DEFAULT nextval('public.artifact_id_seq'::regclass);


--
-- Name: artifact_attachment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_attachment ALTER COLUMN id SET DEFAULT nextval('public.artifact_attachment_id_seq'::regclass);


--
-- Name: artifact_opening id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_opening ALTER COLUMN id SET DEFAULT nextval('public.artifact_opening_id_seq'::regclass);


--
-- Name: artifact_type id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_type ALTER COLUMN id SET DEFAULT nextval('public.artifact_type_id_seq'::regclass);


--
-- Name: bookmark id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookmark ALTER COLUMN id SET DEFAULT nextval('public.bookmark_id_seq'::regclass);


--
-- Name: calendar_subscription id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_subscription ALTER COLUMN id SET DEFAULT nextval('public.calendar_subscription_id_seq'::regclass);


--
-- Name: communities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities ALTER COLUMN id SET DEFAULT nextval('public.communities_id_seq'::regclass);


--
-- Name: community_kind id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_kind ALTER COLUMN id SET DEFAULT nextval('public.community_kind_id_seq'::regclass);


--
-- Name: community_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_session ALTER COLUMN id SET DEFAULT nextval('public.community_session_id_seq'::regclass);


--
-- Name: contribution_love id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contribution_love ALTER COLUMN id SET DEFAULT nextval('public.appreciation_id_seq'::regclass);


--
-- Name: directory_group id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group ALTER COLUMN id SET DEFAULT nextval('public.directory_group_id_seq'::regclass);


--
-- Name: directory_group_member id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group_member ALTER COLUMN id SET DEFAULT nextval('public.directory_group_member_id_seq'::regclass);


--
-- Name: event_section id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section ALTER COLUMN id SET DEFAULT nextval('public.event_section_id_seq'::regclass);


--
-- Name: event_section_type id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section_type ALTER COLUMN id SET DEFAULT nextval('public.event_section_type_id_seq'::regclass);


--
-- Name: event_series id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_series ALTER COLUMN id SET DEFAULT nextval('public.event_series_id_seq'::regclass);


--
-- Name: event_slot id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot ALTER COLUMN id SET DEFAULT nextval('public.event_slot_id_seq'::regclass);


--
-- Name: expertise_named_area id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_named_area ALTER COLUMN id SET DEFAULT nextval('public.expertise_named_area_id_seq'::regclass);


--
-- Name: expertise_signal id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_signal ALTER COLUMN id SET DEFAULT nextval('public.expertise_signal_id_seq'::regclass);


--
-- Name: identity_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_user ALTER COLUMN id SET DEFAULT nextval('public.identity_user_id_seq'::regclass);


--
-- Name: memberships id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memberships ALTER COLUMN id SET DEFAULT nextval('public.memberships_id_seq'::regclass);


--
-- Name: personal_access_token id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_token ALTER COLUMN id SET DEFAULT nextval('public.personal_access_token_id_seq'::regclass);


--
-- Name: recognized_contribution id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recognized_contribution ALTER COLUMN id SET DEFAULT nextval('public.recognized_contribution_id_seq'::regclass);


--
-- Name: role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role ALTER COLUMN id SET DEFAULT nextval('public.role_id_seq'::regclass);


--
-- Name: role_binding id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_binding ALTER COLUMN id SET DEFAULT nextval('public.role_assignment_id_seq'::regclass);


--
-- Name: scim_token id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scim_token ALTER COLUMN id SET DEFAULT nextval('public.scim_token_id_seq'::regclass);


--
-- Name: session_attendance id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_attendance ALTER COLUMN id SET DEFAULT nextval('public.event_attendance_id_seq'::regclass);


--
-- Name: topic id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic ALTER COLUMN id SET DEFAULT nextval('public.topic_id_seq'::regclass);


--
-- Name: topic_vote id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_vote ALTER COLUMN id SET DEFAULT nextval('public.topic_vote_id_seq'::regclass);


--
-- Name: action_item_checkpoint action_item_checkpoint_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item_checkpoint
    ADD CONSTRAINT action_item_checkpoint_pkey PRIMARY KEY (id);


--
-- Name: action_item action_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item
    ADD CONSTRAINT action_item_pkey PRIMARY KEY (id);


--
-- Name: action_item_tag action_item_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item_tag
    ADD CONSTRAINT action_item_tag_pkey PRIMARY KEY (action_item_id, tag);


--
-- Name: agent_connection agent_connection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_connection
    ADD CONSTRAINT agent_connection_pkey PRIMARY KEY (id);


--
-- Name: agent_connection_token agent_connection_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_connection_token
    ADD CONSTRAINT agent_connection_token_pkey PRIMARY KEY (id);


--
-- Name: agent_tool_call agent_tool_call_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_tool_call
    ADD CONSTRAINT agent_tool_call_pkey PRIMARY KEY (id);


--
-- Name: contribution_love appreciation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contribution_love
    ADD CONSTRAINT appreciation_pkey PRIMARY KEY (id);


--
-- Name: artifact_attachment artifact_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_attachment
    ADD CONSTRAINT artifact_attachment_pkey PRIMARY KEY (id);


--
-- Name: artifact_opening artifact_opening_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_opening
    ADD CONSTRAINT artifact_opening_pkey PRIMARY KEY (id);


--
-- Name: artifact artifact_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact
    ADD CONSTRAINT artifact_pkey PRIMARY KEY (id);


--
-- Name: artifact_type artifact_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_type
    ADD CONSTRAINT artifact_type_pkey PRIMARY KEY (id);


--
-- Name: bookmark bookmark_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookmark
    ADD CONSTRAINT bookmark_pkey PRIMARY KEY (id);


--
-- Name: calendar_subscription calendar_subscription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_subscription
    ADD CONSTRAINT calendar_subscription_pkey PRIMARY KEY (id);


--
-- Name: communication_delivery communication_delivery_once; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_delivery
    ADD CONSTRAINT communication_delivery_once UNIQUE (message_id, user_id, channel);


--
-- Name: communication_delivery communication_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_delivery
    ADD CONSTRAINT communication_delivery_pkey PRIMARY KEY (id);


--
-- Name: communication_expired_message communication_expired_message_once; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_expired_message
    ADD CONSTRAINT communication_expired_message_once UNIQUE (organization_id, message_type, subject_ref);


--
-- Name: communication_expired_message communication_expired_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_expired_message
    ADD CONSTRAINT communication_expired_message_pkey PRIMARY KEY (id);


--
-- Name: communication_message communication_message_once; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_message
    ADD CONSTRAINT communication_message_once UNIQUE (organization_id, message_type, subject_ref);


--
-- Name: communication_message communication_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_message
    ADD CONSTRAINT communication_message_pkey PRIMARY KEY (id);


--
-- Name: communication_preference communication_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_preference
    ADD CONSTRAINT communication_preference_pkey PRIMARY KEY (id);


--
-- Name: communication_preference communication_preference_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_preference
    ADD CONSTRAINT communication_preference_unique UNIQUE (organization_id, user_id, message_type);


--
-- Name: communication_production_watermark communication_production_watermark_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_production_watermark
    ADD CONSTRAINT communication_production_watermark_pkey PRIMARY KEY (organization_id, producer_key);


--
-- Name: communities communities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities
    ADD CONSTRAINT communities_pkey PRIMARY KEY (id);


--
-- Name: community_chat_destination community_chat_destination_named_once; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_chat_destination
    ADD CONSTRAINT community_chat_destination_named_once UNIQUE (community_id, name);


--
-- Name: community_chat_destination community_chat_destination_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_chat_destination
    ADD CONSTRAINT community_chat_destination_pkey PRIMARY KEY (id);


--
-- Name: community_kind community_kind_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_kind
    ADD CONSTRAINT community_kind_pkey PRIMARY KEY (id);


--
-- Name: community_session community_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_session
    ADD CONSTRAINT community_session_pkey PRIMARY KEY (id);


--
-- Name: contribution_reaction contribution_reaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contribution_reaction
    ADD CONSTRAINT contribution_reaction_pkey PRIMARY KEY (id);


--
-- Name: directory_group_member directory_group_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group_member
    ADD CONSTRAINT directory_group_member_pkey PRIMARY KEY (id);


--
-- Name: directory_group directory_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group
    ADD CONSTRAINT directory_group_pkey PRIMARY KEY (id);


--
-- Name: erasure_receipt erasure_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.erasure_receipt
    ADD CONSTRAINT erasure_receipt_pkey PRIMARY KEY (id);


--
-- Name: session_attendance event_attendance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_attendance
    ADD CONSTRAINT event_attendance_pkey PRIMARY KEY (id);


--
-- Name: event_section event_section_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section
    ADD CONSTRAINT event_section_pkey PRIMARY KEY (id);


--
-- Name: event_section_type event_section_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section_type
    ADD CONSTRAINT event_section_type_pkey PRIMARY KEY (id);


--
-- Name: event_series event_series_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_series
    ADD CONSTRAINT event_series_pkey PRIMARY KEY (id);


--
-- Name: event_series_section_type event_series_section_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_series_section_type
    ADD CONSTRAINT event_series_section_type_pkey PRIMARY KEY (series_id, "position");


--
-- Name: event_slot event_slot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot
    ADD CONSTRAINT event_slot_pkey PRIMARY KEY (id);


--
-- Name: event_slot_tag event_slot_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot_tag
    ADD CONSTRAINT event_slot_tag_pkey PRIMARY KEY (slot_id, tag);


--
-- Name: expertise_declaration expertise_declaration_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_declaration
    ADD CONSTRAINT expertise_declaration_pkey PRIMARY KEY (organization_id, community_id, user_id, tag);


--
-- Name: expertise_dismissed_signal expertise_dismissed_signal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_dismissed_signal
    ADD CONSTRAINT expertise_dismissed_signal_pkey PRIMARY KEY (organization_id, user_id, source_type, source_id, tag);


--
-- Name: expertise_hidden_tag expertise_hidden_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_hidden_tag
    ADD CONSTRAINT expertise_hidden_tag_pkey PRIMARY KEY (organization_id, user_id, tag);


--
-- Name: expertise_named_area expertise_named_area_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_named_area
    ADD CONSTRAINT expertise_named_area_pkey PRIMARY KEY (id);


--
-- Name: expertise_named_area_tag expertise_named_area_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_named_area_tag
    ADD CONSTRAINT expertise_named_area_tag_pkey PRIMARY KEY (organization_id, community_id, canonical_tag);


--
-- Name: expertise_opt_out expertise_opt_out_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_opt_out
    ADD CONSTRAINT expertise_opt_out_pkey PRIMARY KEY (organization_id, user_id);


--
-- Name: expertise_signal expertise_signal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_signal
    ADD CONSTRAINT expertise_signal_pkey PRIMARY KEY (id);


--
-- Name: expertise_tag_alias expertise_tag_alias_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_tag_alias
    ADD CONSTRAINT expertise_tag_alias_pkey PRIMARY KEY (organization_id, community_id, variant_tag);


--
-- Name: expertise_tag_dismissal expertise_tag_dismissal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_tag_dismissal
    ADD CONSTRAINT expertise_tag_dismissal_pkey PRIMARY KEY (organization_id, community_id, tag_low, tag_high);


--
-- Name: identity_user identity_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_user
    ADD CONSTRAINT identity_user_pkey PRIMARY KEY (id);


--
-- Name: learning_intent learning_intent_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.learning_intent
    ADD CONSTRAINT learning_intent_pkey PRIMARY KEY (organization_id, community_id, user_id, tag);


--
-- Name: memberships memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memberships
    ADD CONSTRAINT memberships_pkey PRIMARY KEY (id);


--
-- Name: onboarding_path onboarding_path_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.onboarding_path
    ADD CONSTRAINT onboarding_path_pkey PRIMARY KEY (subject);


--
-- Name: onboarding_step onboarding_step_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.onboarding_step
    ADD CONSTRAINT onboarding_step_pkey PRIMARY KEY (subject, ordinal);


--
-- Name: organization organization_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT organization_pkey PRIMARY KEY (id);


--
-- Name: personal_access_token personal_access_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_token
    ADD CONSTRAINT personal_access_token_pkey PRIMARY KEY (id);


--
-- Name: reaction_definition reaction_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reaction_definition
    ADD CONSTRAINT reaction_definition_pkey PRIMARY KEY (id);


--
-- Name: recognized_contribution recognized_contribution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recognized_contribution
    ADD CONSTRAINT recognized_contribution_pkey PRIMARY KEY (id);


--
-- Name: role_binding role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_binding
    ADD CONSTRAINT role_assignment_pkey PRIMARY KEY (id);


--
-- Name: role_binding role_binding_subject_role_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_binding
    ADD CONSTRAINT role_binding_subject_role_unique UNIQUE (organization_id, subject_type, subject_id, role_id);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: roundup_settings roundup_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roundup_settings
    ADD CONSTRAINT roundup_settings_pkey PRIMARY KEY (organization_id);


--
-- Name: scim_token scim_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scim_token
    ADD CONSTRAINT scim_token_pkey PRIMARY KEY (id);


--
-- Name: topic topic_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic
    ADD CONSTRAINT topic_pkey PRIMARY KEY (id);


--
-- Name: topic_tag topic_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_tag
    ADD CONSTRAINT topic_tag_pkey PRIMARY KEY (topic_id, tag);


--
-- Name: topic_vote topic_vote_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_vote
    ADD CONSTRAINT topic_vote_pkey PRIMARY KEY (id);


--
-- Name: agent_connection uq_agent_connection_id_org; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_connection
    ADD CONSTRAINT uq_agent_connection_id_org UNIQUE (id, organization_id);


--
-- Name: agent_connection_token uq_agent_connection_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_connection_token
    ADD CONSTRAINT uq_agent_connection_token_hash UNIQUE (token_hash);


--
-- Name: artifact_opening uq_artifact_opening_day; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_opening
    ADD CONSTRAINT uq_artifact_opening_day UNIQUE (organization_id, artifact_id, member_user_id, opened_on);


--
-- Name: artifact_type uq_artifact_type_organization_label; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_type
    ADD CONSTRAINT uq_artifact_type_organization_label UNIQUE (organization_id, label);


--
-- Name: artifact_attachment uq_attachment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_attachment
    ADD CONSTRAINT uq_attachment UNIQUE NULLS NOT DISTINCT (organization_id, artifact_id, session_id, segment_id);


--
-- Name: bookmark uq_bookmark_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookmark
    ADD CONSTRAINT uq_bookmark_member UNIQUE (organization_id, member_user_id, saveable_type, saveable_id);


--
-- Name: calendar_subscription uq_calendar_subscription_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_subscription
    ADD CONSTRAINT uq_calendar_subscription_member UNIQUE (organization_id, community_id, user_id);


--
-- Name: calendar_subscription uq_calendar_subscription_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_subscription
    ADD CONSTRAINT uq_calendar_subscription_token UNIQUE (token_hash);


--
-- Name: communities uq_communities_organization_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities
    ADD CONSTRAINT uq_communities_organization_slug UNIQUE (organization_id, slug);


--
-- Name: community_kind uq_community_kind_organization_label; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_kind
    ADD CONSTRAINT uq_community_kind_organization_label UNIQUE (organization_id, label);


--
-- Name: community_session uq_community_session_organization_series_occurrence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_session
    ADD CONSTRAINT uq_community_session_organization_series_occurrence UNIQUE (organization_id, series_id, occurrence_date);


--
-- Name: contribution_love uq_contribution_love_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contribution_love
    ADD CONSTRAINT uq_contribution_love_member UNIQUE (organization_id, recognized_contribution_id, lover_user_id);


--
-- Name: directory_group_member uq_directory_group_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group_member
    ADD CONSTRAINT uq_directory_group_member UNIQUE (organization_id, group_id, user_scim_id);


--
-- Name: directory_group uq_directory_group_organization_reckey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group
    ADD CONSTRAINT uq_directory_group_organization_reckey UNIQUE (organization_id, reconciliation_key);


--
-- Name: directory_group uq_directory_group_organization_scim; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group
    ADD CONSTRAINT uq_directory_group_organization_scim UNIQUE (organization_id, scim_id);


--
-- Name: event_section uq_event_section_session_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section
    ADD CONSTRAINT uq_event_section_session_position UNIQUE (session_id, "position");


--
-- Name: event_section_type uq_event_section_type_organization_label; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section_type
    ADD CONSTRAINT uq_event_section_type_organization_label UNIQUE (organization_id, label);


--
-- Name: event_slot_presenter uq_event_slot_presenter; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot_presenter
    ADD CONSTRAINT uq_event_slot_presenter UNIQUE (slot_id, user_id);


--
-- Name: event_slot uq_event_slot_section_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot
    ADD CONSTRAINT uq_event_slot_section_position UNIQUE (section_id, "position");


--
-- Name: expertise_signal uq_expertise_signal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_signal
    ADD CONSTRAINT uq_expertise_signal UNIQUE (organization_id, source_type, source_id, user_id, tag);


--
-- Name: identity_user uq_identity_user_organization_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_user
    ADD CONSTRAINT uq_identity_user_organization_user UNIQUE (organization_id, user_id);


--
-- Name: memberships uq_memberships_organization_community_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memberships
    ADD CONSTRAINT uq_memberships_organization_community_user UNIQUE (organization_id, community_id, user_id);


--
-- Name: personal_access_token uq_personal_access_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_token
    ADD CONSTRAINT uq_personal_access_token_hash UNIQUE (token_hash);


--
-- Name: reaction_definition uq_reaction_definition_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reaction_definition
    ADD CONSTRAINT uq_reaction_definition_name UNIQUE (organization_id, name);


--
-- Name: recognized_contribution uq_recognized_contribution; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recognized_contribution
    ADD CONSTRAINT uq_recognized_contribution UNIQUE (organization_id, contribution_type, contribution_id);


--
-- Name: role uq_role_organization_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT uq_role_organization_name UNIQUE (organization_id, name);


--
-- Name: role_permission uq_role_permission; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT uq_role_permission UNIQUE (role_id, permission);


--
-- Name: scim_token uq_scim_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scim_token
    ADD CONSTRAINT uq_scim_token_hash UNIQUE (token_hash);


--
-- Name: session_attendance uq_session_attendance_organization_session_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_attendance
    ADD CONSTRAINT uq_session_attendance_organization_session_user UNIQUE (organization_id, session_id, user_id);


--
-- Name: topic_vote uq_topic_vote_organization_topic_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_vote
    ADD CONSTRAINT uq_topic_vote_organization_topic_user UNIQUE (organization_id, topic_id, user_id);


--
-- Name: communication_delivery_by_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX communication_delivery_by_message ON public.communication_delivery USING btree (message_id);


--
-- Name: communication_delivery_chat_placeholder_once; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX communication_delivery_chat_placeholder_once ON public.communication_delivery USING btree (message_id) WHERE (((channel)::text = 'CHAT'::text) AND (chat_destination_id IS NULL));


--
-- Name: communication_delivery_chat_post_once; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX communication_delivery_chat_post_once ON public.communication_delivery USING btree (message_id, chat_destination_id) WHERE (((channel)::text = 'CHAT'::text) AND (chat_destination_id IS NOT NULL));


--
-- Name: communication_delivery_claimable; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX communication_delivery_claimable ON public.communication_delivery USING btree (id) WHERE (status IN ('PENDING', 'FAILED', 'SENDING'));


--
-- Name: communication_delivery_panel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX communication_delivery_panel ON public.communication_delivery USING btree (organization_id, user_id, sent_at DESC, id DESC) WHERE (((channel)::text = 'IN_APP'::text) AND ((status)::text = 'SENT'::text));


--
-- Name: communication_delivery_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX communication_delivery_unread ON public.communication_delivery USING btree (organization_id, user_id) WHERE (((channel)::text = 'IN_APP'::text) AND ((status)::text = 'SENT'::text) AND (read_at IS NULL));


--
-- Name: communication_message_by_age; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX communication_message_by_age ON public.communication_message USING btree (created_at);


--
-- Name: community_chat_destination_by_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX community_chat_destination_by_community ON public.community_chat_destination USING btree (organization_id, community_id);


--
-- Name: erasure_receipt_organization_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX erasure_receipt_organization_idx ON public.erasure_receipt USING btree (organization_id, erased_at DESC);


--
-- Name: expertise_named_area_by_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX expertise_named_area_by_community ON public.expertise_named_area USING btree (organization_id, community_id);


--
-- Name: expertise_named_area_tag_by_area; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX expertise_named_area_tag_by_area ON public.expertise_named_area_tag USING btree (named_area_id);


--
-- Name: idx_action_item_checkpoint_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_item_checkpoint_item ON public.action_item_checkpoint USING btree (action_item_id);


--
-- Name: idx_action_item_checkpoint_session_fk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_item_checkpoint_session_fk ON public.action_item_checkpoint USING btree (session_id);


--
-- Name: idx_action_item_checkpoint_session_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_item_checkpoint_session_id ON public.action_item USING btree (checkpoint_session_id);


--
-- Name: idx_action_item_community_fk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_item_community_fk ON public.action_item USING btree (community_id);


--
-- Name: idx_action_item_organization_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_item_organization_community ON public.action_item USING btree (organization_id, community_id);


--
-- Name: idx_action_item_origin_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_item_origin_session ON public.action_item USING btree (origin_session_id);


--
-- Name: idx_agent_connection_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_agent_connection_owner ON public.agent_connection USING btree (organization_id, user_id);


--
-- Name: idx_agent_connection_token_connection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_agent_connection_token_connection ON public.agent_connection_token USING btree (connection_id);


--
-- Name: idx_agent_tool_call_org_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_agent_tool_call_org_time ON public.agent_tool_call USING btree (organization_id, occurred_at);


--
-- Name: idx_artifact_library_page; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_library_page ON public.artifact USING btree (organization_id, community_id, status, created_at DESC);


--
-- Name: idx_artifact_opening_artifact; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_opening_artifact ON public.artifact_opening USING btree (artifact_id);


--
-- Name: idx_artifact_opening_window; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_opening_window ON public.artifact_opening USING btree (organization_id, community_id, created_at);


--
-- Name: idx_artifact_organization_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_organization_community ON public.artifact USING btree (organization_id, community_id);


--
-- Name: idx_artifact_search_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_search_vector ON public.artifact USING gin (search_vector);


--
-- Name: idx_artifact_tag_artifact; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_tag_artifact ON public.artifact_tag USING btree (artifact_id);


--
-- Name: idx_artifact_tag_tag; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifact_tag_tag ON public.artifact_tag USING btree (tag);


--
-- Name: idx_attachment_artifact; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_artifact ON public.artifact_attachment USING btree (artifact_id);


--
-- Name: idx_attachment_organization_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_organization_session ON public.artifact_attachment USING btree (organization_id, session_id);


--
-- Name: idx_bookmark_aggregate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookmark_aggregate ON public.bookmark USING btree (organization_id, community_id, created_at);


--
-- Name: idx_calendar_subscription_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calendar_subscription_org ON public.calendar_subscription USING btree (organization_id);


--
-- Name: idx_communities_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_communities_kind ON public.communities USING btree (kind_id);


--
-- Name: idx_community_session_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_community_session_community ON public.community_session USING btree (community_id);


--
-- Name: idx_community_session_organization_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_community_session_organization_community ON public.community_session USING btree (organization_id, community_id);


--
-- Name: idx_community_session_organization_community_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_community_session_organization_community_scheduled ON public.community_session USING btree (organization_id, community_id, scheduled_at);


--
-- Name: idx_community_session_organization_series; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_community_session_organization_series ON public.community_session USING btree (organization_id, series_id);


--
-- Name: idx_community_session_series; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_community_session_series ON public.community_session USING btree (series_id);


--
-- Name: idx_contribution_love_contribution; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contribution_love_contribution ON public.contribution_love USING btree (recognized_contribution_id);


--
-- Name: idx_contribution_reaction_definition; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contribution_reaction_definition ON public.contribution_reaction USING btree (definition_id);


--
-- Name: idx_contribution_reaction_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contribution_reaction_target ON public.contribution_reaction USING btree (organization_id, contribution_type, contribution_id);


--
-- Name: idx_directory_group_member_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_directory_group_member_group ON public.directory_group_member USING btree (group_id);


--
-- Name: idx_directory_group_member_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_directory_group_member_user ON public.directory_group_member USING btree (organization_id, user_scim_id);


--
-- Name: idx_event_section_section_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_section_section_type ON public.event_section USING btree (section_type_id);


--
-- Name: idx_event_series_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_series_community ON public.event_series USING btree (community_id);


--
-- Name: idx_event_series_organization_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_series_organization_community ON public.event_series USING btree (organization_id, community_id);


--
-- Name: idx_event_series_section_type_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_series_section_type_type ON public.event_series_section_type USING btree (section_type_id);


--
-- Name: idx_event_slot_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_slot_topic ON public.event_slot USING btree (topic_id);


--
-- Name: idx_expertise_declaration_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expertise_declaration_user ON public.expertise_declaration USING btree (organization_id, user_id);


--
-- Name: idx_expertise_signal_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expertise_signal_lookup ON public.expertise_signal USING btree (organization_id, community_id, tag);


--
-- Name: idx_expertise_signal_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expertise_signal_user ON public.expertise_signal USING btree (organization_id, user_id);


--
-- Name: idx_identity_user_organization_display_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_user_organization_display_name ON public.identity_user USING btree (organization_id, lower((display_name)::text));


--
-- Name: idx_identity_user_organization_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_user_organization_email ON public.identity_user USING btree (organization_id, lower((email)::text));


--
-- Name: idx_identity_user_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_user_user_id ON public.identity_user USING btree (user_id);


--
-- Name: idx_learning_intent_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_learning_intent_user ON public.learning_intent USING btree (organization_id, user_id);


--
-- Name: idx_memberships_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_memberships_community ON public.memberships USING btree (community_id);


--
-- Name: idx_personal_access_token_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_personal_access_token_owner ON public.personal_access_token USING btree (organization_id, user_id);


--
-- Name: idx_reaction_definition_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reaction_definition_state ON public.reaction_definition USING btree (organization_id, state);


--
-- Name: idx_role_binding_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_binding_role ON public.role_binding USING btree (role_id);


--
-- Name: idx_scim_token_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scim_token_organization ON public.scim_token USING btree (organization_id);


--
-- Name: idx_session_attendance_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_attendance_session ON public.session_attendance USING btree (session_id);


--
-- Name: idx_topic_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_topic_community ON public.topic USING btree (community_id);


--
-- Name: idx_topic_organization_community; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_topic_organization_community ON public.topic USING btree (organization_id, community_id);


--
-- Name: idx_topic_vote_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_topic_vote_topic ON public.topic_vote USING btree (topic_id);


--
-- Name: role_org_slug_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX role_org_slug_uk ON public.role USING btree (organization_id, slug) WHERE (slug IS NOT NULL);


--
-- Name: uq_agent_connection_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_agent_connection_active ON public.agent_connection USING btree (organization_id, user_id, client_id) WHERE ((activated_at IS NOT NULL) AND (revoked_at IS NULL));


--
-- Name: uq_contribution_reaction_member; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_contribution_reaction_member ON public.contribution_reaction USING btree (organization_id, contribution_type, contribution_id, reactor_user_id, COALESCE(emoji, ''::character varying), COALESCE(definition_id, ('-1'::integer)::bigint));


--
-- Name: uq_identity_user_organization_external; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_identity_user_organization_external ON public.identity_user USING btree (organization_id, external_id) WHERE (external_id IS NOT NULL);


--
-- Name: uq_identity_user_organization_scim; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_identity_user_organization_scim ON public.identity_user USING btree (organization_id, scim_id);


--
-- Name: action_item_checkpoint action_item_checkpoint_item_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item_checkpoint
    ADD CONSTRAINT action_item_checkpoint_item_fkey FOREIGN KEY (action_item_id) REFERENCES public.action_item(id) ON DELETE CASCADE;


--
-- Name: action_item_checkpoint action_item_checkpoint_session_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item_checkpoint
    ADD CONSTRAINT action_item_checkpoint_session_fkey FOREIGN KEY (session_id) REFERENCES public.community_session(id) ON DELETE CASCADE;


--
-- Name: action_item action_item_checkpoint_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item
    ADD CONSTRAINT action_item_checkpoint_session_id_fkey FOREIGN KEY (checkpoint_session_id) REFERENCES public.community_session(id) ON DELETE SET NULL;


--
-- Name: action_item action_item_community_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item
    ADD CONSTRAINT action_item_community_id_fkey FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: action_item action_item_origin_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item
    ADD CONSTRAINT action_item_origin_session_id_fkey FOREIGN KEY (origin_session_id) REFERENCES public.community_session(id) ON DELETE SET NULL;


--
-- Name: action_item_tag action_item_tag_item_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_item_tag
    ADD CONSTRAINT action_item_tag_item_fkey FOREIGN KEY (action_item_id) REFERENCES public.action_item(id) ON DELETE CASCADE;


--
-- Name: contribution_love appreciation_recognized_contribution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contribution_love
    ADD CONSTRAINT appreciation_recognized_contribution_id_fkey FOREIGN KEY (recognized_contribution_id) REFERENCES public.recognized_contribution(id) ON DELETE CASCADE;


--
-- Name: artifact_attachment artifact_attachment_artifact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_attachment
    ADD CONSTRAINT artifact_attachment_artifact_id_fkey FOREIGN KEY (artifact_id) REFERENCES public.artifact(id) ON DELETE CASCADE;


--
-- Name: artifact_tag artifact_tag_artifact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_tag
    ADD CONSTRAINT artifact_tag_artifact_id_fkey FOREIGN KEY (artifact_id) REFERENCES public.artifact(id) ON DELETE CASCADE;


--
-- Name: communication_delivery communication_delivery_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communication_delivery
    ADD CONSTRAINT communication_delivery_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.communication_message(id);


--
-- Name: community_chat_destination community_chat_destination_community_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_chat_destination
    ADD CONSTRAINT community_chat_destination_community_id_fkey FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: community_session community_session_community_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_session
    ADD CONSTRAINT community_session_community_id_fkey FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: community_session community_session_series_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_session
    ADD CONSTRAINT community_session_series_id_fkey FOREIGN KEY (series_id) REFERENCES public.event_series(id);


--
-- Name: contribution_reaction contribution_reaction_definition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contribution_reaction
    ADD CONSTRAINT contribution_reaction_definition_id_fkey FOREIGN KEY (definition_id) REFERENCES public.reaction_definition(id) ON DELETE CASCADE;


--
-- Name: directory_group_member directory_group_member_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_group_member
    ADD CONSTRAINT directory_group_member_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.directory_group(id) ON DELETE CASCADE;


--
-- Name: event_section event_section_section_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section
    ADD CONSTRAINT event_section_section_type_id_fkey FOREIGN KEY (section_type_id) REFERENCES public.event_section_type(id);


--
-- Name: event_section event_section_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_section
    ADD CONSTRAINT event_section_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.community_session(id);


--
-- Name: event_series event_series_community_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_series
    ADD CONSTRAINT event_series_community_id_fkey FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: event_series_section_type event_series_section_type_section_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_series_section_type
    ADD CONSTRAINT event_series_section_type_section_type_id_fkey FOREIGN KEY (section_type_id) REFERENCES public.event_section_type(id);


--
-- Name: event_series_section_type event_series_section_type_series_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_series_section_type
    ADD CONSTRAINT event_series_section_type_series_id_fkey FOREIGN KEY (series_id) REFERENCES public.event_series(id);


--
-- Name: event_slot_presenter event_slot_presenter_slot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot_presenter
    ADD CONSTRAINT event_slot_presenter_slot_id_fkey FOREIGN KEY (slot_id) REFERENCES public.event_slot(id);


--
-- Name: event_slot event_slot_section_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot
    ADD CONSTRAINT event_slot_section_id_fkey FOREIGN KEY (section_id) REFERENCES public.event_section(id);


--
-- Name: event_slot_tag event_slot_tag_slot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot_tag
    ADD CONSTRAINT event_slot_tag_slot_id_fkey FOREIGN KEY (slot_id) REFERENCES public.event_slot(id) ON DELETE CASCADE;


--
-- Name: event_slot event_slot_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_slot
    ADD CONSTRAINT event_slot_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.topic(id);


--
-- Name: expertise_named_area_tag expertise_named_area_tag_named_area_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expertise_named_area_tag
    ADD CONSTRAINT expertise_named_area_tag_named_area_id_fkey FOREIGN KEY (named_area_id) REFERENCES public.expertise_named_area(id) ON DELETE CASCADE;


--
-- Name: agent_connection_token fk_agent_connection_token_connection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_connection_token
    ADD CONSTRAINT fk_agent_connection_token_connection FOREIGN KEY (connection_id, organization_id) REFERENCES public.agent_connection(id, organization_id) ON DELETE CASCADE;


--
-- Name: artifact_opening fk_artifact_opening_artifact; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_opening
    ADD CONSTRAINT fk_artifact_opening_artifact FOREIGN KEY (artifact_id) REFERENCES public.artifact(id) ON DELETE CASCADE;


--
-- Name: communities fk_communities_kind; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities
    ADD CONSTRAINT fk_communities_kind FOREIGN KEY (kind_id) REFERENCES public.community_kind(id);


--
-- Name: onboarding_step fk_onboarding_step_path; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.onboarding_step
    ADD CONSTRAINT fk_onboarding_step_path FOREIGN KEY (subject) REFERENCES public.onboarding_path(subject) ON DELETE CASCADE;


--
-- Name: session_attendance fk_session_attendance_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_attendance
    ADD CONSTRAINT fk_session_attendance_session FOREIGN KEY (session_id) REFERENCES public.community_session(id);


--
-- Name: memberships memberships_community_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memberships
    ADD CONSTRAINT memberships_community_id_fkey FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: role_binding role_assignment_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_binding
    ADD CONSTRAINT role_assignment_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: role_permission role_permission_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.role(id) ON DELETE CASCADE;


--
-- Name: topic topic_community_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic
    ADD CONSTRAINT topic_community_id_fkey FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: topic_tag topic_tag_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_tag
    ADD CONSTRAINT topic_tag_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.topic(id) ON DELETE CASCADE;


--
-- Name: topic_vote topic_vote_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topic_vote
    ADD CONSTRAINT topic_vote_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.topic(id);


--
-- Name: action_item; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.action_item ENABLE ROW LEVEL SECURITY;

--
-- Name: action_item_checkpoint; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.action_item_checkpoint ENABLE ROW LEVEL SECURITY;

--
-- Name: agent_connection; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.agent_connection ENABLE ROW LEVEL SECURITY;

--
-- Name: agent_connection_token; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.agent_connection_token ENABLE ROW LEVEL SECURITY;

--
-- Name: agent_connection_token agent_connection_token_auth_lookup; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY agent_connection_token_auth_lookup ON public.agent_connection_token FOR SELECT USING (true);


--
-- Name: agent_connection_token agent_connection_token_org_write; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY agent_connection_token_org_write ON public.agent_connection_token USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: agent_tool_call; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.agent_tool_call ENABLE ROW LEVEL SECURITY;

--
-- Name: artifact; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.artifact ENABLE ROW LEVEL SECURITY;

--
-- Name: artifact_attachment; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.artifact_attachment ENABLE ROW LEVEL SECURITY;

--
-- Name: artifact_opening; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.artifact_opening ENABLE ROW LEVEL SECURITY;

--
-- Name: artifact_type; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.artifact_type ENABLE ROW LEVEL SECURITY;

--
-- Name: bookmark; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.bookmark ENABLE ROW LEVEL SECURITY;

--
-- Name: calendar_subscription; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.calendar_subscription ENABLE ROW LEVEL SECURITY;

--
-- Name: calendar_subscription calendar_subscription_auth_lookup; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY calendar_subscription_auth_lookup ON public.calendar_subscription FOR SELECT USING (true);


--
-- Name: communication_delivery; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.communication_delivery ENABLE ROW LEVEL SECURITY;

--
-- Name: communication_expired_message; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.communication_expired_message ENABLE ROW LEVEL SECURITY;

--
-- Name: communication_message; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.communication_message ENABLE ROW LEVEL SECURITY;

--
-- Name: communication_preference; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.communication_preference ENABLE ROW LEVEL SECURITY;

--
-- Name: communication_production_watermark; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.communication_production_watermark ENABLE ROW LEVEL SECURITY;

--
-- Name: organization communication_read; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY communication_read ON public.organization FOR SELECT TO axonia_communication_read USING (true);


--
-- Name: communities; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.communities ENABLE ROW LEVEL SECURITY;

--
-- Name: community_chat_destination; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.community_chat_destination ENABLE ROW LEVEL SECURITY;

--
-- Name: community_kind; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.community_kind ENABLE ROW LEVEL SECURITY;

--
-- Name: community_session; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.community_session ENABLE ROW LEVEL SECURITY;

--
-- Name: contribution_love; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.contribution_love ENABLE ROW LEVEL SECURITY;

--
-- Name: contribution_reaction; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.contribution_reaction ENABLE ROW LEVEL SECURITY;

--
-- Name: directory_group; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.directory_group ENABLE ROW LEVEL SECURITY;

--
-- Name: directory_group_member; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.directory_group_member ENABLE ROW LEVEL SECURITY;

--
-- Name: erasure_receipt; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.erasure_receipt ENABLE ROW LEVEL SECURITY;

--
-- Name: event_section_type; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.event_section_type ENABLE ROW LEVEL SECURITY;

--
-- Name: event_series; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.event_series ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_declaration; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_declaration ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_dismissed_signal; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_dismissed_signal ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_hidden_tag; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_hidden_tag ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_named_area; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_named_area ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_named_area_tag; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_named_area_tag ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_opt_out; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_opt_out ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_signal; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_signal ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_tag_alias; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_tag_alias ENABLE ROW LEVEL SECURITY;

--
-- Name: expertise_tag_dismissal; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.expertise_tag_dismissal ENABLE ROW LEVEL SECURITY;

--
-- Name: identity_user; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.identity_user ENABLE ROW LEVEL SECURITY;

--
-- Name: learning_intent; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.learning_intent ENABLE ROW LEVEL SECURITY;

--
-- Name: memberships; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.memberships ENABLE ROW LEVEL SECURITY;

--
-- Name: onboarding_path; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.onboarding_path ENABLE ROW LEVEL SECURITY;

--
-- Name: onboarding_path onboarding_path_subject_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY onboarding_path_subject_isolation ON public.onboarding_path USING (((subject)::text = current_setting('app.current_subject'::text, true))) WITH CHECK (((subject)::text = current_setting('app.current_subject'::text, true)));


--
-- Name: onboarding_step; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.onboarding_step ENABLE ROW LEVEL SECURITY;

--
-- Name: onboarding_step onboarding_step_subject_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY onboarding_step_subject_isolation ON public.onboarding_step USING (((subject)::text = current_setting('app.current_subject'::text, true))) WITH CHECK (((subject)::text = current_setting('app.current_subject'::text, true)));


--
-- Name: organization; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.organization ENABLE ROW LEVEL SECURITY;

--
-- Name: action_item organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.action_item USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: action_item_checkpoint organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.action_item_checkpoint USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: agent_connection organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.agent_connection USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: agent_tool_call organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.agent_tool_call USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: artifact organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.artifact USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: artifact_attachment organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.artifact_attachment USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: artifact_opening organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.artifact_opening USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: artifact_type organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.artifact_type USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: bookmark organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.bookmark USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: calendar_subscription organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.calendar_subscription USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: communication_delivery organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.communication_delivery USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: communication_expired_message organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.communication_expired_message USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: communication_message organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.communication_message USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: communication_preference organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.communication_preference USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: communication_production_watermark organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.communication_production_watermark USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: communities organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.communities USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: community_chat_destination organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.community_chat_destination USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: community_kind organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.community_kind USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: community_session organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.community_session USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: contribution_love organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.contribution_love USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: contribution_reaction organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.contribution_reaction USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: directory_group organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.directory_group USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: directory_group_member organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.directory_group_member USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: erasure_receipt organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.erasure_receipt USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: event_section_type organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.event_section_type USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: event_series organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.event_series USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_declaration organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_declaration USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_dismissed_signal organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_dismissed_signal USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_hidden_tag organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_hidden_tag USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_named_area organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_named_area USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_named_area_tag organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_named_area_tag USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_opt_out organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_opt_out USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_signal organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_signal USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_tag_alias organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_tag_alias USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: expertise_tag_dismissal organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.expertise_tag_dismissal USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: identity_user organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.identity_user USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: learning_intent organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.learning_intent USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: memberships organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.memberships USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: organization organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.organization USING (((id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: reaction_definition organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.reaction_definition USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: recognized_contribution organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.recognized_contribution USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: role organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.role USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: role_binding organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.role_binding USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: roundup_settings organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.roundup_settings USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: session_attendance organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.session_attendance USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: topic organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.topic USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: topic_vote organization_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY organization_isolation ON public.topic_vote USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: personal_access_token; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.personal_access_token ENABLE ROW LEVEL SECURITY;

--
-- Name: personal_access_token personal_access_token_auth_lookup; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY personal_access_token_auth_lookup ON public.personal_access_token FOR SELECT USING (true);


--
-- Name: personal_access_token personal_access_token_org_write; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY personal_access_token_org_write ON public.personal_access_token USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: reaction_definition; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.reaction_definition ENABLE ROW LEVEL SECURITY;

--
-- Name: recognized_contribution; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.recognized_contribution ENABLE ROW LEVEL SECURITY;

--
-- Name: role; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.role ENABLE ROW LEVEL SECURITY;

--
-- Name: role_binding; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.role_binding ENABLE ROW LEVEL SECURITY;

--
-- Name: roundup_settings; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.roundup_settings ENABLE ROW LEVEL SECURITY;

--
-- Name: scim_token; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.scim_token ENABLE ROW LEVEL SECURITY;

--
-- Name: scim_token scim_token_auth_lookup; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY scim_token_auth_lookup ON public.scim_token FOR SELECT USING (true);


--
-- Name: scim_token scim_token_org_write; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY scim_token_org_write ON public.scim_token USING (((organization_id)::text = current_setting('app.current_org'::text, true))) WITH CHECK (((organization_id)::text = current_setting('app.current_org'::text, true)));


--
-- Name: session_attendance; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.session_attendance ENABLE ROW LEVEL SECURITY;

--
-- Name: topic; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.topic ENABLE ROW LEVEL SECURITY;

--
-- Name: topic_vote; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.topic_vote ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--



-- -----------------------------------------------------------------------------
-- 3. Sequences the history left advanced, so both editions issue the same ids.
-- -----------------------------------------------------------------------------
SELECT pg_catalog.setval('public.community_session_id_seq', 1, true);

-- -----------------------------------------------------------------------------
--    The tenant-discovery role reads three columns of one table. A row-level
--    policy above lets it see every row; this grant decides which columns.
--    The sweep covers an object created by another role than the one that ran
--    the default privileges above.
-- -----------------------------------------------------------------------------
GRANT SELECT (id, lifecycle_state, kind) ON public.organization TO axonia_communication_read;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO axonia_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO axonia_app;
