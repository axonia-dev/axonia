package dev.axonia.expertise;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.ValidationException;
import dev.axonia.expertise.fake.FakeTagAliasRepository;
import dev.axonia.expertise.internal.VocabularyLock;

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

    private FakeTagAliasRepository aliases;

    private ExpertiseVocabulary sut;

    @BeforeEach
    void setUp() {

        aliases = new FakeTagAliasRepository();
        sut = new ExpertiseVocabulary(organizationContext, aliases, vocabularyLock, expertiseService);
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
}
