package dev.axonia.roundup.domain;

import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.UpcomingSession;
import dev.axonia.roundup.api.spi.TopicActivity.TagCount;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.shared.api.colour.GuildTints;
import dev.axonia.shared.api.text.Initials;
import lombok.Builder;

/**
 * Turns one quarter's read activity into the flat fact map {@code templates/mail/organization-roundup} renders. A pure
 * function: no repository, no port, no clock.
 *
 * <p>
 * <b>It emits no temporal value at any depth.</b> Facts are frozen into the outbox as jsonb and read back type-erased,
 * so an {@link java.time.Instant} nested in a session poster would come back a plain string no template can format.
 * Every date leaves here already rendered, and {@code RoundupFactsTest} walks the whole map to pin it.
 *
 * <p>
 * <b>Presenters are initialled in the HTML and named in the {@code .txt}</b>, so each is carried as a name AND its
 * initials from one list, and the two renderings cannot disagree about who presented. First-time presenters stay a
 * count and are never listed: "X presented for the first time" is about a person's trajectory, and in a manager's inbox
 * it reads as a performance signal. For the same reason no feedback score is carried.
 *
 * <p>
 * <b>Most figures are community-scoped, two are organization-wide.</b> Every figure but
 * {@code firstTimeContributorCount} and {@code openTalkCount} drops a session whose community
 * {@code CommunityActivity#activeCommunities} no longer names. Those two come from organization-wide ports, which is
 * why the first-timer figure is a standalone count rather than "N of them", and why the templates suppress it when
 * {@code sessionCount} is 0 (only a floorless preview reaches that state).
 */
public final class RoundupFacts {

    /**
     * The sender monogram's pair: the shell's neutral chip surface and body ink. Fixed, because an enterprise
     * organization has no accent of its own to resolve.
     */
    private static final String SENDER_TINT_BACKGROUND = "#f6f0ec";
    private static final String SENDER_TINT_FOREGROUND = "#211a26";

    /** Five avatars per community; the sixth onwards is a count. */
    private static final int PRESENTER_CAP = 5;

    private static final int MONTHS_PER_QUARTER = 3;

    /**
     * The topic cloud's five bands and the ratios they switch at, verbatim from the Expertise page's
     * {@code TagConstellation}, so a tag reads the same size in the letter as in the app. A cloud where every tag tied
     * takes the middle band.
     */
    private static final int LIGHTEST_WEIGHT = 1;
    private static final int MIDDLE_WEIGHT = 3;
    private static final List<Double> WEIGHT_THRESHOLDS = List.of(0.2, 0.4, 0.6, 0.8);

    /** Named because {@link #isBelowActivityFloor} reads back exactly what {@link #of} wrote under it. */
    private static final String SESSION_COUNT = "sessionCount";

    private static final DateTimeFormatter NEXT_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter NEXT_MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter NEXT_DAY_OF_MONTH = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    /** How many titled talks a poster lists before it counts the rest. */
    public static final int TALK_CAP = 3;

    /**
     * The order a community's posters are shown in, newest first, so the cap keeps the sessions a reader is most likely
     * to still want to watch. {@code RoundupFactsAssembler} composes thumbnails for the same sessions in the same
     * order.
     */
    public static final Comparator<HeldSession> NEWEST_FIRST = Comparator.comparing(HeldSession::heldAt)
            .thenComparingLong(HeldSession::sessionId).reversed();

    private RoundupFacts() {
    }

    public static Map<String, Object> of(QuarterActivity activity) {

        var reading = Reading.of(activity);
        var cards = communityCards(activity, reading);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("sender", sender(activity.organizationName()));
        facts.put("sessionTitle", heading(activity.quarter()));
        facts.put("periodLabel", periodLabel(activity.quarter()));
        facts.put("quarterLabel", "Q" + activity.quarter().index());
        facts.put("yearLabel", String.valueOf(activity.quarter().year()));
        facts.put(SESSION_COUNT, reading.held().size());
        facts.put("talkCount", talkCount(reading.held()));
        facts.put("presenterCount", presenterNames(reading.held()).size());
        facts.put("resourceCount", resourceCount(reading));
        facts.put("firstTimeContributorCount", activity.firstTimePresenterCount());
        facts.put("topics", topicCloud(reading, activity.topics(), activity.topicCap()));
        facts.put("communities", cards.stream().limit(activity.communityCap()).toList());
        facts.put("moreCommunitiesCount", Math.max(0, cards.size() - activity.communityCap()));
        facts.put("openTalkCount", activity.openTalkCount());
        facts.put("communitiesUrl", activity.communitiesUrl());
        return Collections.unmodifiableMap(facts);
    }

