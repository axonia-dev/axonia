-- The installation's own record, written once at its first start and never changed: the Organization it founded, the
-- subject claim every account is matched on, and when. One row; the start refuses a configuration that names another
-- Organization or another subject claim. No row-level security: the row describes the installation, not a tenant's
-- data, and the start must read it whatever Organization the configuration now names.
CREATE TABLE selfhosted_settings (
    installation    boolean                  PRIMARY KEY DEFAULT true CHECK (installation),
    organization_id character varying(255)   NOT NULL REFERENCES organization (id),
    subject_claim   character varying(255)   NOT NULL,
    founded_at      timestamp with time zone NOT NULL
);

-- Stated here rather than left to the baseline's default privileges, which reach only tables their own role creates:
-- the application reads and records the row, and can never change or remove it.
REVOKE ALL ON selfhosted_settings FROM axonia_app;
GRANT SELECT, INSERT ON selfhosted_settings TO axonia_app;
