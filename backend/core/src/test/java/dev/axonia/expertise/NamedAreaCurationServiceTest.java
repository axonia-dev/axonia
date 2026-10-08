package dev.axonia.expertise;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.expertise.fake.FakeNamedAreaRepository;
import dev.axonia.expertise.fake.FakeTagAliasRepository;
import dev.axonia.expertise.internal.CurationScope;
import dev.axonia.expertise.internal.DeclarationRepository;
import dev.axonia.expertise.internal.ExpertiseQueryRepository;
import dev.axonia.expertise.internal.VocabularyLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class NamedAreaCurationServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "eng";
    private static final String OTHER_SLUG = "data";
    private static final long COMMUNITY_ID = 1L;
    private static final long OTHER_COMMUNITY_ID = 2L;
    private static final String LEADER = "alice";
    private static final String NON_LEADER = "bob";
    private static final long UNKNOWN_ID = 999L;

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private ExpertiseQueryRepository expertiseQueryRepository;
    @Mock
    private DeclarationRepository declarationRepository;
    @Mock
    private VocabularyLock vocabularyLock;

    private FakeTagAliasRepository tagAliasRepository;
    private FakeNamedAreaRepository namedAreaRepository;

    private NamedAreaCurationService sut;

    @BeforeEach
    void setUp() {

        tagAliasRepository = new FakeTagAliasRepository();
        namedAreaRepository = new FakeNamedAreaRepository();
        var curationScope = new CurationScope(currentUser, communityAccess, expertiseQueryRepository,
                declarationRepository, tagAliasRepository, List.of());
        sut = new NamedAreaCurationService(organizationContext, curationScope, tagAliasRepository, namedAreaRepository,
                vocabularyLock);

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(COMMUNITY_ID);
        when(communityAccess.communityIdForSlug(OTHER_SLUG)).thenReturn(OTHER_COMMUNITY_ID);
        when(currentUser.userId()).thenReturn(LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, LEADER)).thenReturn(true);
        when(communityAccess.canCurateTags(OTHER_COMMUNITY_ID, LEADER)).thenReturn(true);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring", "kafka"));
    }

    // -----------------------------------------------------------------------
    // the curation gate, one method at a time
    // -----------------------------------------------------------------------

    @Test
    void namedAreas_refusedWithoutTheCurationGrant() {

        givenNoCurationGrant();

        assertThatThrownBy(() -> sut.namedAreas(SLUG)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createNamedArea_refusedWithoutTheCurationGrant() {

        givenNoCurationGrant();

        assertThatThrownBy(() -> sut.createNamedArea(SLUG, "Messaging")).isInstanceOf(ForbiddenException.class);
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void renameNamedArea_refusedWithoutTheCurationGrant() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        givenNoCurationGrant();

        assertThatThrownBy(() -> sut.renameNamedArea(SLUG, id, "Streaming")).isInstanceOf(ForbiddenException.class);
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).extracting(NamedArea::name)
                .containsExactly("Messaging");
    }

    @Test
    void deleteNamedArea_refusedWithoutTheCurationGrant() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        givenNoCurationGrant();

        assertThatThrownBy(() -> sut.deleteNamedArea(SLUG, id)).isInstanceOf(ForbiddenException.class);
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).hasSize(1);
    }

    @Test
    void placeInNamedArea_refusedWithoutTheCurationGrant() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        givenNoCurationGrant();

        assertThatThrownBy(() -> sut.placeInNamedArea(SLUG, id, "kafka")).isInstanceOf(ForbiddenException.class);
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).isEmpty();
    }

    @Test
    void removeFromNamedArea_refusedWithoutTheCurationGrant() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        namedAreaRepository.place(ORG, COMMUNITY_ID, id, "kafka");
        givenNoCurationGrant();

        assertThatThrownBy(() -> sut.removeFromNamedArea(SLUG, "kafka")).isInstanceOf(ForbiddenException.class);
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).containsExactly("kafka");
    }

    // -----------------------------------------------------------------------
    // reading and naming
    // -----------------------------------------------------------------------

    @Test
    void namedAreasAreTheCommunitysOwn() {

        namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        namedAreaRepository.create(ORG, OTHER_COMMUNITY_ID, "Pipelines");

        assertThat(sut.namedAreas(SLUG)).extracting(NamedArea::name).containsExactly("Messaging");
    }

    @Test
    void creatingANamedAreaAnswersItTrimmedAndEmpty() {

        var created = sut.createNamedArea(SLUG, "  Messaging  ");

        assertThat(created).isEqualTo(new NamedArea(created.id(), "Messaging", List.of()));
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).containsExactly(created);
    }

    @Test
    void aNameOfEightyCharactersIsAccepted() {

        var name = "a".repeat(80);

        assertThat(sut.createNamedArea(SLUG, name).name()).isEqualTo(name);
    }

    @Test
    void aBlankNameIsRejected() {

        assertThatThrownBy(() -> sut.createNamedArea(SLUG, "   ")).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "NAMED_AREA_NAME_BLANK");
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void aNameLongerThanTheColumnIsRejected() {

        assertThatThrownBy(() -> sut.createNamedArea(SLUG, "a".repeat(81))).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "NAMED_AREA_NAME_TOO_LONG");
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void renamingANamedAreaTrimsTheNewName() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");

        sut.renameNamedArea(SLUG, id, " Streaming ");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).extracting(NamedArea::name)
                .containsExactly("Streaming");
    }

    @Test
    void renamingANamedAreaToABlankNameIsRejected() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");

        assertThatThrownBy(() -> sut.renameNamedArea(SLUG, id, "")).isInstanceOf(ValidationException.class);
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).extracting(NamedArea::name)
                .containsExactly("Messaging");
    }

    @Test
    void renamingAnUnknownNamedAreaIsNotFound() {

        assertThatThrownBy(() -> sut.renameNamedArea(SLUG, UNKNOWN_ID, "Streaming"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void renamingAnotherCommunitysNamedAreaIsNotFound() {

        var foreign = namedAreaRepository.create(ORG, OTHER_COMMUNITY_ID, "Pipelines");

        assertThatThrownBy(() -> sut.renameNamedArea(SLUG, foreign, "Streaming")).isInstanceOf(NotFoundException.class);
        assertThat(namedAreaRepository.findAll(ORG, OTHER_COMMUNITY_ID)).extracting(NamedArea::name)
                .containsExactly("Pipelines");
    }

    @Test
    void deletingANamedAreaRemovesIt() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");

        sut.deleteNamedArea(SLUG, id);

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void deletingAnUnknownNamedAreaIsNotFound() {

        assertThatThrownBy(() -> sut.deleteNamedArea(SLUG, UNKNOWN_ID)).isInstanceOf(NotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // placing and removing tags
    // -----------------------------------------------------------------------

    @Test
    void aPlacedTagIsStoredUnderItsCanonical() {

        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "spring-boot", "spring");
        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Backend");

        sut.placeInNamedArea(SLUG, id, "Spring Boot");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).containsExactly("spring");
    }

    @Test
    void aTagOnlyADeclarationCarriesCanBePlaced() {

        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));
        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Platform");

        sut.placeInNamedArea(SLUG, id, "helm");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).containsExactly("helm");
    }

    @Test
    void aTagOutsideTheCommunityVocabularyCannotBePlaced() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");

        assertThatThrownBy(() -> sut.placeInNamedArea(SLUG, id, "rabbitmq")).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "TAG_NOT_IN_COMMUNITY");
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).isEmpty();
    }

    @Test
    void placingIntoAnotherCommunitysNamedAreaIsNotFound() {

        var foreign = namedAreaRepository.create(ORG, OTHER_COMMUNITY_ID, "Pipelines");

        assertThatThrownBy(() -> sut.placeInNamedArea(SLUG, foreign, "kafka")).isInstanceOf(NotFoundException.class);
        assertThat(namedAreaRepository.findAll(ORG, OTHER_COMMUNITY_ID).getFirst().tags()).isEmpty();
    }

    @Test
    void aRemovedTagIsMatchedUnderItsCanonical() {

        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "spring-boot", "spring");
        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Backend");
        namedAreaRepository.place(ORG, COMMUNITY_ID, id, "spring");

        sut.removeFromNamedArea(SLUG, "Spring Boot");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).isEmpty();
    }

    @Test
    void aTagTheCommunityNoLongerCarriesCanStillBeRemoved() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        namedAreaRepository.place(ORG, COMMUNITY_ID, id, "rabbitmq");

        sut.removeFromNamedArea(SLUG, "rabbitmq");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID).getFirst().tags()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // the community lock, taken before the vocabulary is read
    // -----------------------------------------------------------------------

    @Test
    void placingLocksTheCommunityBeforeReadingItsVocabulary() {

        var id = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");

        sut.placeInNamedArea(SLUG, id, "kafka");

        var order = inOrder(vocabularyLock, expertiseQueryRepository);
        order.verify(vocabularyLock).lock(ORG, COMMUNITY_ID);
        order.verify(expertiseQueryRepository).allTags(ORG, COMMUNITY_ID);
    }

    @Test
    void removingLocksTheCommunityBeforeReadingItsMerges() {

        var aliases = spy(tagAliasRepository);
        var curationScope = new CurationScope(currentUser, communityAccess, expertiseQueryRepository,
                declarationRepository, aliases, List.of());
        var locking = new NamedAreaCurationService(organizationContext, curationScope, aliases, namedAreaRepository,
                vocabularyLock);

        locking.removeFromNamedArea(SLUG, "kafka");

        var order = inOrder(vocabularyLock, aliases);
        order.verify(vocabularyLock).lock(ORG, COMMUNITY_ID);
        order.verify(aliases).aliasMap(ORG, COMMUNITY_ID);
    }

    private void givenNoCurationGrant() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);
    }
}
