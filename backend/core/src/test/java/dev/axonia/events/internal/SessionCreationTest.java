package dev.axonia.events.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.CommunicationSettings;
import dev.axonia.events.CommunityAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionCreationTest {

    private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");

    @Mock
    private CommunityAccess communityAccess;

    private SessionCreation sut;

    @BeforeEach
    void wire() {

        sut = new SessionCreation(new UpcomingReleasePolicy(communityAccess, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private void leadDays(String organizationId, long communityId, int days) {

        when(communityAccess.communicationSettings(organizationId, communityId))
                .thenReturn(new CommunicationSettings(true, true, days, 24));
    }

    private NewSession spec(String organizationId, long communityId, Instant scheduledAt) {

        return NewSession.builder().organizationId(organizationId).communityId(communityId).seriesId(1L)
                .status("SCHEDULED").scheduledAt(scheduledAt).durationMinutes(60).sections(List.of()).build();
    }

    @Test
    void newSession_derivesTheReleaseFromTheCommunitysLeadTime() {

        // given a session eleven days out in a community that announces three days ahead
        leadDays("default", 1L, 3);
        var scheduledAt = NOW.plus(11, ChronoUnit.DAYS);

        // when
        var created = sut.newSession(spec("default", 1L, scheduledAt));

        // then
        assertThat(created.getUpcomingReleaseAt()).isEqualTo(scheduledAt.minus(3, ChronoUnit.DAYS));
    }

    /**
     * The lead time is the session's own community's, and the lookup is keyed by organization as well: the same
     * community id in a different organization is a different community, and reading the settings through an ambient
     * request-scoped organization instead of the row's own would answer for whichever tenant happened to be calling.
     */
    @Test
    void newSession_takesTheLeadTimeFromTheSessionsOwnCommunityAndOrganization() {

        // given two communities in one organization, and a third sharing an id with the first across a tenant boundary
        leadDays("acme", 4L, 2);
        leadDays("acme", 9L, 14);
        leadDays("globex", 4L, 30);
        var scheduledAt = NOW.plus(60, ChronoUnit.DAYS);

        // when
        var acmeQuick = sut.newSession(spec("acme", 4L, scheduledAt));
        var acmeEarly = sut.newSession(spec("acme", 9L, scheduledAt));
        var globex = sut.newSession(spec("globex", 4L, scheduledAt));

        // then
        assertThat(acmeQuick.getUpcomingReleaseAt()).isEqualTo(scheduledAt.minus(2, ChronoUnit.DAYS));
        assertThat(acmeEarly.getUpcomingReleaseAt()).isEqualTo(scheduledAt.minus(14, ChronoUnit.DAYS));
        assertThat(globex.getUpcomingReleaseAt()).isEqualTo(scheduledAt.minus(30, ChronoUnit.DAYS));
    }

    /**
     * A session created inside its own lead window stores no intent, so it is visibly off rather than carrying a
     * release instant that has already gone by and could never be claimed.
     */
    @Test
    void newSession_storesNoIntentForASessionCreatedInsideItsOwnLeadWindow() {

        // given a session tomorrow in a community that announces three days ahead
        leadDays("default", 1L, 3);

        // when
        var created = sut.newSession(spec("default", 1L, NOW.plus(1, ChronoUnit.DAYS)));

        // then
        assertThat(created.getUpcomingReleaseAt()).isNull();
    }

    @Test
    void newSession_storesNoIntentForASessionBackfilledIntoThePast() {

        // given a session imported from another tool, held two years ago
        leadDays("default", 1L, 3);

        // when
        var created = sut.newSession(spec("default", 1L, NOW.minus(730, ChronoUnit.DAYS)));

        // then
        assertThat(created.getUpcomingReleaseAt()).isNull();
    }

    /**
     * "In the future" is strict. A derived instant landing exactly on {@code now} is already past by the time any
     * producer could claim it, so it is stored as no intent rather than as an instant that will never fire.
     */
    @Test
    void newSession_storesNoIntentWhenTheDerivedInstantIsExactlyNow() {

        // given
        leadDays("default", 1L, 3);

        // when
        var created = sut.newSession(spec("default", 1L, NOW.plus(3, ChronoUnit.DAYS)));

        // then
        assertThat(created.getUpcomingReleaseAt()).isNull();
    }

    /**
     * Building the row is nine explicit assignments, so each one is real work that can be silently dropped. Three of
     * them are covered nowhere else: {@code organizationId} only fails at runtime, on a {@code NOT NULL} insert, and
     * {@code communityId} and {@code durationMinutes} not even then, since {@code duration_minutes} is nullable.
     *
     * <p>
     * The two {@code NOT NULL} text columns are the other half of the same statement, and are this class's to supply
     * rather than a caller's to remember: {@link NewSession} cannot express them at all, and a row saved with either
     * left null violates the schema.
     */
    @Test
    void newSession_carriesWhatTheCallerStatedAndSuppliesTheEmptyTextColumns() {

        // given values distinct from every other fixture here, so a hardcoded one would not pass by coincidence
        leadDays("acme", 7L, 3);
        var stated = NewSession.builder().organizationId("acme").communityId(7L).seriesId(1L).status("SCHEDULED")
                .scheduledAt(NOW.plus(11, ChronoUnit.DAYS)).durationMinutes(45).sections(List.of()).build();

        // when
        var created = sut.newSession(stated);

        // then what the caller stated reaches the row
        assertThat(created.getOrganizationId()).isEqualTo("acme");
        assertThat(created.getCommunityId()).isEqualTo(7L);
        assertThat(created.getDurationMinutes()).isEqualTo(45);

        // and the two columns the caller cannot state are supplied here
        assertThat(created.getNotes()).isEmpty();
        assertThat(created.getRecordingUrl()).isEmpty();
    }
}
