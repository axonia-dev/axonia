package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.expertise.CommunityTier;
import dev.axonia.expertise.EvidenceItem;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import dev.axonia.expertise.Tier;
import dev.axonia.expertise.mother.ExpertiseSignalMother;

import static org.assertj.core.api.Assertions.assertThat;

class TierPolicyTest {

    @Test
    void tierOf_practitionerWhenAnyPresented() {

        var signals = List.of(ExpertiseSignalMother.any().signalType(SignalType.CONTRIBUTED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.PRESENTED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.OFFERED).build());

        assertThat(TierPolicy.tierOf(signals)).isEqualTo(Tier.PRACTITIONER);
    }

    @Test
    void tierOf_contributorWhenOnlyContributed() {

        var signals = List.of(ExpertiseSignalMother.any().signalType(SignalType.CONTRIBUTED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.CONTRIBUTED).build());

        assertThat(TierPolicy.tierOf(signals)).isEqualTo(Tier.CONTRIBUTOR);
    }

    @Test
    void tierOf_interestedWhenOnlyOffered() {

        var signals = List.of(ExpertiseSignalMother.any().signalType(SignalType.OFFERED).build());

        assertThat(TierPolicy.tierOf(signals)).isEqualTo(Tier.INTERESTED);
    }

    @Test
    void tierOf_interestedWhenEmpty() {

        assertThat(TierPolicy.tierOf(List.of())).isEqualTo(Tier.INTERESTED);
    }

    @Test
    void evidenceOf_countsPerType() {

        var signals = List.of(ExpertiseSignalMother.any().signalType(SignalType.PRESENTED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.PRESENTED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.CONTRIBUTED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.OFFERED).build());

        var evidence = TierPolicy.evidenceOf(signals, 10);

        assertThat(evidence.presentedCount()).isEqualTo(2);
        assertThat(evidence.contributedCount()).isEqualTo(1);
        assertThat(evidence.offeredCount()).isEqualTo(1);
    }

    @Test
    void evidenceOf_completedSurfacesInRecentButIsNotBucketed() {

        var signals = List.of(ExpertiseSignalMother.any().signalType(SignalType.COMPLETED).build());

        var evidence = TierPolicy.evidenceOf(signals, 10);

        assertThat(evidence.presentedCount()).isZero();
        assertThat(evidence.contributedCount()).isZero();
        assertThat(evidence.offeredCount()).isZero();
        assertThat(evidence.recent()).hasSize(1);
        assertThat(evidence.recent().get(0).signalType()).isEqualTo(SignalType.COMPLETED);
    }

    @Test
    void evidenceOf_recentLimitNewestFirst() {

        var older = ExpertiseSignalMother.any().signalType(SignalType.PRESENTED)
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        var middle = ExpertiseSignalMother.any().signalType(SignalType.CONTRIBUTED)
                .occurredAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        var newest = ExpertiseSignalMother.any().signalType(SignalType.OFFERED)
                .occurredAt(Instant.parse("2026-03-01T00:00:00Z")).build();

        var evidence = TierPolicy.evidenceOf(List.of(older, newest, middle), 2);

        assertThat(evidence.recent()).hasSize(2);
        assertThat(evidence.recent().get(0).occurredAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(evidence.recent().get(1).occurredAt()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void rank_orderingPractitionerContributorInterested() {

        assertThat(TierPolicy.rank(Tier.PRACTITIONER)).isEqualTo(0);
        assertThat(TierPolicy.rank(Tier.CONTRIBUTOR)).isEqualTo(1);
        assertThat(TierPolicy.rank(Tier.INTERESTED)).isEqualTo(2);
        assertThat(TierPolicy.rank(Tier.PRACTITIONER)).isLessThan(TierPolicy.rank(Tier.CONTRIBUTOR));
        assertThat(TierPolicy.rank(Tier.CONTRIBUTOR)).isLessThan(TierPolicy.rank(Tier.INTERESTED));
    }

    @Test
    void orderedItems_sortsNewestFirstThenBySource() {

        var older = ExpertiseSignalMother.any().signalType(SignalType.CONTRIBUTED).sourceType(SourceType.ARTIFACT)
                .sourceId(2L).occurredAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        var newer = ExpertiseSignalMother.any().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT)
                .sourceId(9L).occurredAt(Instant.parse("2026-02-01T00:00:00Z")).build();

        var items = TierPolicy.orderedItems(List.of(older, newer));

        assertThat(items).extracting(EvidenceItem::sourceId).containsExactly(9L, 2L);
        assertThat(items).hasSize(2); // no cap, unlike evidenceOf
    }

    @Test
    void openToQuestionsRanksBelowInterested() {

        assertThat(TierPolicy.rank(Tier.OPEN_TO_QUESTIONS)).isGreaterThan(TierPolicy.rank(Tier.INTERESTED));
    }

    @Test
    void evidenceOf_countsOfferedSignals() {

        var signals = List.of(ExpertiseSignalMother.any().signalType(SignalType.OFFERED).build(),
                ExpertiseSignalMother.any().signalType(SignalType.OFFERED).build());

        var evidence = TierPolicy.evidenceOf(signals, 10);

        assertThat(evidence.offeredCount()).isEqualTo(2);
        assertThat(evidence.presentedCount()).isEqualTo(0);
        assertThat(evidence.contributedCount()).isEqualTo(0);
    }

    @Test
    void tiersByCommunity_rollsUpEachCommunitysOwnSignals() {

        var presentedInOne = ExpertiseSignalMother.any().communityId(1L).signalType(SignalType.PRESENTED).build();
        var contributedInTwo = ExpertiseSignalMother.any().communityId(2L).signalType(SignalType.CONTRIBUTED).build();
        var offeredInTwo = ExpertiseSignalMother.any().communityId(2L).signalType(SignalType.OFFERED).build();
        var noCommunity = ExpertiseSignalMother.any().communityId(null).signalType(SignalType.PRESENTED).build();

        var tiers = TierPolicy.tiersByCommunity(List.of(presentedInOne, contributedInTwo, offeredInTwo, noCommunity));

        assertThat(tiers).containsExactly(new CommunityTier(1L, Tier.PRACTITIONER),
                new CommunityTier(2L, Tier.CONTRIBUTOR));
    }

    @Test
    void tiersByCommunity_isEmptyWithoutSignals() {

        assertThat(TierPolicy.tiersByCommunity(List.of())).isEmpty();
    }
}
