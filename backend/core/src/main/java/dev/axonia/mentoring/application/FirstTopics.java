package dev.axonia.mentoring.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.Vocabulary;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;
import lombok.RequiredArgsConstructor;

/**
 * A new pairing's first topics: the proposal's, in its order and in To do, each linked to the guild's area of the same
 * name. The acceptance takes the vocabulary's share lock before the members' locks, the order expertise takes them in,
 * so a curator's rename cannot land between reading the areas and writing the topics.
 */
@Component("mentoringFirstTopics")
@RequiredArgsConstructor
class FirstTopics {

    private final Vocabulary vocabulary;
    private final JournalRepository journal;

    void lockVocabulary(String org, MentoringCommunity community) {

        vocabulary.shareLock(org, community.id());
    }

    /** Called after {@link #lockVocabulary}, in the same transaction; answers the topics seeded, in trail order. */
    List<Topic> seed(String org, MentoringCommunity community, Pairing pairing, List<String> labels) {

        var areas = vocabulary.areasOf(community.id());
        var known = vocabulary.areasIn(community.slug());
        var seeded = new ArrayList<Topic>();
        for (var position = 0; position < labels.size(); position++) {
            var label = labels.get(position);
            var topic = Topic.builder().pairingId(pairing.id()).label(label).area(areaOf(label, areas, known))
                    .state(TopicState.TODO).position(position).createdAt(pairing.startedAt()).build();
            seeded.add(topic.toBuilder().id(journal.insertTopic(org, topic)).build());
        }
        return seeded;
    }

    private String areaOf(String label, CommunityAreas areas, Set<String> known) {

        var canonical = areas.canonicalOf(vocabulary.normalized(label));
        return known.contains(canonical) ? canonical : null;
    }
}
