package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.domain.SubscriptionTokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarSubscriptionRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private CalendarSubscriptionRepository calendarSubscriptionRepository;

    private static CalendarSubscriptionEntity.CalendarSubscriptionEntityBuilder any() {

        return CalendarSubscriptionEntity.builder().organizationId("default").communityId(1L).userId("alice")
                .tokenHash(SubscriptionTokens.hash(SubscriptionTokens.mint()));
    }

    @Test
    void findByTokenHash() {

        // given
        var token = SubscriptionTokens.mint();
        var saved = calendarSubscriptionRepository.save(any().tokenHash(SubscriptionTokens.hash(token)).build());

        // when
        var actual = calendarSubscriptionRepository.findByTokenHash(SubscriptionTokens.hash(token));

        // then
        assertThat(actual).isPresent().get().satisfies(entity -> {
            assertThat(entity.getId()).isEqualTo(saved.getId());
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getLastFetchedAt()).isNull();
            assertThat(entity.getLastFetchedAgent()).isNull();
        });
    }

    @Test
    void findByTokenHash_unknownToken() {

        // when
        var actual = calendarSubscriptionRepository.findByTokenHash(SubscriptionTokens.hash("never minted"));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void findByOrganizationIdAndCommunityIdAndUserId_otherTenantIsIsolated() {

        // given
        calendarSubscriptionRepository.save(any().build());

        // when
        var actual = calendarSubscriptionRepository.findByOrganizationIdAndCommunityIdAndUserId("other-organization",
                1L, "alice");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void save_secondSubscriptionForTheSameMemberAndCommunity_violatesUniqueConstraint() {

        // given
        calendarSubscriptionRepository.save(any().build());

        // when / then
        assertThatThrownBy(() -> calendarSubscriptionRepository.save(any().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stampFetch() {

        // given
        var saved = calendarSubscriptionRepository.save(any().build());
        var at = Instant.parse("2026-08-15T10:15:00Z");

        // when
        calendarSubscriptionRepository.stampFetch(saved.getId(), at, "Outlook/16.0");

        // then
        assertThat(calendarSubscriptionRepository.findById(saved.getId())).isPresent().get().satisfies(entity -> {
            assertThat(entity.getLastFetchedAt()).isEqualTo(at);
            assertThat(entity.getLastFetchedAgent()).isEqualTo("Outlook/16.0");
        });
    }

    /**
     * The revocation path against a real row rather than a mock: reset is an UPDATE under {@code @Version}, and it is
     * the whole of what makes a leaked feed URL recoverable.
     */
    @Test
    void save_resetReplacesTheTokenInPlaceSoTheOldOneStopsResolving() {

        // given - a subscription a calendar has already fetched at least once
        var oldToken = SubscriptionTokens.mint();
        var saved = calendarSubscriptionRepository.save(any().tokenHash(SubscriptionTokens.hash(oldToken)).build());
        calendarSubscriptionRepository.stampFetch(saved.getId(), Instant.parse("2026-08-14T08:00:00Z"), "Outlook/16.0");
        var stamped = calendarSubscriptionRepository.findById(saved.getId()).orElseThrow();

        // when
        var newToken = SubscriptionTokens.mint();
        var reset = calendarSubscriptionRepository.save(stamped.toBuilder().tokenHash(SubscriptionTokens.hash(newToken))
                .lastFetchedAt(null).lastFetchedAgent(null).build());

        // then - one row, updated in place, and the old URL no longer resolves
        assertThat(reset.getId()).isEqualTo(saved.getId());
        assertThat(reset.getVersion()).isEqualTo(stamped.getVersion() + 1);
        assertThat(calendarSubscriptionRepository.count()).isEqualTo(1);
        assertThat(calendarSubscriptionRepository.findByTokenHash(SubscriptionTokens.hash(oldToken))).isEmpty();
        assertThat(calendarSubscriptionRepository.findByTokenHash(SubscriptionTokens.hash(newToken))).isPresent().get()
                .satisfies(entity -> {
                    assertThat(entity.getId()).isEqualTo(saved.getId());
                    assertThat(entity.getCreatedAt()).isEqualTo(stamped.getCreatedAt());
                    assertThat(entity.getLastFetchedAt()).isNull();
                    assertThat(entity.getLastFetchedAgent()).isNull();
                });
    }

    @Test
    void deleteByOrganizationIdAndUserId() {

        // given
        var mine = calendarSubscriptionRepository.save(any().build());
        var theirs = calendarSubscriptionRepository.save(any().userId("bob").build());

        // when
        calendarSubscriptionRepository.deleteByOrganizationIdAndUserId("default", "alice");

        // then
        assertThat(calendarSubscriptionRepository.findById(mine.getId())).isEmpty();
        assertThat(calendarSubscriptionRepository.findById(theirs.getId())).isPresent();
    }

    @Test
    void deleteByOrganizationId() {

        // given
        var here = calendarSubscriptionRepository.save(any().build());
        var elsewhere = calendarSubscriptionRepository.save(any().organizationId("other-organization").build());

        // when
        calendarSubscriptionRepository.deleteByOrganizationId("default");

        // then
        assertThat(calendarSubscriptionRepository.findById(here.getId())).isEmpty();
        assertThat(calendarSubscriptionRepository.findById(elsewhere.getId())).isPresent();
    }
}
