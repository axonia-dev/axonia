package dev.axonia.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.learning.api.HeldArea;
import dev.axonia.learning.api.Learner;
import dev.axonia.learning.api.LearningPage;
import dev.axonia.learning.api.WantedArea;
import dev.axonia.learning.application.port.out.CommunityAccess;
import dev.axonia.learning.application.port.out.CurrentUser;
import dev.axonia.learning.application.port.out.LearningIntentRepository;
import dev.axonia.learning.application.port.out.OrganizationContext;
import dev.axonia.learning.application.port.out.PersonDirectory;
import dev.axonia.learning.application.port.out.Vocabulary;
import dev.axonia.learning.domain.LearningCommunity;
import dev.axonia.learning.domain.LearningIntent;
import dev.axonia.learning.domain.LearningPerson;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final long COMMUNITY = 7L;
    private static final String ALICE = "alice";
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00.123456Z");
    private static final Instant EARLY = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2026-06-01T00:00:00Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private LearningIntentRepository repository;
    @Mock
    private Vocabulary vocabulary;
    @Mock
    private PersonDirectory personDirectory;

    private final Map<String, String> aliases = new HashMap<>();

    private LearningService sut;

    @BeforeEach
    void setUp() {

        sut = new LearningService(organizationContext, currentUser, communityAccess, repository, vocabulary,
                personDirectory, Clock.fixed(NOW, ZoneOffset.UTC));
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ALICE);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(new LearningCommunity(COMMUNITY, false));
        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(true);
        when(vocabulary.areasOf(COMMUNITY)).thenAnswer(invocation -> new CommunityAreas(aliases));
        when(vocabulary.writableName(anyString())).thenAnswer(invocation -> writableName(invocation.getArgument(0)));
        when(vocabulary.canBeAsked(SLUG)).thenReturn(Map.of());
        when(repository.inCommunity(anyString(), anyLong(), anyString())).thenReturn(List.of());
        when(repository.currentIn(anyString(), anyLong())).thenReturn(List.of());
    }

    @Test
    void addingStoresTheNormalizedTagAtTheClocksMillisecond() {

        var actual = sut.add(SLUG, "Apache Kafka");

        var stored = LearningIntent.builder().communityId(COMMUNITY).userId(ALICE).tag("apache-kafka")
                .declaredAt(Instant.parse("2026-09-25T12:00:00.123Z")).build();
        assertThat(actual).isEqualTo(new HeldArea("apache-kafka", stored.declaredAt()));
        verify(repository).insert(ORG, stored);
    }

    @Test
    void addingTakesTheVocabularyLockThenTheLearnersBeforeReadingMembership() {

        sut.add(SLUG, "kafka");

        var order = inOrder(vocabulary, repository, communityAccess);
        order.verify(vocabulary).shareLock(ORG, COMMUNITY);
        order.verify(repository).lockLearner(ORG, COMMUNITY, ALICE);
        order.verify(communityAccess).isMember(COMMUNITY, ALICE);
        order.verify(repository).inCommunity(ORG, COMMUNITY, ALICE);
        order.verify(repository).insert(any(), any());
    }

    @Test
    void addingAVariantSpellingStoresItAsTypedAndNamesTheArea() {

        aliases.put("k8s", "kubernetes");

        var actual = sut.add(SLUG, "K8s");

        assertThat(actual.tag()).isEqualTo("kubernetes");
        verify(repository).insert(ORG, LearningIntent.builder().communityId(COMMUNITY).userId(ALICE).tag("k8s")
                .declaredAt(Instant.parse("2026-09-25T12:00:00.123Z")).build());
    }

    @Test
    void addingAnotherSpellingOfAHeldAreaReturnsTheFirstIntent() {

        aliases.put("k8s", "kubernetes");
        when(repository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(List.of(intent("k8s", EARLY)));

        assertThat(sut.add(SLUG, "Kubernetes")).isEqualTo(new HeldArea("kubernetes", EARLY));
        verify(repository, never()).insert(any(), any());
    }

    @Test
    void theEleventhAreaIsRefused() {

        when(repository.inCommunity(ORG, COMMUNITY, ALICE))
                .thenReturn(IntStream.range(0, 10).mapToObj(i -> intent("area-" + i, EARLY)).toList());

        assertThatThrownBy(() -> sut.add(SLUG, "kafka")).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "LEARNING_INTENT_LIMIT");
        verify(repository, never()).insert(any(), any());
    }

    @Test
    void anAreaAlreadyHeldIsReturnedEvenAtTheCap() {

        when(repository.inCommunity(ORG, COMMUNITY, ALICE))
                .thenReturn(IntStream.range(0, 10).mapToObj(i -> intent("area-" + i, EARLY)).toList());

        assertThat(sut.add(SLUG, "Area 3")).isEqualTo(new HeldArea("area-3", EARLY));
    }

    @Test
    void twoSpellingsOfOneAreaCountOnceTowardsTheCap() {

        aliases.put("k8s", "kubernetes");
        var eight = IntStream.range(0, 8).mapToObj(i -> intent("area-" + i, EARLY));
        when(repository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(
                Stream.concat(eight, Stream.of(intent("k8s", EARLY), intent("kubernetes", EARLY))).toList());

        assertThat(sut.add(SLUG, "kafka").tag()).isEqualTo("kafka");
    }

    @Test
    void anArchivedCommunityTakesNoNewIntent() {

        when(communityAccess.communityForSlug(SLUG)).thenReturn(new LearningCommunity(COMMUNITY, true));

        assertThatThrownBy(() -> sut.add(SLUG, "kafka")).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_ARCHIVED");
        verify(repository, never()).insert(any(), any());
    }

    @Test
    void aNonMemberCannotAdd() {

        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        assertThatThrownBy(() -> sut.add(SLUG, "kafka")).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).insert(any(), any());
    }

    @Test
    void aBlankTagIsRefusedAndNothingIsStored() {

        assertThatThrownBy(() -> sut.add(SLUG, " -- ")).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "TAG_BLANK");
        verify(repository, never()).insert(any(), any());
    }

    @Test
    void removingDeletesEverySpellingOfTheAreaAndAnswersTheArea() {

        aliases.put("k8s", "kubernetes");

        var removed = sut.remove(SLUG, "K8s");

        verify(repository).delete(ORG, COMMUNITY, ALICE, Set.of("kubernetes", "k8s"));
        assertThat(removed).isEqualTo("kubernetes");
    }

    @Test
    void removingTakesTheSameLocksAsAddingBeforeDeleting() {

        sut.remove(SLUG, "kafka");

        var order = inOrder(vocabulary, repository, communityAccess);
        order.verify(vocabulary).shareLock(ORG, COMMUNITY);
        order.verify(repository).lockLearner(ORG, COMMUNITY, ALICE);
        order.verify(communityAccess).isMember(COMMUNITY, ALICE);
        order.verify(repository).delete(ORG, COMMUNITY, ALICE, Set.of("kafka"));
    }

    @Test
    void removingStillWorksInAnArchivedCommunity() {

        when(communityAccess.communityForSlug(SLUG)).thenReturn(new LearningCommunity(COMMUNITY, true));

        sut.remove(SLUG, "kafka");

        verify(repository).delete(ORG, COMMUNITY, ALICE, Set.of("kafka"));
    }

    @Test
    void aNonMemberCannotRemove() {

        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        assertThatThrownBy(() -> sut.remove(SLUG, "kafka")).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).delete(any(), anyLong(), any(), any());
    }

    @Test
    void aNonMembersPageHasNoAreasOfTheirOwnButReadsTheWantedList() {

        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);
        when(repository.currentIn(ORG, COMMUNITY)).thenReturn(List.of(intentOf("bob", "kafka", EARLY)));
        when(vocabulary.canBeAsked(SLUG)).thenReturn(Map.of("kafka", 2));

        assertThat(sut.page(SLUG))
                .isEqualTo(new LearningPage(false, false, 10, List.of(), List.of(new WantedArea("kafka", 1, 2))));
        verify(repository, never()).inCommunity(any(), anyLong(), any());
    }

    @Test
    void aMembersPageListsTheirAreas() {

        when(repository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(List.of(intent("kafka", EARLY)));
        when(repository.currentIn(ORG, COMMUNITY)).thenReturn(List.of(intentOf(ALICE, "kafka", EARLY)));

        var page = sut.page(SLUG);

        assertThat(page.member()).isTrue();
        assertThat(page.mine()).containsExactly(new HeldArea("kafka", EARLY));
        assertThat(page.wanted()).containsExactly(new WantedArea("kafka", 1, 0));
    }

    @Test
    void anArchivedCommunitysPageSaysSoAndStillReads() {

        when(communityAccess.communityForSlug(SLUG)).thenReturn(new LearningCommunity(COMMUNITY, true));
        when(repository.inCommunity(ORG, COMMUNITY, ALICE)).thenReturn(List.of(intent("kafka", EARLY)));

        var page = sut.page(SLUG);

        assertThat(page.archived()).isTrue();
        assertThat(page.mine()).containsExactly(new HeldArea("kafka", EARLY));
    }

    @Test
    void learnersOfAVariantAnswerForTheCanonicalArea() {

        aliases.put("k8s", "kubernetes");
        when(repository.currentIn(ORG, COMMUNITY))
                .thenReturn(List.of(intentOf("bob", "kubernetes", LATE), intentOf(ALICE, "k8s", EARLY)));
        when(personDirectory.resolve(List.of(ALICE, "bob"))).thenReturn(Map.of(ALICE,
                new LearningPerson(ALICE, "Alice", null), "bob", new LearningPerson("bob", "Bob", "https://x/b.png")));

        assertThat(sut.learners(SLUG, "K8S")).containsExactly(new Learner(ALICE, "Alice", null, EARLY),
                new Learner("bob", "Bob", "https://x/b.png", LATE));
    }

    @Test
    void anAreaNobodyWantsHasNoLearners() {

        when(repository.currentIn(ORG, COMMUNITY)).thenReturn(List.of(intentOf("bob", "kafka", EARLY)));

        assertThat(sut.learners(SLUG, "rust")).isEmpty();
    }

    @Test
    void forgettingADepartedLearnerDeletesTheirIntentsThereUnderTheLocksAnAddTakes() {

        sut.forgetDepartedLearner("acme", COMMUNITY, ALICE);

        var order = inOrder(vocabulary, repository);
        order.verify(vocabulary).shareLock("acme", COMMUNITY);
        order.verify(repository).lockLearner("acme", COMMUNITY, ALICE);
        order.verify(repository).deleteAllIn("acme", COMMUNITY, ALICE);
    }

    private static LearningIntent intent(String tag, Instant at) {

        return intentOf(ALICE, tag, at);
    }

    private static LearningIntent intentOf(String userId, String tag, Instant at) {

        return LearningIntent.builder().communityId(COMMUNITY).userId(userId).tag(tag).declaredAt(at).build();
    }

    private static String writableName(String raw) {

        var name = TagNormalizer.normalize(raw);
        if (name.isBlank()) {
            throw new ValidationException("TAG_BLANK", "A tag needs a name");
        }
        return name;
    }
}