    /**
     * Whether the assembled letter would report nothing at all: not one session it can still name. The floor
     * {@code RoundupProducer} refuses to send below, asked of the FACTS rather than of the reading: a quarter whose
     * every community was archived since reads busy at the port and empty here, and the letter must not open "No
     * sessions were held over the quarter.". Resources hang off held sessions, so no session means no resource.
     */
    public static boolean isBelowActivityFloor(Map<String, Object> facts) {

        // Safe by construction: of() is the only writer of this key, and writes an int under it.
        return (Integer) facts.get(SESSION_COUNT) == 0;
    }

    /**
     * The quarter as the letter can still describe it, and <b>the one place the active-communities filter is
     * applied</b>: held and upcoming sessions of a still-named community, and what was captured on those held sessions.
     */
    @Builder
    private record Reading(Map<Long, NamedCommunity> communities, List<HeldSession> held,
            List<UpcomingSession> upcoming, List<CapturedArtifact> captured) {

        /** No merge function: a community id is a primary key, so a duplicate is a broken port and should say so. */
        public static Reading of(QuarterActivity activity) {

            var communities = activity.communities().stream()
                    .collect(Collectors.toMap(NamedCommunity::id, community -> community));
            var held = activity.held().stream().filter(session -> communities.containsKey(session.communityId()))
                    .toList();
            var sessionIds = held.stream().map(HeldSession::sessionId).collect(Collectors.toSet());
            return Reading.builder().communities(communities).held(held)
                    .upcoming(activity.upcoming().stream()
                            .filter(session -> communities.containsKey(session.communityId())).toList())
                    .captured(activity.captured().stream().filter(artifact -> sessionIds.contains(artifact.sessionId()))
                            .toList())
                    .build();
        }
    }

    private static Map<String, String> sender(String organizationName) {

        var fact = new LinkedHashMap<String, String>();
        fact.put("name", organizationName);
        fact.put("initials", Initials.of(organizationName));
        fact.put("tintBackground", SENDER_TINT_BACKGROUND);
        fact.put("tintForeground", SENDER_TINT_FOREGROUND);
        return Collections.unmodifiableMap(fact);
    }

    /** The agenda's own size, titled or not; independent of the presenter count. */
    private static int talkCount(List<HeldSession> held) {

        return held.stream().mapToInt(HeldSession::talkCount).sum();
    }

    /**
     * Everybody who presented, once each, alphabetically: recognition, so no order anybody could read a ranking into.
     * An erased member is dropped rather than named "Deleted member" to the whole organization. Identity is the display
     * name, since the port carries no ids; two colleagues sharing one count once, which is accepted.
     */
    private static List<String> presenterNames(List<HeldSession> held) {

        return held.stream().flatMap(session -> session.presenterNames().stream()).filter(SessionPosters::isNamed)
                .distinct().sorted().toList();
    }

    /**
     * One resource counts once however many sessions it hangs off, identified by {@code (title, url)} since a row
     * carries no artifact id; a session's own recording is not counted, as its poster plays it rather than lists it.
     */
    private static int resourceCount(Reading reading) {

        return (int) reading.held().stream()
                .flatMap(session -> SessionPosters.keptBy(session, reading.captured()).stream())
                .map(artifact -> Arrays.asList(artifact.title(), artifact.url())).distinct().count();
    }

    /**
     * The quarter's tags as a weighted cloud, busiest first, the port's order. Dropped entirely when no held session
     * survived the filter: {@link TagCount} carries no session id, so all or nothing is the only honest granularity.
     */
    private static List<Map<String, Object>> topicCloud(Reading reading, List<TagCount> tags, int cap) {

        if (reading.held().isEmpty()) {
            return List.of();
        }
        var shown = tags.stream().limit(cap).toList();
        var counts = shown.stream().mapToInt(TagCount::count).summaryStatistics();
        return shown.stream().map(tag -> topic(tag, counts.getMin(), counts.getMax())).toList();
    }

    private static Map<String, Object> topic(TagCount tag, int min, int max) {

        var fact = new LinkedHashMap<String, Object>();
        fact.put("label", tag.tag());
        fact.put("weight", weightOf(tag.count(), min, max));
        return Collections.unmodifiableMap(fact);
    }

