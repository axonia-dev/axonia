package dev.axonia.learning.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagUsage;
import dev.axonia.community.api.TagUsageKind;
import dev.axonia.community.api.spi.CommunityTagWriter;
import dev.axonia.learning.application.port.out.LearningIntentRepository;
import dev.axonia.learning.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/** The learning context's slice of a curator's rename or delete: the tags members want to learn in the community. */
@Component
@RequiredArgsConstructor
class LearningTagWriter implements CommunityTagWriter {

    private final OrganizationContext organizationContext;
    private final LearningIntentRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<TagUsage> usage(long communityId, String tag) {

        var count = repository.countIn(organizationContext.organizationId(), communityId, tag);
        return count == 0 ? List.of() : List.of(new TagUsage(TagUsageKind.LEARNING_INTENT, count));
    }

    @Override
    public void rename(long communityId, String from, String to) {

        repository.renameTag(organizationContext.organizationId(), communityId, from, to);
    }

    @Override
    public void remove(long communityId, String tag) {

        repository.removeTag(organizationContext.organizationId(), communityId, tag);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> tagsIn(long communityId) {

        return repository.tagsIn(organizationContext.organizationId(), communityId);
    }
}
