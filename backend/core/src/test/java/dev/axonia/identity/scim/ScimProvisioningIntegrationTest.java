package dev.axonia.identity.scim;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.TokenHasher;
import dev.axonia.access.AdminUser;
import dev.axonia.access.AdminUserService;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.internal.RoleBindingEntity;
import dev.axonia.access.internal.RoleBindingRepository;
import dev.axonia.access.internal.RoleEntity;
import dev.axonia.access.internal.RoleRepository;
import dev.axonia.identity.IdentityClaims;
import dev.axonia.identity.SuccessionAddresses;
import dev.axonia.identity.UserService;
import dev.axonia.identity.internal.DirectoryGroupRepository;
import dev.axonia.identity.internal.ScimTokenEntity;
import dev.axonia.identity.internal.ScimTokenRepository;
import dev.axonia.identity.internal.UserArrival;
import dev.axonia.identity.internal.UserEntity;
import dev.axonia.identity.internal.UserRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * End-to-end SCIM provisioning integration test.
 *
 * <p>
 * Boots the full Spring context against Testcontainers-managed Postgres and exercises the complete provisioning
 * lifecycle across five behaviours, each asserted by a dedicated {@code @Test} method. All data is captured into static
 * snapshot fields inside {@link #setUp} (which runs only once, guarded by an {@link AtomicBoolean}). Inserted rows are
 * deleted at the end of the same {@code setUp} invocation so that the shared Testcontainers database is left in its
 * original state and other test classes are not affected.
 *
 * <ol>
 * <li>Generate SCIM token; {@code POST /scim/v2/Users} → 201; identity_user row persisted with the correct fields and a
 * null userId (pre-login).</li>
 * <li>First login / JIT: {@link UserService#provision} matches by email → userId set, user remains active in
 * directory.</li>
 * <li>{@code PATCH /scim/v2/Users/{id} active:false} → row deactivated in directory.</li>
 * <li>Group creation + SCIM member add; GROUP role_assignment seeded; {@link AdminUserService#byId} surfaces an
 * {@code EffectiveRole} with {@code via="GROUP"}.</li>
 * <li>RLS: a SCIM token for a second organization cannot see organization-1 users.</li>
 * </ol>
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ScimProvisioningIntegrationTest {

    // ── shared state (seeded once, snapshotted, read by every @Test) ─────────

    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    // step 1 - SCIM create
    private static volatile int STEP1_CREATE_STATUS;
    private static volatile String USER_SCIM_ID;
    /** Snapshot taken after the POST, before JIT mutates userId. */
    private static volatile Boolean SNAP_USER_ACTIVE;
    private static volatile String SNAP_USER_EMAIL;
    private static volatile String SNAP_USER_EXT_ID;
    private static volatile Boolean SNAP_USER_ID_NULL;
    /** Stored token hash (snapshot for the hash-assertion test). */
    private static volatile String SNAP_TOKEN_HASH;
    private static volatile String TOKEN_A_PLAINTEXT;

    // step 2 - JIT
    private static volatile String SNAP_JIT_USER_ID;
    private static volatile Boolean SNAP_JIT_USER_ACTIVE;

    // step 3 - de-provision PATCH
    private static volatile int STEP3_PATCH_STATUS;
    private static volatile Boolean SNAP_DEPROVISIONED_ACTIVE;

    // step 4 - group + access read-side
    private static volatile String SNAP_GROUP_RECON_KEY;
    private static volatile AdminUser STEP4_ADMIN_USER;

    // step 5 - RLS
    private static volatile int SNAP_RLS_LIST_STATUS;
    private static volatile int SNAP_RLS_LIST_TOTAL;
    private static volatile int SNAP_RLS_GETBYID_STATUS;

    // step 6 - additive minting + per-id revoke lifecycle
    private static volatile int SNAP_TOK1_AUTH_STATUS;
    private static volatile int SNAP_TOK2_AUTH_STATUS;
    private static volatile int SNAP_TOK1_AFTER_REVOKE_STATUS;
    private static volatile int SNAP_TOK2_SURVIVOR_STATUS;

    // step 7 - last_used_at advances after an authenticated SCIM call
    private static volatile java.time.Instant SNAP_TOKEN_A_LAST_USED_AT;

    // step 8 - connection succession: the directory door
    private static volatile String SUCCESSION_SCIM_ID;
    private static volatile int SNAP_ADOPT_STATUS;
    private static volatile String SNAP_ADOPT_RESOURCE_ID;
    private static volatile Long SNAP_ADOPT_ROSTER_ROWS;
    private static volatile Instant SNAP_ADOPT_CREATED_AT_BEFORE;
    private static volatile Instant SNAP_ADOPT_CREATED_AT_AFTER;
    private static volatile String SNAP_ADOPT_EMAIL;
    private static volatile String SNAP_ADOPT_EXT_ID;
    private static volatile String SNAP_ADOPT_USER_ID;

    // ── constants ─────────────────────────────────────────────────────────────

    private static final String ORGANIZATION = "default";
    private static final String TENANT_B = "tenant-two";
    private static final String EMAIL = "scim-alice@provision.test";
    private static final String EXT_ID = "okta-ext-alice-001";
    private static final String USER_SUB = "sub-jit-alice-prov";

    private static final String OLD_DOMAIN = "old.succession.provision.test";
    private static final String NEW_DOMAIN = "new.succession.provision.test";
    private static final String SUCCESSION_SUB = "sub-grace-succession-prov";
    private static final String SUCCESSION_OLD_EMAIL = "grace@" + OLD_DOMAIN;
    private static final String SUCCESSION_NEW_EMAIL = "grace@" + NEW_DOMAIN;
    private static final String SUCCESSION_EXT_ID = "entra-grace-001";

    // ── injected beans ────────────────────────────────────────────────────────

    @LocalServerPort
    private int port;

    @Autowired
    private ScimTokens scimTokens;

    @Autowired
    private ScimTokenRepository tokenRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleBindingRepository assignmentRepository;

    @Autowired
    private DirectoryGroupRepository groupRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    /** Mocked so void permission checks in AdminUserService pass silently. */
    @MockitoBean
    private AuthorizationGate authorizationGate;

    /** Answers step 8's open succession the way a declared one does; the declaration itself is closed. */
    @MockitoBean
    private SuccessionAddresses successionAddresses;

    private final HttpClient http = HttpClient.newHttpClient();

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity"})
    void setUp() throws Exception {
        if (!SEEDED.compareAndSet(false, true)) {
            return;
        }

        // ── seed orgs ────────────────────────────────────────────────────────
        ensureOrg(ORGANIZATION, "provision-org-a");
        ensureOrg(TENANT_B, "provision-org-b");
        tokenRepository.deleteByOrganizationId(ORGANIZATION);
        tokenRepository.deleteByOrganizationId(TENANT_B);

        String groupScimId = null;
        Long seededRoleId = null;

        try {

            // ── STEP 1: generate token; POST /scim/v2/Users → 201 ────────────
            TOKEN_A_PLAINTEXT = scimTokens.generate(ORGANIZATION, "provision-test", null);

            var createResp = scimRequest("POST", "/scim/v2/Users", TOKEN_A_PLAINTEXT, """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                     "userName":"%s","externalId":"%s","displayName":"Alice Test",
                     "active":true,"emails":[{"value":"%s","type":"work","primary":true}]}
                    """.formatted(EMAIL, EXT_ID, EMAIL));
            STEP1_CREATE_STATUS = createResp.statusCode();
            USER_SCIM_ID = jsonMapper.readTree(createResp.body()).get("id").asText();

            // Snapshot step-1 user state before JIT modifies it
            userRepository.findByOrganizationIdAndScimId(ORGANIZATION, USER_SCIM_ID).ifPresent(u -> {
                SNAP_USER_ACTIVE = Boolean.TRUE.equals(u.getActive());
                SNAP_USER_EMAIL = u.getEmail();
                SNAP_USER_EXT_ID = u.getExternalId();
                SNAP_USER_ID_NULL = u.getUserId() == null;
            });

            // Snapshot token hash (FIX 4)
            var tokens = tokenRepository.findByOrganizationId(ORGANIZATION);
            if (!tokens.isEmpty()) {
                SNAP_TOKEN_HASH = tokens.get(0).getTokenHash();
            }

            // ── STEP 2: JIT reconciliation ────────────────────────────────────
            // provision() takes claims directly - no SecurityContext required
            userService.provision(IdentityClaims.builder().organizationId(ORGANIZATION).userId(USER_SUB).email(EMAIL)
                    .displayName("Alice Test").build());

            // Snapshot step-2 state: userId attached and user still active in directory
            SNAP_JIT_USER_ID = userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_SUB)
                    .map(u -> u.getUserId()).orElse(null);
            SNAP_JIT_USER_ACTIVE = userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_SUB)
                    .map(u -> Boolean.TRUE.equals(u.getActive())).orElse(null);

            // ── STEP 3: PATCH active:false → de-provision ─────────────────────
            var patchResp = scimRequest("PATCH", "/scim/v2/Users/" + USER_SCIM_ID, TOKEN_A_PLAINTEXT, """
                    {"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                     "Operations":[{"op":"replace","path":"active","value":false}]}
                    """);
            STEP3_PATCH_STATUS = patchResp.statusCode();

            SNAP_DEPROVISIONED_ACTIVE = userRepository.findByOrganizationIdAndScimId(ORGANIZATION, USER_SCIM_ID)
                    .map(u -> Boolean.TRUE.equals(u.getActive())).orElse(null);

            // ── STEP 4: groups + role binding + access read-side ──────────────
            var groupResp = scimRequest("POST", "/scim/v2/Groups", TOKEN_A_PLAINTEXT, """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                     "displayName":"Engineering","externalId":"okta-grp-eng-001"}
                    """);
            groupScimId = jsonMapper.readTree(groupResp.body()).get("id").asText();
            final String finalGroupScimId = groupScimId;

            SNAP_GROUP_RECON_KEY = groupRepository.findByOrganizationIdAndScimId(ORGANIZATION, groupScimId)
                    .map(g -> g.getReconciliationKey()).orElse(null);

            // Add the user to the group
            scimRequest("PATCH", "/scim/v2/Groups/" + groupScimId, TOKEN_A_PLAINTEXT, """
                    {"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                     "Operations":[{"op":"add","path":"members","value":[{"value":"%s"}]}]}
                    """.formatted(USER_SCIM_ID));

            // Seed: Role + GROUP role_assignment scoped to org A
            var role = roleRepository.save(RoleEntity.builder().organizationId(ORGANIZATION).name("scim-it-role")
                    .description("seeded by ScimProvisioningIntegrationTest").build());
            seededRoleId = role.getId();

            assignmentRepository.save(RoleBindingEntity.builder().organizationId(ORGANIZATION).subjectType("GROUP")
                    .subjectId(SNAP_GROUP_RECON_KEY).subjectLabel("Engineering").roleId(seededRoleId).build());

            // Re-activate so AdminUserService can find the user in UserDirectory
            userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_SUB)
                    .ifPresent(u -> userRepository.save(u.toBuilder().active(true).build()));

            // Set JWT in SecurityContext so OrganizationContext resolves correctly
            withJwt("admin-tester", ORGANIZATION);
            STEP4_ADMIN_USER = adminUserService.byId(USER_SUB);
            SecurityContextHolder.clearContext();

            // ── STEP 5: RLS - generate second-organization token; capture HTTP results ─
            var tokenB = scimTokens.generate(TENANT_B, "rls-test", null);

            var listResp = scimRequest("GET", "/scim/v2/Users?filter=userName+eq+%22" + EMAIL + "%22", tokenB, null);
            SNAP_RLS_LIST_STATUS = listResp.statusCode();
            SNAP_RLS_LIST_TOTAL = jsonMapper.readTree(listResp.body()).get("totalResults").asInt();

            var getByIdResp = scimRequest("GET", "/scim/v2/Users/" + USER_SCIM_ID, tokenB, null);
            SNAP_RLS_GETBYID_STATUS = getByIdResp.statusCode();

            // ── STEP 6: additive minting + per-id revoke ──────────────────────
            // Two labeled tokens coexist for one Organization (additive); both authenticate.
            var rotate1 = scimTokens.generate(ORGANIZATION, "rotate-1", null);
            var rotate2 = scimTokens.generate(ORGANIZATION, "rotate-2", null);
            var probe = "/scim/v2/Users?filter=userName+eq+%22nobody%40probe.test%22";
            SNAP_TOK1_AUTH_STATUS = scimRequest("GET", probe, rotate1, null).statusCode();
            SNAP_TOK2_AUTH_STATUS = scimRequest("GET", probe, rotate2, null).statusCode();

            // Revoke exactly one by id (org-scoped). Mirrors what DELETE /scim-tokens/{id} does
            // server-side; the admin HTTP path needs an identity provider's JWT this SCIM harness does not wire.
            var rotate1Hash = TokenHasher.sha256Hex(rotate1);
            var rotate1Id = tokenRepository.findByOrganizationId(ORGANIZATION).stream()
                    .filter(t -> rotate1Hash.equals(t.getTokenHash())).map(ScimTokenEntity::getId).findFirst()
                    .orElseThrow();
            scimTokens.revoke(ORGANIZATION, rotate1Id);

            // Revoked token → 401; the sibling token still authenticates → 200.
            SNAP_TOK1_AFTER_REVOKE_STATUS = scimRequest("GET", probe, rotate1, null).statusCode();
            SNAP_TOK2_SURVIVOR_STATUS = scimRequest("GET", probe, rotate2, null).statusCode();

            // ── STEP 7: last_used_at advanced by the prior authenticated SCIM calls ──
            // Token A drove several authenticated SCIM requests (POST/PATCH); recordUsage runs
            // after the Organization GUC is set, so last_used_at must now be non-null. Read it
            // back via the org-scoped finder. Set before the GUC, it stays null (RLS no-op).
            var tokenAHash = TokenHasher.sha256Hex(TOKEN_A_PLAINTEXT);
            SNAP_TOKEN_A_LAST_USED_AT = tokenRepository.findByOrganizationId(ORGANIZATION).stream()
                    .filter(t -> tokenAHash.equals(t.getTokenHash())).map(ScimTokenEntity::getLastUsedAt).findFirst()
                    .orElse(null);

            // ── STEP 8: connection succession - the directory door ────────────
            // The only drive that puts adoption through Postgres. SuccessionAdoption's unit drives stub save() to
            // return their argument, so they prove what the builder carries and nothing about the write: that Spring
            // Data JDBC issues an UPDATE rather than an INSERT, that @Version holds, that created_at survives, and
            // that findActiveByEmail's hand-written SQL parses at all. Every SCIM create before this one returned
            // from adopt() with no succession open, so none of that SQL had ever reached the database.
            var member = userRepository.save(UserEntity.builder().organizationId(ORGANIZATION).userId(SUCCESSION_SUB)
                    .scimId(UUID.randomUUID().toString()).displayName("Grace Hopper").email(SUCCESSION_OLD_EMAIL)
                    .active(true).arrival(UserArrival.FIRST_SIGN_IN).build());
            SUCCESSION_SCIM_ID = member.getScimId();
            // Read back rather than taken off the saved entity: Postgres stores microseconds and an Instant carries
            // nanoseconds, so comparing an in-memory stamp against a read-back one would fail on precision alone.
            SNAP_ADOPT_CREATED_AT_BEFORE = userRepository
                    .findByOrganizationIdAndScimId(ORGANIZATION, SUCCESSION_SCIM_ID).map(UserEntity::getCreatedAt)
                    .orElse(null);

            when(successionAddresses.formerAddressOf(ORGANIZATION, SUCCESSION_NEW_EMAIL))
                    .thenReturn(Optional.of(SUCCESSION_OLD_EMAIL));

            var adoptResp = scimRequest("POST", "/scim/v2/Users", TOKEN_A_PLAINTEXT, """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                     "userName":"%s","externalId":"%s","displayName":"Grace B Hopper",
                     "active":true,"emails":[{"value":"%s","type":"work","primary":true}]}
                    """.formatted(SUCCESSION_NEW_EMAIL, SUCCESSION_EXT_ID, SUCCESSION_NEW_EMAIL));
            SNAP_ADOPT_STATUS = adoptResp.statusCode();
            SNAP_ADOPT_RESOURCE_ID = jsonMapper.readTree(adoptResp.body()).get("id").asText();
            SNAP_ADOPT_ROSTER_ROWS = jdbc.queryForObject(
                    "SELECT count(*) FROM identity_user WHERE organization_id = ? AND email IN (?, ?)", Long.class,
                    ORGANIZATION, SUCCESSION_OLD_EMAIL, SUCCESSION_NEW_EMAIL);
            userRepository.findByOrganizationIdAndScimId(ORGANIZATION, SUCCESSION_SCIM_ID).ifPresent(adopted -> {
                SNAP_ADOPT_CREATED_AT_AFTER = adopted.getCreatedAt();
                SNAP_ADOPT_EMAIL = adopted.getEmail();
                SNAP_ADOPT_EXT_ID = adopted.getExternalId();
                SNAP_ADOPT_USER_ID = adopted.getUserId();
            });

        } finally {

            // ── cleanup: remove all rows seeded by this test ──────────────────
            SecurityContextHolder.clearContext();

            // Remove group member and group (if created)
            if (groupScimId != null) {
                groupRepository.findByOrganizationIdAndScimId(ORGANIZATION, groupScimId).ifPresent(g -> {
                    jdbc.update("DELETE FROM directory_group_member WHERE organization_id = ? AND group_id = ?",
                            ORGANIZATION, g.getId());
                    groupRepository.delete(g);
                });
            }

            // Remove role assignment and role
            if (seededRoleId != null) {
                final Long finalRoleId = seededRoleId;
                assignmentRepository.findByRoleId(finalRoleId).forEach(assignmentRepository::delete);
                roleRepository.findById(finalRoleId).ifPresent(roleRepository::delete);
            }

            // Remove user
            userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_SUB).ifPresent(userRepository::delete);
            if (USER_SCIM_ID != null) {
                userRepository.findByOrganizationIdAndScimId(ORGANIZATION, USER_SCIM_ID)
                        .ifPresent(userRepository::delete);
            }

            // Remove the succession member
            jdbc.update("DELETE FROM identity_user WHERE organization_id = ? AND email IN (?, ?)", ORGANIZATION,
                    SUCCESSION_OLD_EMAIL, SUCCESSION_NEW_EMAIL);

            // Remove tokens
            tokenRepository.deleteByOrganizationId(ORGANIZATION);
            tokenRepository.deleteByOrganizationId(TENANT_B);
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── step 1 assertions ─────────────────────────────────────────────────────

    @Test
    void step1_scimCreate_returns201_andPersistsRowWithCorrectFields() {

        // then
        assertThat(STEP1_CREATE_STATUS).isEqualTo(201);
        assertThat(SNAP_USER_ACTIVE).as("user.active must be true at creation").isTrue();
        assertThat(SNAP_USER_ID_NULL).as("userId must be null before first JIT login").isTrue();
        assertThat(SNAP_USER_EMAIL).as("user.email must match posted userName").isEqualToIgnoringCase(EMAIL);
        assertThat(SNAP_USER_EXT_ID).as("user.externalId must match posted externalId").isEqualTo(EXT_ID);
    }

    @Test
    void step1_tokenHash_equalsTokenHasherSha256() {

        // then
        assertThat(SNAP_TOKEN_HASH).as("stored hash must not equal plaintext").isNotEqualTo(TOKEN_A_PLAINTEXT);
        assertThat(SNAP_TOKEN_HASH).as("stored hash must equal SHA-256(plaintext)")
                .isEqualTo(TokenHasher.sha256Hex(TOKEN_A_PLAINTEXT));
    }

    // ── step 2 assertions ─────────────────────────────────────────────────────

    @Test
    void step2_jitProvision_setsUserId_andUserIsActiveInDirectory() {

        // then
        assertThat(SNAP_JIT_USER_ID).as("JIT provision must set userId on the SCIM row").isEqualTo(USER_SUB);
        assertThat(SNAP_JIT_USER_ACTIVE).as("JIT provision must leave user active in the directory").isTrue();
    }

    // ── step 3 assertions ─────────────────────────────────────────────────────

    @Test
    void step3_patchActiveFalse_returns200_deactivatesRow() {

        // then
        assertThat(STEP3_PATCH_STATUS).isEqualTo(200);
        assertThat(SNAP_DEPROVISIONED_ACTIVE).as("user.active must be false after PATCH active:false").isFalse();
    }

    // ── step 4 assertions ─────────────────────────────────────────────────────

    @Test
    void step4_groupCreation_setsOktaReconciliationKeyFromDisplayName() {

        // then
        assertThat(SNAP_GROUP_RECON_KEY).as("Non-Entra provider: reconciliationKey must equal displayName at creation")
                .isEqualTo("Engineering");
    }

    @Test
    void step4_groupMembership_producesEffectiveRoleViaGroup() {

        // then
        assertThat(STEP4_ADMIN_USER).as("AdminUserService.byId must return a result").isNotNull();
        assertThat(STEP4_ADMIN_USER.effectiveRoles()).as("AdminUserService.byId must surface a GROUP-derived role")
                .anyMatch(r -> "GROUP".equals(r.via()));
    }

    // ── step 5 assertions ─────────────────────────────────────────────────────

    @Test
    void step5_rls_secondTenantToken_listReturnsEmpty() {

        // then
        assertThat(SNAP_RLS_LIST_STATUS).as("valid second-organization token must pass auth").isEqualTo(200);
        assertThat(SNAP_RLS_LIST_TOTAL).as("second-tenant bearer must not see tenant-1 users in filter response")
                .isEqualTo(0);
    }

    @Test
    void step5_rls_secondTenantToken_getByScimIdReturns404() {

        // then
        assertThat(SNAP_RLS_GETBYID_STATUS).as("second-tenant bearer must get 404 for tenant-1 user's scimId")
                .isEqualTo(404);
    }

    // ── step 6 assertions ─────────────────────────────────────────────────────

    @Test
    void step6_additiveMinting_bothTokensAuthenticate() {

        // then
        assertThat(SNAP_TOK1_AUTH_STATUS).as("first additive token must authenticate").isEqualTo(200);
        assertThat(SNAP_TOK2_AUTH_STATUS).as("second additive token must authenticate").isEqualTo(200);
    }

    @Test
    void step6_perIdRevoke_revokesOneTokenAndLeavesTheOther() {

        // then
        assertThat(SNAP_TOK1_AFTER_REVOKE_STATUS).as("revoked token must be rejected with 401").isEqualTo(401);
        assertThat(SNAP_TOK2_SURVIVOR_STATUS).as("sibling token must still authenticate after per-id revoke")
                .isEqualTo(200);
    }

    // ── step 7 assertions ───────────────────────────────────────────────────────

    @Test
    void step7_lastUsedAt_advancesAfterAuthenticatedScimCall() {

        // then - recordUsage ran after the Organization GUC was set, so the write matched the row
        assertThat(SNAP_TOKEN_A_LAST_USED_AT).as("last_used_at must advance after an authenticated SCIM call")
                .isNotNull();
    }

    // ── step 8 assertions (connection succession: the directory door) ─────────

    /**
     * A replacement directory issues a fresh {@code externalId} for everybody, so without adoption this push would have
     * been a second row and the roster would have doubled before anybody signed in.
     *
     * <p>
     * Deliberately database-backed. {@code created_at} surviving alongside a roster of exactly one is what shows the
     * write was an UPDATE of the member's own row rather than an INSERT beside it, and the request is a real
     * {@code POST /scim/v2/Users} so {@code findActiveByEmail}'s hand-written SQL is executed by Postgres rather than
     * by a mock that cannot parse it.
     */
    @Test
    void step8_underAnOpenSuccession_aRewrittenPushAdoptsTheMemberItRenames() {

        // then
        assertThat(SNAP_ADOPT_STATUS).as("a rewritten push must still be answered as a create").isEqualTo(201);
        assertThat(SNAP_ADOPT_RESOURCE_ID).as("adoption must answer with the resource the member already was")
                .isEqualTo(SUCCESSION_SCIM_ID);
        assertThat(SNAP_ADOPT_ROSTER_ROWS).as("the roster must not double").isEqualTo(1L);
        assertThat(SNAP_ADOPT_CREATED_AT_AFTER).as("adoption must UPDATE the member's row, not insert beside it")
                .isNotNull().isEqualTo(SNAP_ADOPT_CREATED_AT_BEFORE);
        assertThat(SNAP_ADOPT_EMAIL).as("the new directory's address must land").isEqualTo(SUCCESSION_NEW_EMAIL);
        assertThat(SNAP_ADOPT_EXT_ID).as("the new directory's externalId must land").isEqualTo(SUCCESSION_EXT_ID);
        assertThat(SNAP_ADOPT_USER_ID).as("adoption must keep the member's subject").isEqualTo(SUCCESSION_SUB);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Ensures the Organization registry row exists for the given organization id (the registry's string PK). SCIM is
     * now scoped to the Organization boundary (== organization), so there is no bigint sub-org id to mint.
     */
    private void ensureOrg(String organizationId, String name) {
        var exists = jdbc.queryForList("SELECT id FROM organization WHERE id = ?", organizationId);
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES (?, ?, 'ENTERPRISE', now(), now())", organizationId, name);
        }
    }

    private HttpResponse<String> scimRequest(String method, String path, String bearer, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + bearer).header("Content-Type", "application/scim+json");
        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(pub(body));
            case "PATCH" -> builder.method("PATCH", pub(body));
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.BodyPublisher pub(String json) {
        return json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.strip());
    }

    private static void withJwt(String sub, String organization) {
        var jwt = Jwt.withTokenValue("tok").header("alg", "RS256").subject(sub).claim("org", organization).build();
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt)));
    }
}
