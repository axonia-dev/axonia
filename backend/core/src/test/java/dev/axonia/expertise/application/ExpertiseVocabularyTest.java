package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.community.api.spi.FakeCommunityTagWriter;
import dev.axonia.expertise.api.TagSnapshot;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.FakeTagAliasRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.VocabularyLock;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ExpertiseVocabularyTest {

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private VocabularyLock vocabularyLock;
    @Mock
    private ExpertiseService expertiseService;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private ExpertiseQueryRepository expertiseQueryRepository;
    @Mock
    private DeclarationRepository declarationRepository;

    private FakeTagAliasRepository aliases;
    private FakeCommunityTagWriter writer;

    private ExpertiseVocabulary sut;

    @BeforeEach
    void setUp() {

        aliases = new FakeTagAliasRepository();
        writer = new FakeCommunityTagWriter();
        var curationScope = new CurationScope(currentUser, communityAccess, expertiseQueryRepository,
                declarationRepository, aliases, List.of(writer));
        sut = new ExpertiseVocabulary(organizationContext, aliases, vocabularyLock, expertiseService, curationScope);
        when(organizationContext.organizationId()).thenReturn("org-1");
    }

    @Test
    void sharingTheLockTakesTheCurationLockForTheGivenOrganization() {

        sut.shareLock("org-2", 7L);

        verify(vocabularyLock).share("org-2", 7L);
    }

    @Test
    void theAreasOfACommunityReadThroughItsMerges() {

        aliases.addAlias("org-1", 7L, "k8s", "kubernetes");

        assertThat(sut.areasOf(7L).canonicalOf("k8s")).isEqualTo("kubernetes");
    }

    @Test
    void whoCanBeAskedIsTheAreasListsCountPerCanonical() {

        when(expertiseService.areas("platform", null)).thenReturn(List
                .of(new ExpertiseArea("kafka", List.of("kafka"), 3), new ExpertiseArea("helm", List.of("helm"), 1)));

        assertThat(sut.canBeAsked("platform")).containsExactlyInAnyOrderEntriesOf(Map.of("kafka", 3, "helm", 1));
    }

    @Test
    void aWritableNameIsNormalized() {

        assertThat(sut.writableName("Apache Kafka")).isEqualTo("apache-kafka");
    }

    @Test
    void aBlankNameIsRefused() {

        assertThatThrownBy(() -> sut.writableName(" !! ")).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "TAG_BLANK");
    }

    @Test
    void theVisibleTagsAreContentAndUnhiddenDeclarations_foldedSortedAndOnce() {

        givenMember(true);
        writer.carry(7L, "kafka");
        writer.carry(7L, "k8s");
        aliases.addAlias("org-1", 7L, "k8s", "kubernetes");
        when(declarationRepository.allVisibleIn("org-1", 7L)).thenReturn(List.of(
                new Declaration(7L, "u-1", "helm", Instant.EPOCH), new Declaration(7L, "u-2", "kafka", Instant.EPOCH)));
        when(expertiseQueryRepository.allTags("org-1", 7L)).thenReturn(List.of("hidden-signal"));
        when(declarationRepository.tagsIn("org-1", 7L)).thenReturn(List.of("hidden-declaration"));

        assertThat(sut.visibleTags("platform")).containsExactly("helm", "kafka", "kubernetes");
    }

    @Test
    void theVisibleTagsAreRefusedToANonMember() {

        givenMember(false);

        assertThatThrownBy(() -> sut.visibleTags("platform")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aSnapshotKnowsTheVisibleTagsAndTheMerges_neverAHiddenDeclaration() {

        writer.carry(7L, "kubernetes");
        aliases.addAlias("org-1", 7L, "k8s", "kubernetes");
        when(declarationRepository.tagsIn("org-1", 7L)).thenReturn(List.of("burnout"));

        var reading = sut.snapshot(7L).read(List.of("k8s", "kubernetes", "burnout", "terraform"));

        assertThat(reading.merged()).containsExactly(new TagSnapshot.TagReading.Merged("k8s", "kubernetes"));
        assertThat(reading.created()).as("a hidden declaration reads as new, so the report cannot reveal it")
                .containsExactly("burnout", "terraform");
    }

    private void givenMember(boolean member) {

        when(communityAccess.communityIdForSlug("platform")).thenReturn(7L);
        when(currentUser.userId()).thenReturn("u-9");
        when(communityAccess.isMember(7L, "u-9")).thenReturn(member);
    }
}
