package dev.axonia.roundup.domain;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.HeldTalk;
import dev.axonia.roundup.api.spi.SessionActivity.UpcomingSession;
import dev.axonia.roundup.api.spi.TopicActivity.TagCount;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.shared.api.colour.GuildTints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class RoundupFactsTest {

    private static final Quarter Q2 = new Quarter(2026, 2);
    private static final Instant APRIL = Instant.parse("2026-04-14T17:00:00Z");
    private static final Instant MAY = Instant.parse("2026-05-13T17:00:00Z");
    private static final Instant JUNE = Instant.parse("2026-06-09T17:00:00Z");
    private static final Instant AUGUST = Instant.parse("2026-08-02T17:00:00Z");
    private static final Instant SEPTEMBER = Instant.parse("2026-09-06T17:00:00Z");
    private static final int COMMUNITY_CAP = 8;

    /**
     * The seam itself, at every depth. The two templates read these names and no others, and a fact map frozen into the
     * outbox as jsonb comes back type-erased, so a key renamed on one side and not the other renders the literal word
     * "null" into an organization-wide mail rather than failing anywhere. Pinning the whole set rather than the keys
     * somebody thought to assert is also what makes an ADDED fact fail here - the direction
     * {@code RoundupTemplateFactsTest}, which reads the templates, cannot see.
     */
    @Test
    void theLetterIsBuiltFromTheseNamesAndNoOthers() {

        var facts = RoundupFacts
                .of(aQuarter().captured(List.of(new CapturedArtifact(41L, "Deck", "https://deck", "Slides"))).build());

        var card = communityCards(facts).getFirst();
        assertThat(facts).containsOnlyKeys("sender", "sessionTitle", "periodLabel", "quarterLabel", "yearLabel",
                "sessionCount", "talkCount", "presenterCount", "resourceCount", "firstTimeContributorCount", "topics",
                "communities", "moreCommunitiesCount", "openTalkCount", "communitiesUrl");
        assertThat(sender(facts)).containsOnlyKeys("name", "initials", "tintBackground", "tintForeground");
        assertThat(topicsOf(facts).getFirst()).containsOnlyKeys("label", "weight");
        assertThat(card).containsOnlyKeys("name", "initials", "badgeUrl", "tints", "presenters", "morePresenterCount",
                "sessionCountLabel", "sessions", "moreSessionsLabel", "nextTitle", "nextLabel", "nextMonth", "nextDay");
        assertThat(presentersOf(card).getFirst()).containsOnlyKeys("name", "initials", "tint");
        assertThat(sessionsOf(card).getFirst()).containsOnlyKeys("dateLabel", "title", "durationLabel", "recordingUrl",
                "sessionUrl", "thumbnailUrl", "talks", "moreTalkCount", "goingCount", "resourceGroups");
        assertThat(talksOf(sessionsOf(card).getLast()).getFirst()).containsOnlyKeys("title", "presenter",
                "morePresenterCount", "tag");
        assertThat(groupsOf(sessionsOf(card).getLast())).isNotEmpty()
                .allSatisfy(group -> assertThat(group).containsOnlyKeys("label", "count"));
    }

    @Test
    void aResourceCountsOnceHoweverManyOfTheQuartersSessionsItIsAttachedTo() {

        var facts = RoundupFacts
                .of(aQuarter().captured(List.of(new CapturedArtifact(41L, "Recording", "https://r", "Recording"),
                        new CapturedArtifact(42L, "Recording", "https://r", "Recording"))).build());

        assertThat(facts).containsEntry("resourceCount", 1);
    }

    @Test
    void aPersonKeepsOneColourWhereverTheyAppearInTheLetter() {

        var facts = RoundupFacts.of(aQuarter().build());

        var tints = communityCards(facts).stream().flatMap(card -> presentersOf(card).stream())
                .collect(Collectors.groupingBy(presenter -> presenter.get("name"),
                        Collectors.mapping(presenter -> presenter.get("tint"), Collectors.toSet())));
        assertThat(tints).as("the fixture must put one person in two cards, or this proves nothing")
                .containsKey("Alice Dubois");
        assertThat(tints.values()).allSatisfy(colours -> assertThat(colours).hasSize(1));
    }

    @Test
    void aCommunityCardCountsTheSessionsItHeldEvenWhereItOnlyListsSomeOfThem() {

        var facts = RoundupFacts.of(aQuarter().sessionsPerCommunityCap(1).build());

        var card = communityCards(facts).getFirst();
        assertThat(sessionsOf(card)).hasSize(1);
        assertThat(card).containsEntry("sessionCountLabel", "2 sessions").containsEntry("moreSessionsLabel",
                "+ Hexagons in anger \u00b7 13 May");
    }

    @Test
    void aQuarterWithNoTagsCarriesNoTopicsRatherThanAnEmptyCloudOfOneWord() {

        var facts = RoundupFacts.of(aQuarter().topics(List.of()).build());

        assertThat(facts.get("topics")).asInstanceOf(LIST).isEmpty();
    }

    @Test
    void topicWeightsSpanTheScaleAndCollapseToTheMiddleWhenEveryTagTiedOnce() {

        var spread = RoundupFacts.of(aQuarter()
                .topics(List.of(new TagCount("kafka", 9), new TagCount("spring", 5), new TagCount("testing", 1)))
                .build());
        var tied = RoundupFacts
                .of(aQuarter().topics(List.of(new TagCount("kafka", 1), new TagCount("spring", 1))).build());

        assertThat(topicWeights(spread)).containsExactly(5, 3, 1);
        assertThat(topicWeights(tied)).containsExactly(3, 3);
    }

    /**
     * The bands switch where the Expertise page's {@code TagConstellation} switches - at 0.8, 0.6, 0.4 and 0.2 - so a
     * tag reads the same size and colour in the letter as it does in the app. Counts 1 to 11 put a tag exactly on each
     * edge: the earlier {@code 1 + round(ratio * 4)} rule switched at 0.875, 0.625, 0.375 and 0.125 and would answer 5,
     * 4, 3, 3, 2, 1 here, sizing the 0.8 tag as ink at 22px where the app makes it the largest pink word on the page.
     */
    @Test
    void aTagSitsInTheBandTheAppWouldGiveItRatherThanARoundingOfItsRatio() {

        var facts = RoundupFacts.of(aQuarter().topics(
                List.of(new TagCount("peak", 11), new TagCount("four-fifths", 9), new TagCount("three-fifths", 7),
                        new TagCount("two-fifths", 5), new TagCount("one-fifth", 3), new TagCount("floor", 1)))
                .build());

        assertThat(topicWeights(facts)).containsExactly(5, 5, 4, 3, 2, 1);
    }

    /**
     * The cap and the scale are one decision, not two. Weights are taken over the tags SHOWN, so a cap that dropped one
     * tag too few or too many silently re-sizes the whole cloud: with the fourth tag left in, the range would run from
     * 1 rather than from 5 and every surviving weight would move. Pinning the labels alone would miss that, and pinning
     * the weights alone would pass with no cap at all.
     */
    @Test
    void capsTheTopicCloudAndScalesTheTagsThatSurvivedRatherThanTheOnesItDropped() {

        var facts = RoundupFacts.of(aQuarter().topicCap(3).topics(List.of(new TagCount("kafka", 9),
                new TagCount("spring", 7), new TagCount("testing", 5), new TagCount("hexagonal", 1))).build());

        assertThat(topicLabels(facts)).containsExactly("kafka", "spring", "testing");
        assertThat(topicWeights(facts)).containsExactly(5, 3, 1);
    }

    @Test
    void theFloorIsNowJustWhetherTheLetterCanNameASession() {

        assertThat(RoundupFacts.isBelowActivityFloor(RoundupFacts.of(aQuarter().held(List.of()).build()))).isTrue();
        assertThat(RoundupFacts.isBelowActivityFloor(RoundupFacts.of(aQuarter().build()))).isFalse();
    }

    /**
     * The cloud is the one figure that cannot be filtered row by row to the sessions the letter still names -
     * {@link TagCount} is pre-aggregated and carries no session id - so the only honest arm is all or nothing. A
     * quarter whose every session was held in a community archived since reports no sessions at all, and a cloud of
     * what it was about printed above that sentence is the contradiction the first-timer suppression also exists for.
     */
    @Test
    void aQuarterTheLetterCanNameNoSessionInCarriesNoTopicsEitherHoweverManyTagsThePortAnswered() {

        var facts = RoundupFacts.of(aQuarter().communities(List.of()).build());

        assertThat(facts).containsEntry("sessionCount", 0);
        assertThat(facts.get("topics")).asInstanceOf(LIST).isEmpty();
    }

    @Test
    void namesPresentersInEveryCardAndOnlyCountsFirstTimeContributors() {

        var facts = RoundupFacts.of(aQuarter().firstTimePresenterCount(4).build());

        assertThat(presenterNamesIn(facts)).contains("Alice Dubois", "Bob Martin");
        assertThat(facts).containsEntry("firstTimeContributorCount", 4).containsEntry("presenterCount", 2);
        assertThat(facts.toString()).as("a newcomer is a count, never a name").doesNotContain("firstTimeNames");
    }

    /**
     * The HTML shows initialled badges with no names beside them and the {@code .txt} names everybody in full, so a
     * presenter is carried as both. One list, two renderings, and no way for them to disagree about who presented.
     */
    @Test
    void aPresenterIsCarriedAsBothANameAndAMonogramSoTheTwoRenderingsCannotDisagree() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(presentersOf(communityCards(facts).getFirst())).anySatisfy(presenter -> assertThat(presenter)
                .containsEntry("name", "Alice Dubois").containsEntry("initials", "AD").containsKey("tint"));
    }

    @Test
    void capsACommunitysAvatarsAndSaysHowManyPeopleItLeftOut() {

        var facts = RoundupFacts
                .of(aQuarter()
                        .held(List.of(session(41, 1, "Everybody", MAY, 7, List.of(),
                                IntStream.rangeClosed(1, 7).mapToObj(index -> "Presenter " + index).toList())))
                        .build());

        var card = communityCards(facts).getFirst();
        assertThat(presentersOf(card)).hasSize(5);
        assertThat(card).containsEntry("morePresenterCount", 2);
    }

    @Test
    void capsTheCommunityListAndSaysHowManyItLeftOut() {

        var facts = RoundupFacts.of(twelveActiveCommunities().build());

        assertThat(communityCards(facts)).hasSize(COMMUNITY_CAP);
        assertThat(facts).containsEntry("moreCommunitiesCount", 4);
    }

    @Test
    void aRemainderOfNoneIsZeroRatherThanANegativeNumber() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(communityCards(facts)).hasSize(2);
        assertThat(facts).containsEntry("moreCommunitiesCount", 0);
    }

    @Test
    void communitiesAreOrderedByHowMuchTheyHeldAndThenByName() {

        var facts = RoundupFacts.of(aQuarter()
                .communities(List.of(summary(1, "Zebra Guild"), summary(2, "Backend Guild"), summary(3, "Alpha Guild")))
                .held(List.of(session(41, 1, "One", MAY, 1, List.of(), List.of("Alice Dubois")),
                        session(42, 2, "Two", MAY, 1, List.of(), List.of("Bob Martin")),
                        session(43, 2, "Three", JUNE, 1, List.of(), List.of("Carol Ng")),
                        session(44, 3, "Four", APRIL, 1, List.of(), List.of("Dan Roy")),
                        session(45, 3, "Five", MAY, 1, List.of(), List.of("Dan Roy"))))
                .build());

        assertThat(communityNames(facts)).containsExactly("Alpha Guild", "Backend Guild", "Zebra Guild");
    }

    /**
     * The community-scoped figures, all of which drop a session held in a community nobody can name any more. The two
     * that are deliberately NOT scoped this way have their own test below.
     */
    @Test
    void theCommunityScopedFiguresCountOnlyWhatTheRoundupCanStillName() {

        var facts = RoundupFacts.of(aQuarter().communities(List.of(summary(1, "Backend Guild")))
                .captured(List.of(new CapturedArtifact(41L, "Named", "https://a", "Notes"),
                        new CapturedArtifact(43L, "Archived", "https://b", "Notes")))
                .build());

        assertThat(facts).containsEntry("sessionCount", 2).containsEntry("talkCount", 3)
                .containsEntry("presenterCount", 2).containsEntry("resourceCount", 1);
        assertThat(communityNames(facts)).containsExactly("Backend Guild");
        assertThat(facts.toString()).doesNotContain("Archived");
    }

    /**
     * The fourth headline figure, and it must stay independent of the third: a talk with two presenters is one talk,
     * and one person presenting in two sessions is one person. Deriving either from the other would print the same
     * number twice under two labels.
     */
    @Test
    void theTalkCountIsTheAgendasOwnSizeRatherThanAHeadCountOfPresenters() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(facts).containsEntry("sessionCount", 3).containsEntry("talkCount", 4).containsEntry("presenterCount",
                2);
    }

    /**
     * The reason the first-timer figure is worded as a standalone sentence rather than as "N of them": it comes from an
     * organization-wide port with no community predicate, while presenterCount is scoped to the communities still named
     * and filtered past erased members. This is the exact shape - a community archived, or a presenter erased, between
     * the quarter closing and the mail draining - that makes a subset claim arithmetically false, and the facts must
     * carry it honestly rather than clamp one figure to the other and invent a number.
     */
    @Test
    void theFirstTimerCountIsOrganizationWideAndMayExceedTheScopedPresenterCount() {

        var facts = RoundupFacts
                .of(aQuarter().communities(List.of(summary(2, "Alpha Guild"))).firstTimePresenterCount(5).build());

        assertThat(facts).containsEntry("presenterCount", 1).containsEntry("firstTimeContributorCount", 5);
    }

    /**
     * The activity floor is the letter's own figure, so it can never disagree with what the letter says. This quarter
     * is the one that tells the two readings apart: the session port answers three held sessions, every community that
     * held them is gone, and the assembled letter reports nothing at all - so the floor, asked of the letter, agrees.
     */
    @Test
    void aQuarterTheLetterCanReportNothingAboutIsBelowTheFloorHoweverBusyThePortsWere() {

        var activity = aQuarter().communities(List.of()).firstTimePresenterCount(3).build();

        var facts = RoundupFacts.of(activity);

        assertThat(activity.held()).as("the port itself reports a busy quarter").isNotEmpty();
        assertThat(facts).containsEntry("sessionCount", 0).containsEntry("resourceCount", 0);
        assertThat(RoundupFacts.isBelowActivityFloor(facts)).isTrue();
    }

    @Test
    void anErasedPresenterIsNeverNamedAndIsNeverCounted() {

        var facts = RoundupFacts.of(aQuarter().held(List.of(session(41, 1, "Signals in React", MAY, 1, List.of(),
                List.of("Alice Dubois", DirectoryUser.DELETED_MEMBER_LABEL)))).build());

        assertThat(presenterNamesIn(facts)).containsExactly("Alice Dubois");
        assertThat(facts).containsEntry("presenterCount", 1);
        assertThat(facts.toString()).doesNotContain(DirectoryUser.DELETED_MEMBER_LABEL);
    }

    @Test
    void aPresenterWhoSpokeTwiceIsOnePerson() {

        var facts = RoundupFacts.of(aQuarter().communities(List.of(summary(1, "Backend Guild"))).build());

        assertThat(facts).containsEntry("presenterCount", 2);
        assertThat(presenterNamesIn(facts)).containsExactly("Alice Dubois", "Bob Martin");
    }

    @Test
    void theSenderIsTheOrganizationItselfWithAMonogramOfItsOwn() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(sender(facts)).containsEntry("name", "Acme Corp").containsEntry("initials", "AC");
        assertThat(sender(facts).get("tintBackground")).isNotBlank();
        assertThat(sender(facts).get("tintForeground")).isNotBlank();
    }

    @Test
    void theQuarterIsHeadedByItsKeyAndSubtitledByItsMonths() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(facts).containsEntry("sessionTitle", "Q2 2026").containsEntry("periodLabel", "April to June 2026");
    }

    @Test
    void aCardCountsItsSessionsWithASingularArm() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(communityCards(facts).getFirst()).containsEntry("sessionCountLabel", "2 sessions");
        assertThat(communityCards(facts).getLast()).containsEntry("sessionCountLabel", "1 session");
    }

    @Test
    void aCardCarriesItsCommunitysOwnMarkAndPassesAMissingOneThrough() {

        var facts = RoundupFacts.of(aQuarter()
                .communities(List.of(new NamedCommunity(1L, "Backend Guild", "https://cdn.axonia.example/badges/1.png",
                        "#1f9d63", null, null, null, null), summary(2, "Alpha Guild")))
                .build());

        assertThat(communityCards(facts).getFirst()).containsEntry("name", "Backend Guild")
                .containsEntry("initials", "BG").containsEntry("badgeUrl", "https://cdn.axonia.example/badges/1.png")
                .containsEntry("tints", GuildTints.of("#1f9d63"));
        assertThat(communityCards(facts).getLast()).containsEntry("badgeUrl", null).containsEntry("tints",
                GuildTints.of(null));
    }

    @Test
    void aCardCarriesItsOwnFutureRatherThanAShatteredCalendarSection() {

        var facts = RoundupFacts.of(aQuarter().upcoming(List.of(new UpcomingSession(1L, "Signals in React", AUGUST),
                new UpcomingSession(1L, "Tracing in prod", SEPTEMBER))).build());

        assertThat(communityCards(facts).getFirst()).containsEntry("nextTitle", "Signals in React")
                .containsEntry("nextLabel", "Sunday 2 August").containsEntry("nextMonth", "AUG")
                .containsEntry("nextDay", "2");
        assertThat(facts).as("the flat chronological list is gone, not moved").doesNotContainKey("upcoming");
    }

    @Test
    void aCommunityWithNothingComingCarriesNoNextSession() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(communityCards(facts).getFirst()).containsEntry("nextTitle", null).containsEntry("nextLabel", null)
                .containsEntry("nextMonth", null).containsEntry("nextDay", null)
                .containsEntry("moreSessionsLabel", null);
    }

    @Test
    void aPosterIsADatedTitleItsTalksAndLinksToItsRecordingAndItsPage() {

        var facts = RoundupFacts.of(aQuarter().thumbnailUrls(Map.of(41L, "https://cdn.axonia.test/thumb.jpg"))
                .held(List.of(HeldSession.builder().sessionId(41L).communityId(1L).title("Hexagons in anger")
                        .heldAt(MAY).durationMinutes(72).recordingUrl("https://video.test/41").goingCount(31)
                        .talkCount(2)
                        .talks(List.of(new HeldTalk("Ports first", List.of("Alice Dubois", "Bob Martin"), "hexagonal"),
                                new HeldTalk("Signals in React", List.of(), null)))
                        .presenterNames(List.of("Alice Dubois", "Bob Martin")).build()))
                .build());

        var poster = sessionsOf(communityCards(facts).getFirst()).getFirst();
        assertThat(poster).containsEntry("dateLabel", "13 May").containsEntry("title", "Hexagons in anger")
                .containsEntry("durationLabel", "1h 12").containsEntry("recordingUrl", "https://video.test/41")
                .containsEntry("sessionUrl", "https://axonia.test/communities/backend-guild/sessions/41?org=acme")
                .containsEntry("thumbnailUrl", "https://cdn.axonia.test/thumb.jpg").containsEntry("goingCount", 31)
                .containsEntry("moreTalkCount", 0);
        assertThat(talksOf(poster).getFirst()).containsEntry("title", "Ports first").containsEntry("tag", "hexagonal")
                .containsEntry("morePresenterCount", 1)
                .containsEntry("presenter", Map.of("name", "Alice Dubois", "initials", "AD", "tint",
                        presentersOf(communityCards(facts).getFirst()).getFirst().get("tint")));
        assertThat(talksOf(poster).getLast()).containsEntry("presenter", null).containsEntry("morePresenterCount", 0)
                .containsEntry("tag", null);
    }

    @Test
    void aSessionWithoutARecordingOrAThumbnailCarriesNullsRatherThanEmptyStrings() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(sessionsOf(communityCards(facts).getFirst()).getFirst()).containsEntry("recordingUrl", null)
                .containsEntry("thumbnailUrl", null).containsEntry("durationLabel", null)
                .containsEntry("goingCount", 0);
    }

    @ParameterizedTest
    @CsvSource(value = {"45, 45 min", "60, 1h", "72, 1h 12", "125, 2h 05", "0, NULL",
            "NULL, NULL"}, nullValues = "NULL")
    void aDurationReadsAsMinutesOrAsHoursAndMinutes(Integer minutes, String label) {

        var facts = RoundupFacts.of(aQuarter().held(List.of(HeldSession.builder().sessionId(41L).communityId(1L)
                .title("Hexagons in anger").heldAt(MAY).durationMinutes(minutes).build())).build());

        assertThat(sessionsOf(communityCards(facts).getFirst()).getFirst()).containsEntry("durationLabel", label);
    }

    @Test
    void anUntitledSlotIsCountedButNeverListedAndTheListStopsAtThree() {

        var talks = IntStream.rangeClosed(1, 8).mapToObj(index -> new HeldTalk("Talk " + index, List.of(), null))
                .collect(Collectors.toCollection(ArrayList::new));
        talks.add(new HeldTalk(null, List.of(), null));
        talks.add(new HeldTalk(" ", List.of(), null));
        var facts = RoundupFacts.of(aQuarter().held(List.of(HeldSession.builder().sessionId(41L).communityId(1L)
                .title("Lightning").heldAt(MAY).talkCount(talks.size()).talks(talks).build())).build());

        var poster = sessionsOf(communityCards(facts).getFirst()).getFirst();
        assertThat(talksOf(poster)).extracting(talk -> talk.get("title")).containsExactly("Talk 1", "Talk 2", "Talk 3");
        assertThat(poster).containsEntry("moreTalkCount", 5);
        assertThat(facts).containsEntry("talkCount", 10);
    }

    @Test
    void anErasedCoPresenterIsNeitherTheAvatarNorCounted() {

        var facts = RoundupFacts
                .of(aQuarter()
                        .held(List.of(HeldSession.builder().sessionId(41L).communityId(1L).title("Hexagons").heldAt(MAY)
                                .talkCount(1)
                                .talks(List.of(new HeldTalk("Ports",
                                        List.of(DirectoryUser.DELETED_MEMBER_LABEL, "Bob Martin"), null)))
                                .build()))
                        .build());

        var talk = talksOf(sessionsOf(communityCards(facts).getFirst()).getFirst()).getFirst();
        assertThat(talk).containsEntry("morePresenterCount", 0);
        assertThat(talk.get("presenter")).asInstanceOf(MAP).containsEntry("name", "Bob Martin");
    }

    @Test
    void resourcesAreCountedByKindBusiestFirstAndAnUntypedOneIsALink() {

        var facts = RoundupFacts
                .of(aQuarter().captured(List.of(new CapturedArtifact(41L, "Deck", "https://a", "Slides"),
                        new CapturedArtifact(41L, "Repo", "https://b", "Repo"),
                        new CapturedArtifact(41L, "Other repo", "https://c", "Repo"),
                        new CapturedArtifact(41L, "Bare", "https://d", null),
                        new CapturedArtifact(41L, "Postmortem", "https://e", "Postmortem"))).build());

        assertThat(groupsOf(sessionsOf(communityCards(facts).getFirst()).getLast())).containsExactly(
                Map.of("label", "Repo", "count", 2), Map.of("label", "Link", "count", 1),
                Map.of("label", "Postmortem", "count", 1), Map.of("label", "Slides", "count", 1));
    }

    /** A recording saved with a stray blank or trailing slash is still the recording the band plays. */
    @ParameterizedTest
    @ValueSource(strings = {"https://video.test/41/", " https://video.test/41 "})
    void aRecordingSavedWithAStrayBlankOrSlashIsStillTheRecording(String savedAs) {

        var facts = RoundupFacts.of(aQuarter()
                .held(List.of(HeldSession.builder().sessionId(41L).communityId(1L).title("Hexagons in anger")
                        .heldAt(MAY).talkCount(0).recordingUrl("https://video.test/41").build()))
                .captured(List.of(new CapturedArtifact(41L, "Recording", savedAs, "Recording"))).build());

        assertThat(facts).containsEntry("resourceCount", 0);
        assertThat(groupsOf(sessionsOf(communityCards(facts).getFirst()).getFirst())).isEmpty();
    }

    /** A builder that never names the thumbnails gets none, rather than a poster that cannot be built. */
    @Test
    void aQuarterBuiltWithoutThumbnailsGivesEveryPosterNone() {

        var facts = RoundupFacts.of(aQuarter().thumbnailUrls(null).build());

        assertThat(sessionsOf(communityCards(facts).getFirst()))
                .allSatisfy(poster -> assertThat(poster).containsEntry("thumbnailUrl", null));
    }

    /** The band plays the recording, so the artifact that IS the recording is neither grouped nor counted again. */
    @Test
    void theSessionsOwnRecordingIsNeverCountedAsAResource() {

        var facts = RoundupFacts.of(aQuarter()
                .held(List.of(HeldSession.builder().sessionId(41L).communityId(1L).title("Hexagons").heldAt(MAY)
                        .recordingUrl("https://video.test/41").build()))
                .captured(List.of(new CapturedArtifact(41L, "Recording", "https://video.test/41", "Recording"),
                        new CapturedArtifact(41L, "Deck", "https://deck", "Slides")))
                .build());

        assertThat(groupsOf(sessionsOf(communityCards(facts).getFirst()).getFirst()))
                .containsExactly(Map.of("label", "Slides", "count", 1));
        assertThat(facts).containsEntry("resourceCount", 1);
    }

    @Test
    void theFirstSessionTheCapLeftOutIsNamedAndTheRestAreACount() {

        var sessions = IntStream.rangeClosed(1, 5)
                .mapToObj(index -> HeldSession.builder().sessionId(40L + index).communityId(1L)
                        .title(index == 3 ? null : "S26E0" + index).heldAt(MAY.plusSeconds(index * 86_400L)).build())
                .toList();

        var none = RoundupFacts.of(aQuarter().held(sessions).sessionsPerCommunityCap(5).build());
        var one = RoundupFacts.of(aQuarter().held(sessions).sessionsPerCommunityCap(4).build());
        var three = RoundupFacts.of(aQuarter().held(sessions).sessionsPerCommunityCap(2).build());

        assertThat(communityCards(none).getFirst()).containsEntry("moreSessionsLabel", null);
        assertThat(communityCards(one).getFirst()).containsEntry("moreSessionsLabel", "+ S26E01 \u00b7 14 May");
        assertThat(communityCards(three).getFirst()).containsEntry("moreSessionsLabel", "+ 16 May and 2 more");
    }

    /** Newest first, so the cap keeps what a reader most wants to watch; the port answers oldest first. */
    @Test
    void aCommunitysPostersRunNewestFirst() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(sessionsOf(communityCards(facts).getFirst())).extracting(poster -> poster.get("dateLabel"))
                .containsExactly("9 June", "13 May");
    }

    @Test
    void theQuarterIsSplitForItsDisplayType() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(facts).containsEntry("quarterLabel", "Q2").containsEntry("yearLabel", "2026");
    }

    /**
     * The structural guarantee this fact map is built on: no value anywhere in it is a temporal, at any depth. Facts
     * are frozen into the outbox as jsonb and read back type-erased, so an {@link Instant} nested inside a community
     * card's session rows would come back a plain string, past the two hand-named key paths {@code FactRehydration}
     * repairs, and every real send would fail on a template call no preview built from live facts ever makes. Asserting
     * the shape rather than the keys that carry a date today is what makes a third one added tomorrow fail here rather
     * than in production.
     */
    @Test
    void noFactIsATemporalAtAnyDepthSoTheJsonbRoundTripIsLosslessByConstruction() {

        var facts = RoundupFacts.of(aQuarter().upcoming(List.of(new UpcomingSession(1L, "Signals in React", AUGUST)))
                .captured(List.of(new CapturedArtifact(41L, "Recording", "https://recording", "Recording"))).build());

        assertThat(temporalsIn(facts)).isEmpty();
        assertThat(facts.toString()).doesNotContain("2026-05-13T");
    }

    private static List<Object> temporalsIn(Object value) {

        return switch (value) {
            case Map<?, ?> map -> map.values().stream().flatMap(nested -> temporalsIn(nested).stream()).toList();
            case List<?> list -> list.stream().flatMap(nested -> temporalsIn(nested).stream()).toList();
            case TemporalAccessor temporal -> List.of(temporal);
            case null, default -> List.of();
        };
    }

    @Test
    void theOneCallToActionIsBrowsingTheCommunities() {

        var facts = RoundupFacts.of(aQuarter().build());

        assertThat(facts).containsEntry("communitiesUrl", "https://axonia.test/communities");
    }

    /** The only fact that asks the reader for something, and it comes straight from the port, organization-wide. */
    @Test
    void theAskIsHowManyTalksStillNeedAPresenterAcrossTheComingQuarter() {

        var facts = RoundupFacts.of(aQuarter().openTalkCount(3).build());

        assertThat(facts).containsEntry("openTalkCount", 3);
    }

    @Test
    void whatWasCapturedIsACountAndTheResourcesThemselvesAndNothingElse() {

        var facts = RoundupFacts.of(aQuarter().captured(capturedArtifacts(47)).build());

        assertThat(facts).containsEntry("resourceCount", 47);
        assertThat(facts.toString()).as("no dial, no diagnosis, no trend").doesNotContain("trend")
                .doesNotContain("health").doesNotContain("score");
    }

    /**
     * Two communities, one of them busier than the other, and one person who presented in both. Every case above
     * overrides only what it is about.
     */
    private static QuarterActivity.QuarterActivityBuilder aQuarter() {

        return QuarterActivity.builder().organizationName("Acme Corp").quarter(Q2)
                .communities(List.of(summary(1, "Backend Guild"), summary(2, "Alpha Guild")))
                .held(List.of(
                        session(41, 1, "Hexagons in anger", MAY, 2,
                                List.of("Ports first, adapters after", "Signals in React"),
                                List.of("Alice Dubois", "Bob Martin")),
                        session(42, 1, "Tracing in prod", JUNE, 1, List.of(), List.of("Alice Dubois")),
                        session(43, 2, "Row level security", APRIL, 1, List.of("RLS from scratch"),
                                List.of("Alice Dubois"))))
                .upcoming(List.of()).captured(List.of()).firstTimePresenterCount(0)
                .topics(List.of(new TagCount("kafka", 9), new TagCount("spring", 5), new TagCount("testing", 1)))
                .openTalkCount(0).communityCap(COMMUNITY_CAP).sessionsPerCommunityCap(4).topicCap(12)
                .communitiesUrl("https://axonia.test/communities").organizationId("acme")
                .publicBaseUrl("https://axonia.test").thumbnailUrls(Map.of());
    }

    private static QuarterActivity.QuarterActivityBuilder twelveActiveCommunities() {

        var ids = IntStream.rangeClosed(1, 12).boxed().toList();
        return aQuarter().communities(ids.stream().map(id -> summary(id, "Guild " + id)).toList())
                .held(ids.stream()
                        .map(id -> session(40 + id, id, "Session " + id, MAY, 1, List.of(), List.of("Presenter " + id)))
                        .toList());
    }

    private static List<CapturedArtifact> capturedArtifacts(int count) {

        return IntStream.range(0, count)
                .mapToObj(i -> new CapturedArtifact(41L, "Artifact " + i, "https://example.test/" + i, "Recording"))
                .toList();
    }

    private static NamedCommunity summary(long id, String name) {

        return new NamedCommunity(id, name, null, null, name.toLowerCase(Locale.ROOT).replace(' ', '-'), null, null,
                null);
    }

    private static HeldSession session(long sessionId, long communityId, String title, Instant heldAt, int talkCount,
            List<String> talkTitles, List<String> presenters) {

        return HeldSession.builder().sessionId(sessionId).communityId(communityId).title(title).heldAt(heldAt)
                .talkCount(talkCount)
                .talks(talkTitles.stream().map(talk -> new HeldTalk(talk, List.of(), null)).toList())
                .presenterNames(presenters).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> sender(Map<String, Object> facts) {

        return (Map<String, String>) facts.get("sender");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> communityCards(Map<String, Object> facts) {

        return (List<Map<String, Object>>) facts.get("communities");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> presentersOf(Map<String, Object> card) {

        return (List<Map<String, String>>) card.get("presenters");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sessionsOf(Map<String, Object> card) {

        return (List<Map<String, Object>>) card.get("sessions");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> talksOf(Map<String, Object> poster) {

        return (List<Map<String, Object>>) poster.get("talks");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groupsOf(Map<String, Object> poster) {

        return (List<Map<String, Object>>) poster.get("resourceGroups");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> topicsOf(Map<String, Object> facts) {

        return (List<Map<String, Object>>) facts.get("topics");
    }

    private static List<Integer> topicWeights(Map<String, Object> facts) {

        return topicsOf(facts).stream().map(topic -> (Integer) topic.get("weight")).toList();
    }

    private static List<String> topicLabels(Map<String, Object> facts) {

        return topicsOf(facts).stream().map(topic -> (String) topic.get("label")).toList();
    }

    private static List<String> communityNames(Map<String, Object> facts) {

        return communityCards(facts).stream().map(card -> (String) card.get("name")).toList();
    }

    private static List<String> presenterNamesIn(Map<String, Object> facts) {

        return communityCards(facts).stream().flatMap(card -> presentersOf(card).stream())
                .map(presenter -> presenter.get("name")).distinct().sorted().toList();
    }
}
