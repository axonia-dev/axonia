-- The mail server the installation sends through, as an Owner saved it in the application; the deployment's
-- AXONIA_MAIL_* variables override it field by field. One row, created here empty, so every save is an update of a
-- known revision. The password is sealed by the application (v1:<key id>:<ciphertext>); tested_fingerprint is a keyed
-- digest of the values the last save was tested with. No row-level security: the row describes the installation.
CREATE TABLE mail_server_settings (
    installation        boolean                  PRIMARY KEY DEFAULT true CHECK (installation),
    host                character varying(253),
    port                integer                  CHECK (port BETWEEN 1 AND 65535),
    security            character varying(8)     CHECK (security IN ('STARTTLS', 'TLS', 'NONE')),
    username            character varying(255),
    password_secret     text,
    from_address        character varying(254),
    from_name           character varying(120),
    tested_fingerprint  character varying(128),
    revision            bigint                   NOT NULL DEFAULT 0,
    last_failure_at     timestamp with time zone,
    last_failure_reason character varying(32),
    updated_at          timestamp with time zone
);

INSERT INTO mail_server_settings (installation) VALUES (true);

-- The application reads and changes the one row and can never add or remove one.
REVOKE ALL ON mail_server_settings FROM axonia_app;
GRANT SELECT, UPDATE ON mail_server_settings TO axonia_app;

-- Who changed the installation's settings, when and what, newest first. Secrets are recorded as replaced or cleared,
-- never with a value. Installation-wide like the row it records, so no row-level security either.
CREATE TABLE installation_settings_change (
    id            bigint                   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamp with time zone NOT NULL,
    actor_user_id character varying(255),
    integration   character varying(16)    NOT NULL CONSTRAINT installation_settings_change_integration_check
                                           CHECK (integration IN ('mail')),
    changes       jsonb                    NOT NULL
);

CREATE INDEX installation_settings_change_newest ON installation_settings_change (integration, occurred_at DESC, id DESC);
CREATE INDEX installation_settings_change_actor ON installation_settings_change (actor_user_id);

-- Append-only, except that erasing a person clears the actor of their changes.
REVOKE ALL ON installation_settings_change FROM axonia_app;
GRANT SELECT, INSERT ON installation_settings_change TO axonia_app;
GRANT UPDATE (actor_user_id) ON installation_settings_change TO axonia_app;
