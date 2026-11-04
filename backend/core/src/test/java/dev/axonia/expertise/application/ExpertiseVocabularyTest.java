package dev.axonia.expertise.application;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.expertise.api.TagReading;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.FakeTagAliasRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.VocabularyLock;
import dev.axonia.expertise.domain.ExpertiseArea;
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

    private ExpertiseVocabulary sut;

    @BeforeEach
    void setUp() {

        aliases = new FakeTagAliasRepository();
        var curationScope = new CurationScope(currentUser, communityAccess, expertiseQueryRepository,
                declarationRepository, aliases, List.of());
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
    void readingTagsNamesThoseTheCommunityNeverCarried() {

        when(expertiseQueryRepository.allTags("org-1", 7L)).thenReturn(List.of("kafka"));

        var reading = sut.read(7L, List.of("Kafka", "Azure PIM", "azure-pim"));

        assertThat(reading).isEqualTo(new TagReading(List.of("azure-pim"), List.of()));
    }

    @Test
    void readingTagsNamesThoseMergedIntoAnother() {

        when(expertiseQueryRepository.allTags("org-1", 7L)).thenReturn(List.of("kubernetes"));
        aliases.addAlias("org-1", 7L, "k8s", "kubernetes");
        aliases.addAlias("org-1", 7L, "kube", "kubernetes");

        var reading = sut.read(7L, List.of("K8s", "kube", "kubernetes"));

        assertThat(reading).isEqualTo(new TagReading(List.of(),
                List.of(new TagReading.Merged("k8s", "kubernetes"), new TagReading.Merged("kube", "kubernetes"))));
    }

    @Test
    void readingTagsIgnoresBlankOnes() {

        assertThat(sut.read(7L, List.of(" !! ", ""))).isEqualTo(new TagReading(List.of(), List.of()));
    }
}
