package dev.axonia.expertise.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.api.CuratedNamedArea;
import dev.axonia.expertise.api.NamedAreaAssignment;
import dev.axonia.expertise.api.NamedAreaCuration;
import dev.axonia.expertise.api.TagCuration;
import dev.axonia.expertise.api.TagMergeSuggestion;
import dev.axonia.expertise.api.TagSplit;
import dev.axonia.expertise.api.TagUnassignment;
import dev.axonia.expertise.api.TagUsageReport;
import dev.axonia.expertise.api.VocabularyTag;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.NamedArea;
import lombok.RequiredArgsConstructor;

/**
 * Tag and named-area curation as another context sees it: the curator's verbs, each answering with the state it left,
 * in the expertise module's public types. Every write and the read of its result share one transaction, so the answer
 * is the state the write produced.
 */
@Service
@RequiredArgsConstructor
public class CurationFacade implements TagCuration, NamedAreaCuration {

    private final ExpertiseCurationService tags;
    private final NamedAreaCurationService namedAreas;

    @Override
    @Transactional(readOnly = true)
    public List<VocabularyTag> vocabulary(String slug) {

        var areaOfTag = areaOfTag(namedAreas.namedAreas(slug));
        return tags.vocabulary(slug).stream().map(area -> toVocabularyTag(area, areaOfTag)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagMergeSuggestion> suggestions(String slug) {

        var suggestions = tags.suggestions(slug).stream().limit(SUGGESTION_LIMIT).toList();
        if (suggestions.isEmpty()) {
            return List.of();
        }
        var areas = tags.vocabulary(slug).stream()
                .collect(Collectors.toMap(ExpertiseArea::canonicalTag, Function.identity()));
        var usages = tags.usages(slug,
                suggestions.stream().flatMap(s -> Stream.of(s.tagA(), s.tagB())).distinct().toList());
        return suggestions.stream().map(
                s -> new TagMergeSuggestion(side(s.tagA(), areas, usages), side(s.tagB(), areas, usages), s.score()))
                .toList();
    }

    @Override
    @Transactional
    public VocabularyTag merge(String slug, List<String> variants, String canonical) {

        tags.mergeMany(slug, variants, canonical);
        return vocabularyTag(slug, canonical);
    }

    @Override
    @Transactional
    public TagSplit split(String slug, String variant) {

        var formerCanonical = tags.split(slug, variant);
        var vocabulary = vocabulary(slug);
        return new TagSplit(find(vocabulary, variant),
                formerCanonical.map(canonical -> find(vocabulary, canonical)).orElse(null));
    }

    @Override
    @Transactional
    public VocabularyTag rename(String slug, String from, String to) {

        tags.rename(slug, from, to);
        return vocabularyTag(slug, to);
    }

    @Override
    public void dismiss(String slug, String tagA, String tagB) {

        tags.dismiss(slug, tagA, tagB);
    }

    @Override
    public void undismiss(String slug, String tagA, String tagB) {

        tags.undismiss(slug, tagA, tagB);
    }

    @Override
    public TagUsageReport usage(String slug, String tag) {

        return tags.usage(slug, tag);
    }

    @Override
    public void delete(String slug, String tag) {

        tags.delete(slug, tag);
    }

    @Override
    public List<CuratedNamedArea> namedAreas(String slug) {

        return namedAreas.namedAreas(slug).stream().map(CurationFacade::toCurated).toList();
    }

    @Override
    public CuratedNamedArea create(String slug, String name) {

        return toCurated(namedAreas.createNamedArea(slug, name));
    }

    @Override
    public CuratedNamedArea rename(String slug, long id, String name) {

        return toCurated(namedAreas.renameNamedArea(slug, id, name));
    }

    @Override
    public CuratedNamedArea delete(String slug, long id) {

        return toCurated(namedAreas.deleteNamedArea(slug, id));
    }

    @Override
    public NamedAreaAssignment assign(String slug, long id, List<String> tags) {

        var placement = namedAreas.placeAllInNamedArea(slug, id, tags);
        return new NamedAreaAssignment(toCurated(placement.area()), placement.moves().stream()
                .map(move -> new NamedAreaAssignment.Move(move.tag(), move.fromId(), move.fromName())).toList());
    }

    @Override
    public TagUnassignment unassign(String slug, String tag) {

        var removal = namedAreas.removeFromNamedArea(slug, tag);
        return new TagUnassignment(removal.tag(), removal.from().map(CurationFacade::toCurated));
    }

    private VocabularyTag vocabularyTag(String slug, String tag) {

        return find(vocabulary(slug), tag);
    }

    /** A tag the vocabulary no longer folds anything onto, nor counts anyone for, reads as bare rather than absent. */
    private static VocabularyTag find(List<VocabularyTag> vocabulary, String tag) {

        var normalized = TagNormalizer.normalize(tag);
        return vocabulary.stream().filter(entry -> entry.tag().equals(normalized)).findFirst()
                .orElseGet(() -> VocabularyTag.bare(normalized));
    }

    private static Map<String, VocabularyTag.NamedAreaRef> areaOfTag(List<NamedArea> areas) {

        return areas.stream()
                .flatMap(area -> area.tags().stream()
                        .map(tag -> Map.entry(tag, new VocabularyTag.NamedAreaRef(area.id(), area.name()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static VocabularyTag toVocabularyTag(ExpertiseArea area,
            Map<String, VocabularyTag.NamedAreaRef> areaOfTag) {

        return new VocabularyTag(area.canonicalTag(), mergedSpellings(area), area.peopleCount(),
                areaOfTag.get(area.canonicalTag()));
    }

    private static TagMergeSuggestion.Side side(String tag, Map<String, ExpertiseArea> areas,
            Map<String, TagUsageReport> usages) {

        var area = areas.getOrDefault(tag, new ExpertiseArea(tag, List.of(), 0));
        return new TagMergeSuggestion.Side(tag, mergedSpellings(area), area.peopleCount(), usages.get(tag));
    }

    /** The fold lists every spelling carried under an area, its canonical among them when content carries it. */
    private static List<String> mergedSpellings(ExpertiseArea area) {

        return area.variants().stream().filter(spelling -> !spelling.equals(area.canonicalTag())).toList();
    }

    private static CuratedNamedArea toCurated(NamedArea area) {

        return new CuratedNamedArea(area.id(), area.name(), area.tags());
    }
}
