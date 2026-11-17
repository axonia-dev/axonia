package dev.axonia.mentoring.adapter.out.expertise;

import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.mentoring.application.port.out.Vocabulary;
import lombok.RequiredArgsConstructor;

@Component("mentoringVocabularyAdapter")
@RequiredArgsConstructor
class MentoringVocabularyAdapter implements Vocabulary {

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
    public Set<String> areasIn(String slug) {

        return Set.copyOf(expertiseVocabulary.visibleTags(slug));
    }

    @Override
    public String normalized(String label) {

        return TagNormalizer.normalize(label);
    }
}
