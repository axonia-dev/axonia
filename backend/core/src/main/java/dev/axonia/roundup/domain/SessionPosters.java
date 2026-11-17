package dev.axonia.roundup.domain;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.HeldTalk;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.shared.api.text.Initials;
import dev.axonia.shared.api.web.OrganizationLinks;

/**
 * One session as the letter stages it: a band that plays the recording, the titled talks, the going count and the
 * resources it kept, counted by kind. Every date leaves here as a string; see {@link RoundupFacts} for why.
 */
final class SessionPosters {

    /**
     * The shell's avatar trio. A person's colour hangs off {@code String#hashCode}, which is specified, so somebody who
     * appears in two cards wears one colour in both.
     */
    private static final List<String> AVATAR_TINTS = List.of("#de3b78", "#6e3ceb", "#1f9d63");

    private static final int MINUTES_PER_HOUR = 60;
    private static final String UNTYPED_RESOURCE = "Link";
    private static final String SESSION_PATH = "%s/communities/%s/sessions/%d";

    /** UTC and English, like the shell's own date line; no year, which the headline already states. */
    private static final DateTimeFormatter DAY_IN_QUARTER = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private SessionPosters() {
    }

    static Map<String, Object> poster(QuarterActivity activity, NamedCommunity community, HeldSession session,
            List<CapturedArtifact> captured) {

        var titled = session.talks().stream().filter(talk -> isTitled(talk.title())).toList();
        var row = new LinkedHashMap<String, Object>();
        row.put("dateLabel", dateLabel(session));
        row.put("title", session.title());
        row.put("durationLabel", durationLabel(session.durationMinutes()));
        row.put("recordingUrl", session.recordingUrl());
        row.put("sessionUrl", sessionUrl(activity, community, session));
        row.put("thumbnailUrl", thumbnailUrlOf(activity, session));
        row.put("talks", titled.stream().limit(RoundupFacts.TALK_CAP).map(SessionPosters::talk).toList());
        row.put("moreTalkCount", Math.max(0, titled.size() - RoundupFacts.TALK_CAP));
        row.put("goingCount", session.goingCount());
        row.put("resourceGroups", resourceGroups(keptBy(session, captured)));
        return Collections.unmodifiableMap(row);
    }

    static String dateLabel(HeldSession session) {

        return DAY_IN_QUARTER.format(session.heldAt());
    }

    /**
     * What a session kept, without the recording: the band already plays it, and counting it again in the take-away
     * strip would show one thing twice.
     */
    static List<CapturedArtifact> keptBy(HeldSession session, List<CapturedArtifact> captured) {

        return captured.stream().filter(artifact -> artifact.sessionId() == session.sessionId())
                .filter(artifact -> !Objects.equals(comparable(artifact.url()), comparable(session.recordingUrl())))
                .toList();
    }

    /** One trailing slash and surrounding blanks never make two links to the same recording differ. */
    private static String comparable(String url) {

        if (url == null) {
            return null;
        }
        var trimmed = url.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    static Map<String, String> person(String name) {

        var fact = new LinkedHashMap<String, String>();
        fact.put("name", name);
        fact.put("initials", Initials.of(name));
        fact.put("tint", AVATAR_TINTS.get(Math.floorMod(name.hashCode(), AVATAR_TINTS.size())));
        return Collections.unmodifiableMap(fact);
    }

    static boolean isTitled(String title) {

        return title != null && !title.isBlank();
    }

    static boolean isNamed(String name) {

        return !DirectoryUser.DELETED_MEMBER_LABEL.equals(name);
    }

    /** "45 min", "1h", "1h 12"; absent when the session never stated how long it ran. */
    static String durationLabel(Integer minutes) {

        if (minutes == null || minutes <= 0) {
            return null;
        }
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + " min";
        }
        var hours = minutes / MINUTES_PER_HOUR;
        var rest = minutes % MINUTES_PER_HOUR;
        return rest == 0 ? hours + "h" : "%dh %02d".formatted(hours, rest);
    }

    private static String sessionUrl(QuarterActivity activity, NamedCommunity community, HeldSession session) {

        return OrganizationLinks.withOrganization(
                SESSION_PATH.formatted(activity.publicBaseUrl(), community.slug(), session.sessionId()),
                activity.organizationId());
    }

    private static String thumbnailUrlOf(QuarterActivity activity, HeldSession session) {

        return activity.thumbnailUrls().get(session.sessionId());
    }

    /** The first presenter wears the avatar and the rest are a count; an erased member is neither. */
    private static Map<String, Object> talk(HeldTalk talk) {

        var presenters = talk.presenterNames().stream().filter(SessionPosters::isNamed).toList();
        var fact = new LinkedHashMap<String, Object>();
        fact.put("title", talk.title());
        fact.put("presenter", presenters.isEmpty() ? null : person(presenters.getFirst()));
        fact.put("morePresenterCount", Math.max(0, presenters.size() - 1));
        fact.put("tag", talk.tag());
        return Collections.unmodifiableMap(fact);
    }

    private static List<Map<String, Object>> resourceGroups(List<CapturedArtifact> kept) {

        return kept.stream()
                .collect(Collectors.groupingBy(
                        artifact -> Objects.requireNonNullElse(artifact.typeLabel(), UNTYPED_RESOURCE),
                        Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(SessionPosters::resourceGroup).toList();
    }

    private static Map<String, Object> resourceGroup(Map.Entry<String, Long> group) {

        var fact = new LinkedHashMap<String, Object>();
        fact.put("label", group.getKey());
        fact.put("count", group.getValue().intValue());
        return Collections.unmodifiableMap(fact);
    }
}
