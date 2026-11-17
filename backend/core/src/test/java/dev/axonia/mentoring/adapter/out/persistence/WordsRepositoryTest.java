package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.mentoring.domain.Words;
import dev.axonia.mentoring.domain.WordsForMentor;

import static org.assertj.core.api.Assertions.assertThat;

@Import({WordsRepositoryImpl.class, PairingRepositoryImpl.class})
class WordsRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant EARLY = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant LATE = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    private WordsRepositoryImpl repo;

    @Autowired
    private PairingRepositoryImpl pairings;

    @Test
    void upsertWordsReportsCreationOnlyTheFirstTime() {

        // given
        var pairing = pairings.insert(ORG, pairing(seedCommunity("platform"), "mia", "max").build());

        // when
        var first = repo.upsert(ORG, words(pairing, "thanks", EARLY));
        var second = repo.upsert(ORG, words(pairing, "thanks a lot", LATE));

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(repo.find(ORG, pairing)).contains(
                Words.builder().pairingId(pairing).body("thanks a lot").writtenAt(EARLY).updatedAt(LATE).build());
    }

    @Test
    void theMentorOpensOnceAndAChangeSealsItAgain() {

        // given
        var pairing = pairings.insert(ORG, pairing(seedCommunity("platform"), "mia", "max").build());
        repo.upsert(ORG, words(pairing, "thanks", EARLY));

        // when
        repo.markOpened(ORG, pairing, EARLY.plusSeconds(60));
        repo.markOpened(ORG, pairing, EARLY.plusSeconds(120));
        var opened = repo.find(ORG, pairing).orElseThrow();
        repo.upsert(ORG, words(pairing, "thanks again", LATE));
        var actual = repo.find(ORG, pairing).orElseThrow();

        // then
        assertThat(opened.openedAt()).isEqualTo(EARLY.plusSeconds(60));
        assertThat(actual.openedAt()).isNull();
        assertThat(actual.updatedAt()).isEqualTo(LATE);
        assertThat(repo.toMentor(ORG, "mia")).singleElement().extracting(WordsForMentor::openedAt).isNull();
    }

    @Test
    void theSameWordsAgainKeepTheEnvelopeOpen() {

        // given
        var pairing = pairings.insert(ORG, pairing(seedCommunity("platform"), "mia", "max").build());
        repo.upsert(ORG, words(pairing, "thanks", EARLY));
        repo.markOpened(ORG, pairing, EARLY.plusSeconds(60));

        // when
        var created = repo.upsert(ORG, words(pairing, "thanks", LATE));
        var actual = repo.find(ORG, pairing).orElseThrow();

        // then
        assertThat(created).isFalse();
        assertThat(actual.openedAt()).isEqualTo(EARLY.plusSeconds(60));
        assertThat(actual.updatedAt()).isNull();
        assertThat(repo.toMentor(ORG, "mia")).singleElement().extracting(WordsForMentor::openedAt)
                .isEqualTo(EARLY.plusSeconds(60));
    }

    @Test
    void findIsEmptyBeforeAnyWords() {

        assertThat(repo.find(ORG, 424242L)).isEmpty();
    }

    @Test
    void deleteRemovesTheWords() {

        // given
        var pairing = pairings.insert(ORG, pairing(seedCommunity("platform"), "mia", "max").build());
        repo.upsert(ORG, words(pairing, "thanks", EARLY));

        // when
        repo.delete(ORG, pairing);

        // then
        assertThat(repo.find(ORG, pairing)).isEmpty();
    }

    @Test
    void toMentorListsOnlyTheWordsAddressedToThatMentorNewestFirst() {

        // given
        var community = seedCommunity("platform");
        var older = pairings.insert(ORG, pairing(community, "mia", "max").build());
        var newer = pairings.insert(ORG, pairing(community, "mia", "zoe").build());
        var other = pairings.insert(ORG, pairing(community, "lea", "max").build());
        repo.upsert(ORG, words(older, "first", EARLY));
        repo.upsert(ORG, words(newer, "second", LATE));
        repo.upsert(ORG, words(other, "not for mia", LATE));

        // when
        var actual = repo.toMentor(ORG, "mia");

        // then
        assertThat(actual).extracting(WordsForMentor::pairingId).containsExactly(newer, older);
        assertThat(actual.get(0)).satisfies(w -> {
            assertThat(w.menteeUserId()).isEqualTo("zoe");
            assertThat(w.communityId()).isEqualTo(community);
            assertThat(w.body()).isEqualTo("second");
            assertThat(w.startedAt()).isNotNull();
            assertThat(w.endedAt()).isNull();
        });
    }

    private static Words words(long pairing, String body, Instant at) {

        return Words.builder().pairingId(pairing).body(body).writtenAt(at).build();
    }
}
