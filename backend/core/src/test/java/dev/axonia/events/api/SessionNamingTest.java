package dev.axonia.events.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.events.application.CreateSeriesRequest;
import dev.axonia.events.application.ScheduleService;
import dev.axonia.events.application.SectionService;
import dev.axonia.events.application.SeriesService;
import dev.axonia.events.application.SessionFactsAssembler;
import dev.axonia.events.application.SessionService;
import dev.axonia.events.application.SlotService;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.Cadence;
import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A series date named after the fact, read back the way the page, the calendar and a mail each read it. The three used
 * to disagree: the page said the first slot's title, the calendar and the mail said the session's own.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class SessionNamingTest {

    private static final String ORG = "default";
    private static final String ORGANIZER = "session-naming-organizer";
    private static final String MEMBER = "session-naming-member";
    private static final LocalDate HELD_ON = LocalDate.of(2025, 9, 4);

    /** A community of its own per test: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger COMMUNITIES = new AtomicInteger();

    @Autowired
    private SessionNaming naming;

    @Autowired
    private SeriesService series;

    @Autowired
    private SessionService sessions;

    @Autowired
    private SectionService sections;

    @Autowired
    private SlotService slots;

    @Autowired
    private ScheduleService schedule;

    @Autowired
    private SessionFactsAssembler factsAssembler;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private JdbcTemplate jdbc;

    private String slug;
    private Long sessionId;

    @BeforeEach
    void seedAHeldSeriesDateWithATitledSlot() {

        slug = "session-naming-" + COMMUNITIES.incrementAndGet();
        var communityId = community(slug);
        signedInAs(ORGANIZER);

        var guild = series.start(slug, new CreateSeriesRequest("Backend Guild", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.of(2025, 9, 4), LocalTime.of(17, 0), "UTC", null, 60, List.of()));
        sessionId = sessions.ensure(guild.id(), HELD_ON).id();
        var sectionTypeId = jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, "
                        + "expects_presenter, icon, accent_color, created_at, updated_at, version) "
                        + "VALUES (?, ?, true, 'wrench', '#123456', now(), now(), 0) RETURNING id",
                Long.class, ORG, "Talks of " + slug);
        var sectionId = sections.addSection(sessionId, new SectionTemplate(sectionTypeId, "Talks", 60)).sections()
                .getFirst().id();
        slots.recordSlot(sessionId, sectionId, new RecordSlotRequest("P1s", 15, null, List.of(), List.of("kafka")));
        jdbc.update("UPDATE community_session SET status = 'HELD', closed_at = scheduled_at WHERE id = ?", sessionId);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'MEMBER', now(), now(), 0)", ORG, communityId, MEMBER);
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a held series date takes its new name on the page, the calendar and in its mails alike")
    void everySurfaceShowsTheNewName() {

        var renamed = naming.rename(sessionId, "S26E10 - DevTool");

        assertThat(renamed.status()).isEqualTo(SessionStatus.HELD);
        assertThat(renamed.displayTitle()).isEqualTo("S26E10 - DevTool");
        assertThat(renamed.automaticTitle()).isEqualTo("P1s");
        assertThat(sessions.getDetail(sessionId).displayTitle()).isEqualTo("S26E10 - DevTool");
        assertThat(calendarName()).isEqualTo("S26E10 - DevTool");
        assertThat(mailName()).isEqualTo("S26E10 - DevTool");
    }

    @Test
    @DisplayName("clearing the name falls back to the first titled slot, everywhere at once")
    void clearingFallsBackToTheFirstTitledSlot() {

        naming.rename(sessionId, "S26E10 - DevTool");

        var cleared = naming.rename(sessionId, "   ");

        assertThat(cleared.title()).isNull();
        assertThat(cleared.displayTitle()).isEqualTo("P1s");
        assertThat(calendarName()).isEqualTo("P1s");
        assertThat(mailName()).isEqualTo("P1s");
    }

    @Test
    @DisplayName("a plain member cannot rename a session")
    void aPlainMemberIsRefused() {

        signedInAs(MEMBER);

        assertThatThrownBy(() -> naming.rename(sessionId, "Mine now")).isInstanceOf(ForbiddenException.class);
        assertThat(jdbc.queryForObject("SELECT title FROM community_session WHERE id = ?", String.class, sessionId))
                .isNull();
    }

    private String calendarName() {

        return schedule.schedule(slug, Instant.parse("2025-09-01T00:00:00Z"), Instant.parse("2025-09-08T00:00:00Z"))
                .stream().filter(entry -> sessionId.equals(entry.sessionId())).findFirst().orElseThrow().displayTitle();
    }

    private Object mailName() {

        var row = sessionStore.findByOrganizationIdAndId(ORG, sessionId).orElseThrow();
        return factsAssembler.facts(ORG, row).get("sessionTitle");
    }

    private long community(String communitySlug) {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        for (var user : List.of(ORGANIZER, MEMBER)) {
            jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, "
                    + "arrival, created_at, updated_at, version) VALUES (?, ?, ?, null, true, ?, 'SYNCED', now(), "
                    + "now(), 0) ON CONFLICT DO NOTHING", ORG, user, user, "scim-" + user);
        }
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', "
                        + "(SELECT min(id) FROM community_kind WHERE organization_id = ?), now(), now(), 0) "
                        + "RETURNING id",
                Long.class, ORG, communitySlug, communitySlug, ORG);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'ORGANIZER', now(), now(), 0)", ORG, communityId, ORGANIZER);
        return communityId;
    }

    private static void signedInAs(String userId) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
