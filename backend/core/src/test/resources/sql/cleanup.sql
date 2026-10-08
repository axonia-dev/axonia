-- Shared post-class cleanup for every test class that COMMITS rows.
--
-- Two kinds of class need it. Non-transactional @SpringBootTest integration tests boot
-- the full context and commit their fixtures to the shared Testcontainers Postgres (no
-- @Transactional rollback). So does a @DataJdbcTest whose subject writes with
-- REQUIRES_NEW: that propagation opens its own transaction and commits it, straight
-- past the rollback the test slice would otherwise give for free (see
-- JdbcReportReceiptsTest). Left in place, that data leaks into tests that assume empty
-- tables (see JdbcTestSupport). Wire this in with:
--
--   @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
--
-- Truncate every transactional table back to its post-migration (empty) state.
-- CASCADE resolves FK ordering; RESTART IDENTITY keeps surrogate ids predictable.
--
-- The reference catalogs are truncated too. They were once preserved, because the migrations
-- seeded them and @DataJdbcTest tests read those seeds. The baseline stopped seeding anything
-- ("the baseline seeds no reference catalogs"), so preserving them no longer keeps migration
-- data alive: it leaks whatever a committing integration test happened to insert into every
-- test that runs after it. That is what made the suite pass or fail on class ordering alone.
-- Every test now seeds the catalog rows it reads, so there is nothing left here worth keeping.
--
-- Every table of the open schema is named here, once, even one a CASCADE already reaches, and flyway_schema_history
-- is the only one left alone: CleanupScriptCoverageTest fails until a new table is added to the list below.
TRUNCATE TABLE
    action_item,
    action_item_checkpoint,
    action_item_tag,
    agent_connection,
    agent_connection_token,
    agent_tool_call,
    artifact,
    artifact_attachment,
    artifact_opening,
    artifact_tag,
    artifact_type,
    bookmark,
    calendar_subscription,
    communication_delivery,
    communication_expired_message,
    communication_message,
    communication_preference,
    communication_production_watermark,
    communities,
    community_chat_destination,
    community_kind,
    community_session,
    contribution_love,
    contribution_reaction,
    directory_group,
    directory_group_member,
    erasure_receipt,
    event_section,
    event_section_type,
    event_series,
    event_series_section_type,
    event_slot,
    event_slot_presenter,
    event_slot_tag,
    expertise_declaration,
    expertise_dismissed_signal,
    expertise_hidden_tag,
    expertise_named_area,
    expertise_named_area_tag,
    expertise_opt_out,
    expertise_signal,
    expertise_tag_alias,
    expertise_tag_dismissal,
    identity_user,
    learning_intent,
    memberships,
    onboarding_path,
    onboarding_step,
    personal_access_token,
    reaction_definition,
    recognized_contribution,
    role,
    role_binding,
    role_permission,
    roundup_settings,
    scim_token,
    session_attendance,
    topic,
    topic_tag,
    topic_vote
    RESTART IDENTITY CASCADE;

-- organization: after V47 the table uses the organization id string as PK (no slug column).
-- 'default' was once spared here on the belief that a migration seeds it. No migration does: the baseline seeds
-- nothing. Sparing it therefore kept one committing test's organization alive for every class that ran afterwards,
-- and a class assuming 'default' exists passed or failed on ordering alone. Every class seeds the organization it
-- acts in, so nothing here is worth keeping.
DELETE FROM organization;
