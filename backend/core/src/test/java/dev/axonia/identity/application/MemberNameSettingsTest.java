package dev.axonia.identity.application;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.adapter.out.persistence.JdbcUserStore;
import dev.axonia.identity.adapter.out.persistence.UserEntity;
import dev.axonia.identity.adapter.out.persistence.UserRepository;
import dev.axonia.identity.api.spi.MemberNamesGate;
import dev.axonia.identity.application.MemberNameSettings.PreviewRow;
import dev.axonia.identity.domain.NamePatterns;
import dev.axonia.identity.domain.UserArrival;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Over the real directory rows, assembled by hand so the class joins the plain JDBC context: a save must rewrite the
 * stored names, and a mocked store would accept any write.
 */
class MemberNameSettingsTest extends CoreJdbcTestSupport {

    private static final String ORG = "member-names-org";
    private static final String HOUSE_FORMAT = "{family}, {given}-NORTHWIND";

    @Autowired
    private UserRepository userRepository;

    private final MemberNamesGate gate = mock(MemberNamesGate.class);
    private final FixedNamePatterns patterns = new FixedNamePatterns();
    private MemberNameSettings sut;

    @BeforeEach
    void setUp() {

        sut = new MemberNameSettings(gate, patterns, new JdbcUserStore(userRepository));
        var jwt = Jwt.withTokenValue("tok").header("alg", "RS256").subject("admin").claim("org", ORG).build();
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt)));
        member("scim-octocat", "octocat");
        member("scim-aurelia", "Rochat, Aurélia-NORTHWIND");
        member("scim-jonathan", "Jacquat, Jonathan-NORTHWIND");
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void savingReadsEveryMembersNameAndLeavesAnUnmatchedOneAlone() {

        // when
        var changed = sut.save(List.of(HOUSE_FORMAT));

        // then
        assertThat(changed).isEqualTo(2);
        assertThat(sut.patterns()).containsExactly(HOUSE_FORMAT);
        assertThat(stored("scim-aurelia"))
                .extracting(UserEntity::getDisplayName, UserEntity::getGivenName, UserEntity::getReceivedName)
                .containsExactly("Aurélia Rochat", "Aurélia", "Rochat, Aurélia-NORTHWIND");
        assertThat(stored("scim-jonathan")).extracting(UserEntity::getDisplayName, UserEntity::getGivenName)
                .containsExactly("Jonathan Jacquat", "Jonathan");
        assertThat(stored("scim-octocat"))
                .extracting(UserEntity::getDisplayName, UserEntity::getGivenName, UserEntity::getVersion)
                .containsExactly("octocat", null, 0L);
    }

    @Test
    void savingNoPatternRestoresEveryReceivedName() {

        // given
        sut.save(List.of(HOUSE_FORMAT));

        // when
        var changed = sut.save(List.of());

        // then
        assertThat(changed).isEqualTo(2);
        assertThat(userRepository.findAllByOrganizationId(ORG)).isNotEmpty().allSatisfy(member -> {
            assertThat(member.getDisplayName()).isEqualTo(member.getReceivedName());
            assertThat(member.getGivenName()).isNull();
        });
    }

    @Test
    void savingTheSameListAgainChangesNobody() {

        // given
        sut.save(List.of(HOUSE_FORMAT));

        // when / then
        assertThat(sut.save(List.of(HOUSE_FORMAT))).isZero();
    }

    @Test
    void thePreviewListsTheMembersAPatternChangesFirstAndWritesNothing() {

        // when
        var rows = sut.preview(List.of(HOUSE_FORMAT));

        // then
        assertThat(rows).containsExactly(
                new PreviewRow("Jacquat, Jonathan-NORTHWIND", "Jonathan Jacquat", "Jonathan", 0),
                new PreviewRow("Rochat, Aurélia-NORTHWIND", "Aurélia Rochat", "Aurélia", 0),
                new PreviewRow("octocat", "octocat", null, null));
        assertThat(stored("scim-aurelia").getDisplayName()).isEqualTo("Rochat, Aurélia-NORTHWIND");
        assertThat(sut.patterns()).isEmpty();
    }

    @Test
    void thePreviewShowsAtMostTwelveMembers() {

        // given
        for (var i = 0; i < MemberNameSettings.PREVIEW_ROWS; i++) {
            member("scim-extra-" + i, "Extra" + i + ", Member-NORTHWIND");
        }

        // when / then
        assertThat(sut.preview(List.of(HOUSE_FORMAT))).hasSize(MemberNameSettings.PREVIEW_ROWS)
                .allSatisfy(row -> assertThat(row.matched()).isZero());
    }

    @Test
    void aRefusedPatternIsNamedByItsPlaceInTheList() {

        assertThatThrownBy(() -> sut.preview(List.of(HOUSE_FORMAT, "{given} {family}", "{given}", "{family}")))
                .isInstanceOfSatisfying(MemberNamesRefusedException.class, refusal -> {
                    assertThat(refusal.getCode()).isEqualTo("NAME_PATTERN_INVALID");
                    assertThat(refusal.getField()).isEqualTo("patterns[2]");
                });
    }

    @Test
    void aListTooLongIsNamedAsAWholeAndWritesNothing() {

        var eleven = Collections.nCopies(NamePatterns.MAX + 1, HOUSE_FORMAT);

        assertThatThrownBy(() -> sut.save(eleven)).isInstanceOfSatisfying(MemberNamesRefusedException.class,
                refusal -> {
                    assertThat(refusal.getCode()).isEqualTo("NAME_PATTERNS_TOO_MANY");
                    assertThat(refusal.getField()).isEqualTo("patterns");
                });
        assertThat(patterns.find(ORG).patterns()).isEmpty();
    }

    @Test
    void savingNeedsTheWriteGateAndWritesNothingWithoutIt() {

        // given
        doThrow(new ForbiddenException("no")).when(gate).requireWrite();

        // when / then
        assertThatThrownBy(() -> sut.save(List.of(HOUSE_FORMAT))).isInstanceOf(ForbiddenException.class);
        assertThat(patterns.find(ORG).patterns()).isEmpty();
        assertThat(stored("scim-aurelia").getDisplayName()).isEqualTo("Rochat, Aurélia-NORTHWIND");
    }

    @Test
    void readingAndPreviewingAskTheReadGate() {

        // when
        sut.patterns();
        sut.preview(List.of());

        // then
        verify(gate, times(2)).requireRead();
    }

    private void member(String scimId, String received) {

        userRepository.save(UserEntity.builder().organizationId(ORG).scimId(scimId).arrival(UserArrival.SYNCED)
                .displayName(received).receivedName(received).active(true).build());
    }

    private UserEntity stored(String scimId) {

        return userRepository.findByOrganizationIdAndScimId(ORG, scimId).orElseThrow();
    }
}
