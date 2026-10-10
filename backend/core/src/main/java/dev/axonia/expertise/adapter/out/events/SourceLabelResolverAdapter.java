package dev.axonia.expertise.adapter.out.events;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.expertise.application.port.out.SourceLabelResolver;
import dev.axonia.expertise.domain.SourceRef;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.knowledge.api.ArtifactTitles;
import lombok.RequiredArgsConstructor;

/**
 * Resolves source titles across the modules that own them: SLOT and TOPIC titles come from {@code events}, ARTIFACT
 * titles from {@code knowledge}, one batched read per source type. A source that no longer resolves is simply left out
 * of the result.
 *
 * <p>
 * Every lookup here is a batched reader returning a map, never an aggregate read that throws on a missing row. TOPIC
 * and ARTIFACT titles once went through {@code TopicService.get} / {@code ArtifactService.get} one id at a time, with
 * the {@code NotFoundException} caught and turned into a null title. That looked harmless and was not: both are
 * {@code @Transactional}, so they joined the caller's read transaction, and Spring marks a joined transaction
 * rollback-only the moment a method throws out of it - before the catch here ever runs. The evidence view then
 * committed and the commit threw {@code UnexpectedRollbackException}, so one withdrawn artifact turned every expertise
 * search over its tags into a 500. A miss must be a value, not an exception.
 */
@Component
@RequiredArgsConstructor
class SourceLabelResolverAdapter implements SourceLabelResolver {

    private final ActivityTitles activityTitleReader;
    private final ArtifactTitles artifactTitleReader;

    @Override
    public Map<SourceRef, String> titlesFor(Collection<SourceRef> refs) {

        var distinct = refs.stream().distinct().toList();
        var titles = new HashMap<SourceRef, String>();

        put(titles, SourceType.SLOT, distinct, activityTitleReader::slotTitles);
        put(titles, SourceType.TOPIC, distinct, activityTitleReader::topicTitles);
        put(titles, SourceType.ARTIFACT, distinct, artifactTitleReader::titlesByIds);

        return titles;
    }

    private static void put(Map<SourceRef, String> titles, SourceType type, List<SourceRef> refs,
            Function<Collection<Long>, Map<Long, String>> lookup) {

        lookup.apply(idsOfType(refs, type)).forEach((id, title) -> titles.put(new SourceRef(type, id), title));
    }

    private static List<Long> idsOfType(Collection<SourceRef> refs, SourceType type) {

        return refs.stream().filter(r -> r.sourceType() == type).map(SourceRef::sourceId).toList();
    }
}