    /** Relative to the tags SHOWN, so a capped cloud still uses its whole scale. */
    private static int weightOf(int count, int min, int max) {

        if (max == min) {
            return MIDDLE_WEIGHT;
        }
        var ratio = (double) (count - min) / (max - min);
        return LIGHTEST_WEIGHT + (int) WEIGHT_THRESHOLDS.stream().filter(threshold -> ratio >= threshold).count();
    }

    /** One card per community that held something, busiest first and alphabetical within a tie. */
    private static List<Map<String, Object>> communityCards(QuarterActivity activity, Reading reading) {

        return reading.held().stream().collect(Collectors.groupingBy(HeldSession::communityId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, Long> entry) -> entry.getValue()).reversed()
                        .thenComparing(entry -> reading.communities().get(entry.getKey()).name()))
                .map(entry -> communityCard(activity, reading, reading.communities().get(entry.getKey()))).toList();
    }

    /**
     * A community's whole share of the letter, dressed in its own tints. The next session is the FIRST of its upcoming
     * rows, which rests on {@code SessionActivity#scheduledAfter} answering soonest first. Posters run
     * {@link #NEWEST_FIRST}, the order the assembler composed thumbnails in.
     */
    private static Map<String, Object> communityCard(QuarterActivity activity, Reading reading,
            NamedCommunity community) {

        var sessions = reading.held().stream().filter(session -> session.communityId() == community.id())
                .sorted(NEWEST_FIRST).toList();
        var shown = sessions.stream().limit(activity.sessionsPerCommunityCap()).toList();
        var presenters = presenterNames(sessions);
        var next = reading.upcoming().stream().filter(session -> session.communityId() == community.id()).findFirst()
                .orElse(null);

        var card = new LinkedHashMap<String, Object>();
        card.put("name", community.name());
        card.put("initials", Initials.of(community.name()));
        card.put("badgeUrl", community.badgeUrl());
        card.put("tints", GuildTints.of(community.accentColor()));
        card.put("presenters", presenters.stream().limit(PRESENTER_CAP).map(SessionPosters::person).toList());
        card.put("morePresenterCount", Math.max(0, presenters.size() - PRESENTER_CAP));
        card.put("sessionCountLabel", sessions.size() + (sessions.size() == 1 ? " session" : " sessions"));
        card.put("sessions", shown.stream()
                .map(session -> SessionPosters.poster(activity, community, session, reading.captured())).toList());
        card.put("moreSessionsLabel", moreSessionsLabel(sessions.subList(shown.size(), sessions.size())));
        card.put("nextTitle", next == null ? null : next.title());
        card.put("nextLabel", next == null ? null : NEXT_DAY.format(next.scheduledAt()));
        card.put("nextMonth", next == null ? null : NEXT_MONTH.format(next.scheduledAt()).toUpperCase(Locale.ENGLISH));
        card.put("nextDay", next == null ? null : NEXT_DAY_OF_MONTH.format(next.scheduledAt()));
        return Collections.unmodifiableMap(card);
    }

    /** "+ S26E07 · 15 July", naming the first session the cap left out, then "and 2 more" for the rest. */
    private static String moreSessionsLabel(List<HeldSession> hidden) {

        if (hidden.isEmpty()) {
            return null;
        }
        var first = hidden.getFirst();
        var named = SessionPosters.isTitled(first.title())
                ? first.title() + " \u00b7 " + SessionPosters.dateLabel(first)
                : SessionPosters.dateLabel(first);
        return "+ " + named + (hidden.size() > 1 ? " and " + (hidden.size() - 1) + " more" : "");
    }

    /** "Q2 2026": the outbox's "2026-Q2" is a key, not a sentence. */
    private static String heading(Quarter quarter) {

        return "Q" + quarter.index() + " " + quarter.year();
    }

    /** "April to June 2026": the months a reader recognises, since "Q2" alone means four things to four people. */
    private static String periodLabel(Quarter quarter) {

        var firstMonth = quarter.index() * MONTHS_PER_QUARTER - (MONTHS_PER_QUARTER - 1);
        return monthName(firstMonth) + " to " + monthName(firstMonth + MONTHS_PER_QUARTER - 1) + " " + quarter.year();
    }

    private static String monthName(int month) {

        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
