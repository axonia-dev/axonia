package dev.axonia.learning.internal;

import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.CommunityAreas;
import dev.axonia.expertise.ExpertiseVocabulary;
import dev.axonia.learning.Vocabulary;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ExpertiseVocabularyAdapter implements Vocabulary {

    private final ExpertiseVocabulary expertiseVocabulary;

    @Override
    public void shareLock(String org, long communityId) {

        expertiseVocabulary.shareLock(org, communityId);
    }

    @Override
    public CommunityAreas areasOf(long communityId) {

        return expertiseVocabulary.areasOf(communityId);
    }

    @Override
    public Map<String, Integer> canBeAsked(String slug) {

        return expertiseVocabulary.canBeAsked(slug);
    }

    @Override
    public String writableName(String raw) {

        return expertiseVocabulary.writableName(raw);
    }
}
