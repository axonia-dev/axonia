package dev.axonia.expertise;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.ValidationException;
import dev.axonia.expertise.fake.FakeTagAliasRepository;
import dev.axonia.expertise.internal.DeclarationRepository;
import dev.axonia.expertise.internal.VocabularyLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpertiseDeclarationServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final long COMMUNITY = 7L;
    private static final String ALICE = "alice";
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private DeclarationRepository declarationRepository;
    @Mock
    private VocabularyLock vocabularyLock;

    private FakeTagAliasRepository tagAliasRepository;

    private ExpertiseDeclarationService sut;

    @BeforeEach
    void setUp() {

        tagAliasRepository = new FakeTagAliasRepository();
        sut = new ExpertiseDeclarationService(organizationContext, currentUser, communityAccess, declarationRepository,
                tagAliasRepository, Clock.fixed(NOW, ZoneOffset.UTC), vocabularyLock);
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ALICE);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(COMMUNITY);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(new CommunityRef(COMMUNITY, false));
        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(true);
        when(declarationRepository.inCommunity(anyString(), any(Long.class), anyString())).thenReturn(List.of());
    }

    @Test
    void declaringStoresTheNormalizedTagForTheCallerAtTheClocksInstant() {

        var actual = sut.declare(SLUG, "Apache Kafka");

        var expected = Declaration.builder().communityId(COMMUNITY).userId(ALICE).tag("apache-kafka").declaredAt(NOW)
                .build();
        assertThat(actual).isEqualTo(expected);
        verify(declarationRepository).insert(ORG, expected);
    }

    @Test
    void declaringLocksTheDeclarerBeforeReadingTheirMembershipAndDeclarations() {

        sut.declare(SLUG, "kafka");

        var inOrder = inOrder(vocabularyLock, declarationRepository, communityAccess);
        inOrder.verify(vocabularyLock).share(ORG, COMMUNITY);
        inOrder.verify(declarationRepository).lockDeclarer(ORG, COMMUNITY, ALICE);
        inOrder.verify(communityAccess).isMember(COMMUNITY, ALICE);
        inOrder.verify(declarationRepository).inCommunity(ORG, COMMUNITY, ALICE);
        inOrder.verify(declarationRepository).insert(any(), any());
    }

    @Test
    void declaringTheSameAreaAgainReturnsTheFirstDeclaration() {

        var first = Declaration.builder().communityId(COMMUNITY).userId(ALICE).tag("kafka")
                .declaredAt(Instant.parse("2026-09-01T12:00:00Z")).build();
        when(declarationRepository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(List.of(first));

        assertThat(sut.declare(SLUG, "kafka")).isEqualTo(first);
        verify(declarationRepository, never()).insert(any(), any());
    }

    @Test
    void declaringAnotherSpellingOfADeclaredAreaReturnsTheFirstDeclaration() {

        tagAliasRepository.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        var first = Declaration.builder().communityId(COMMUNITY).userId(ALICE).tag("k8s")
                .declaredAt(Instant.parse("2026-09-01T12:00:00Z")).build();
        when(declarationRepository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(List.of(first));

        assertThat(sut.declare(SLUG, "kubernetes")).isEqualTo(Declaration.builder().communityId(COMMUNITY).userId(ALICE)
                .tag("kubernetes").declaredAt(Instant.parse("2026-09-01T12:00:00Z")).build());
        verify(declarationRepository, never()).insert(any(), any());
    }

    @Test
    void declaringAVariantSpellingStoresItAsTypedAndNamesTheArea() {

        tagAliasRepository.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");

        var actual = sut.declare(SLUG, "k8s");

        assertThat(actual.tag()).isEqualTo("kubernetes");
        verify(declarationRepository).insert(ORG,
                Declaration.builder().communityId(COMMUNITY).userId(ALICE).tag("k8s").declaredAt(NOW).build());
    }

    @Test
    void twoSpellingsOfOneAreaCountOnceTowardsTheCap() {

        tagAliasRepository.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        var declared = Stream
                .concat(Stream.of(declaration("k8s"), declaration("kubernetes")), declarations(18).stream()).toList();
        when(declarationRepository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(declared);

        sut.declare(SLUG, "kafka");

        verify(declarationRepository).insert(any(), any());
    }

    @Test
    void theTwentyFirstDeclarationIsRefused() {

        when(declarationRepository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(declarations(20));

        assertThatThrownBy(() -> sut.declare(SLUG, "kafka")).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("DECLARATION_LIMIT");
        verify(declarationRepository, never()).insert(any(), any());
    }

    @Test
    void theTwentiethDeclarationIsAccepted() {

        when(declarationRepository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(declarations(19));

        sut.declare(SLUG, "kafka");

        verify(declarationRepository).insert(any(), any());
    }

    @Test
    void aNonMemberCannotDeclare() {

        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        assertThatThrownBy(() -> sut.declare(SLUG, "kafka")).isInstanceOf(ForbiddenException.class);
        verify(declarationRepository, never()).insert(any(), any());
    }

    @Test
    void anArchivedCommunityTakesNoNewDeclaration() {

        when(communityAccess.communityForSlug(SLUG)).thenReturn(new CommunityRef(COMMUNITY, true));

        assertThatThrownBy(() -> sut.declare(SLUG, "kafka")).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("COMMUNITY_ARCHIVED");
        verify(declarationRepository, never()).lockDeclarer(any(), any(Long.class), any());
        verify(declarationRepository, never()).insert(any(), any());
    }

    @Test
    void aBlankTagIsRefused() {

        assertThatThrownBy(() -> sut.declare(SLUG, " !! ")).isInstanceOf(ValidationException.class).extracting("code")
                .isEqualTo("TAG_BLANK");
    }

    @Test
    void removingDeletesTheCallersOwnNormalizedDeclaration() {

        sut.remove(SLUG, "Apache Kafka");

        verify(declarationRepository).delete(ORG, COMMUNITY, ALICE, Set.of("apache-kafka"));
    }

    @Test
    void removingAMergedAreaDeletesEverySpellingItWasDeclaredUnder() {

        tagAliasRepository.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        tagAliasRepository.addAlias(ORG, COMMUNITY, "kube", "kubernetes");

        sut.remove(SLUG, "Kubernetes");

        verify(declarationRepository).delete(ORG, COMMUNITY, ALICE, Set.of("kubernetes", "k8s", "kube"));
    }

    @Test
    void removingUnderAVariantSpellingResolvesToTheWholeArea() {

        tagAliasRepository.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");

        sut.remove(SLUG, "k8s");

        verify(declarationRepository).delete(ORG, COMMUNITY, ALICE, Set.of("kubernetes", "k8s"));
    }

    @Test
    void removingLocksTheDeclarerBeforeReadingTheirMembership() {

        sut.remove(SLUG, "kafka");

        var inOrder = inOrder(vocabularyLock, declarationRepository, communityAccess);
        inOrder.verify(vocabularyLock).share(ORG, COMMUNITY);
        inOrder.verify(declarationRepository).lockDeclarer(ORG, COMMUNITY, ALICE);
        inOrder.verify(communityAccess).isMember(COMMUNITY, ALICE);
        inOrder.verify(declarationRepository).delete(ORG, COMMUNITY, ALICE, Set.of("kafka"));
    }

    @Test
    void aNonMemberCannotRemove() {

        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        assertThatThrownBy(() -> sut.remove(SLUG, "kafka")).isInstanceOf(ForbiddenException.class);
        verify(declarationRepository, never()).delete(any(), any(Long.class), any(), any());
    }

    private static List<Declaration> declarations(int count) {

        return IntStream.range(0, count).mapToObj(i -> declaration("area-" + i)).toList();
    }

    private static Declaration declaration(String tag) {

        return Declaration.builder().communityId(COMMUNITY).userId(ALICE).tag(tag).declaredAt(NOW).build();
    }
}
