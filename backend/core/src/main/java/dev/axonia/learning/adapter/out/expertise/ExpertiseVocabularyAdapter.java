package dev.axonia.learning.adapter.out.expertise;

import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.learning.application.port.out.Vocabulary;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExpertiseVocabularyAdapter implements Vocabulary {

    private final CommunityVocabulary expertiseVocabulary;

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
