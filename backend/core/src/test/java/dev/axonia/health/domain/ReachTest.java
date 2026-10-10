package dev.axonia.health.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.health.api.OrgUnitLabels;

import static org.assertj.core.api.Assertions.assertThat;

class ReachTest {

    @Test
    void of_noUnits_isUnavailable() {

        // when no contributor has a synced unit (self-serve tenant)
        var actual = Reach.of(List.of(), 5);

        // then the dial is unavailable, never a misleading zero
        assertThat(actual.available()).isFalse();
        assertThat(actual.teams()).isZero();
        assertThat(actual.contributorsWithUnit()).isZero();
        assertThat(actual.totalContributors()).isEqualTo(5);
    }

    @Test
    void of_countsDistinctAtEachGrain() {

        // given three contributors across two departments of one business unit
        var units = List.of(new OrgUnitLabels("Acme", "Platform", "Payments"),
                new OrgUnitLabels("Acme", "Platform", "Checkout"), new OrgUnitLabels("Acme", "Mobile", "iOS"));

        // when
        var actual = Reach.of(units, 3);

        // then
        assertThat(actual.teams()).isEqualTo(3);
        assertThat(actual.departments()).isEqualTo(2);
        assertThat(actual.businessUnits()).isEqualTo(1);
        assertThat(actual.contributorsWithUnit()).isEqualTo(3);
        assertThat(actual.available()).isTrue();
    }

    @Test
    void of_sameTeamNameUnderDifferentParentsStaysDistinct() {

        // given two teams both named "Core" but under different departments
        var units = List.of(new OrgUnitLabels("Acme", "Platform", "Core"), new OrgUnitLabels("Acme", "Mobile", "Core"));

        // when
        var actual = Reach.of(units, 2);

        // then they are two distinct teams and two distinct departments
        assertThat(actual.teams()).isEqualTo(2);
        assertThat(actual.departments()).isEqualTo(2);
    }

    @Test
    void of_coarseOnlyContributorCountsCoarserGrainsOnly() {

        // given one contributor with only department data, one with a full path
        var units = List.of(new OrgUnitLabels("Acme", "Platform", null),
                new OrgUnitLabels("Acme", "Platform", "Payments"));

        // when
        var actual = Reach.of(units, 2);

        // then the team-less contributor lifts only the coarser counts
        assertThat(actual.teams()).isEqualTo(1);
        assertThat(actual.departments()).isEqualTo(1);
        assertThat(actual.businessUnits()).isEqualTo(1);
    }

    @Test
    void of_partialCoverageIsReflected() {

        // given only one of four contributors has a synced unit
        var units = List.of(new OrgUnitLabels("Acme", "Platform", "Payments"));

        // when
        var actual = Reach.of(units, 4);

        // then coverage is honest about its blind spot
        assertThat(actual.contributorsWithUnit()).isEqualTo(1);
        assertThat(actual.totalContributors()).isEqualTo(4);
        assertThat(actual.available()).isTrue();
    }

    @Test
    void of_allContributorsInOneTeam() {

        // given the whole contribution sits in a single team
        var units = List.of(new OrgUnitLabels("Acme", "Platform", "Payments"),
                new OrgUnitLabels("Acme", "Platform", "Payments"));

        // when
        var actual = Reach.of(units, 2);

        // then teams == 1 — the fragility signal this dial exists to show
        assertThat(actual.teams()).isEqualTo(1);
    }

    @Test
    void of_multiWordLabelsDoNotCollideAcrossGrains() {

        // given two genuinely distinct departments that a space-joined key would merge
        var units = List.of(new OrgUnitLabels("Acme Mobile", "iOS", "Core"),
                new OrgUnitLabels("Acme", "Mobile iOS", "Core"));

        // when
        var actual = Reach.of(units, 2);

        // then all grains stay distinct (with null-byte separator; with space they'd collide at dept/team level)
        assertThat(actual.businessUnits()).isEqualTo(2);
        assertThat(actual.departments()).isEqualTo(2);
        assertThat(actual.teams()).isEqualTo(2);
    }
}
