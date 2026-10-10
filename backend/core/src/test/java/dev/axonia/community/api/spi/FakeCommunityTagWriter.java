package dev.axonia.community.api.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.axonia.community.api.TagUsage;

/**
 * In-memory {@link CommunityTagWriter} standing in for one content-owning module. It records what a curator asked of
 * it, so a test can assert that the service reaches every writer rather than the first one, and replays canned usage.
 */
public class FakeCommunityTagWriter implements CommunityTagWriter {

    /** A rename this writer was asked to perform, as the service phrased it. */
    public record Rename(long communityId, String from, String to) {
    }

    /** A removal this writer was asked to perform, as the service phrased it. */
    public record Removal(long communityId, String tag) {
    }

    private record TagRef(long communityId, String tag) {
    }

    private final List<Rename> renames = new ArrayList<>();
    private final List<Removal> removals = new ArrayList<>();
    private final Map<TagRef, List<TagUsage>> reports = new HashMap<>();
    private final Map<Long, List<String>> carried = new HashMap<>();

    /** Arms this writer to report {@code usage} whenever it is asked about the tag in the community. */
    public void report(long communityId, String tag, TagUsage usage) {

        reports.put(new TagRef(communityId, tag), List.of(usage));
    }

    /** Arms this writer to report the tag among those its records carry in the community. */
    public void carry(long communityId, String tag) {

        carried.computeIfAbsent(communityId, id -> new ArrayList<>()).add(tag);
    }

    public List<Rename> renames() {

        return List.copyOf(renames);
    }

    public List<Removal> removals() {

        return List.copyOf(removals);
    }

    @Override
    public List<TagUsage> usage(long communityId, String tag) {

        return reports.getOrDefault(new TagRef(communityId, tag), List.of());
    }

    @Override
    public List<String> tagsIn(long communityId) {

        return List.copyOf(carried.getOrDefault(communityId, List.of()));
    }

    @Override
    public void rename(long communityId, String from, String to) {

        renames.add(new Rename(communityId, from, to));
    }

    @Override
    public void remove(long communityId, String tag) {

        removals.add(new Removal(communityId, tag));
    }
}
