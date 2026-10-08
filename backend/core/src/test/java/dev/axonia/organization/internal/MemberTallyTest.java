package dev.axonia.organization.internal;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.axonia.organization.DirectoryHeadcount;
import dev.axonia.organization.MemberCountPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTallyTest {

    private final DirectoryHeadcount directory = organizationId -> 4L;

    @Test
    void withNoPolicyTheDirectoryIsCounted() {

        assertThat(new MemberTally(List.of(), directory).of("acme")).isEqualTo(4L);
    }

    @Test
    void aPolicyWithNothingToSayLeavesTheDirectoryCount() {

        MemberCountPolicy silent = organizationId -> Optional.empty();

        assertThat(new MemberTally(List.of(silent), directory).of("acme")).isEqualTo(4L);
    }

    @Test
    void aPolicyThatAnswersForTheOrganizationDecidesWithoutAskingTheDirectory() {

        MemberCountPolicy ownTally = organizationId -> Optional.of(12L);
        DirectoryHeadcount unreachable = organizationId -> {
            throw new AssertionError("the directory is not asked when a policy answers");
        };

        assertThat(new MemberTally(List.of(ownTally), unreachable).of("rust-hub")).isEqualTo(12L);
    }
}
