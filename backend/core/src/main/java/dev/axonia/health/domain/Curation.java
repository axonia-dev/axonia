package dev.axonia.health.domain;

import java.util.Collection;

/**
 * Behind the scenes: how many curation acts (topics proposed, artifacts captured) there were and the share the top
 * curator made. Counts in, anonymous figures out.
 */
public record Curation(long acts, double topShare) {

    public static Curation of(Collection<Long> actCounts) {

        var acts = actCounts.stream().mapToLong(Long::longValue).sum();
        if (acts == 0) {
            return new Curation(0, 0.0);
        }
        var top = actCounts.stream().mapToLong(Long::longValue).max().orElseThrow();
        return new Curation(acts, (double) top / acts);
    }
}
