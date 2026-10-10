package dev.axonia.roundup.domain;

import java.time.Instant;
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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.UpcomingSession;
import dev.axonia.roundup.api.spi.TopicActivity.TagCount;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.shared.api.text.Initials;
import lombok.Builder;

/**
 * Turns one quarter's read activity into the flat fact map {@code templates/mail/organization-roundup} renders. A pure
 * function, the same shape {@code events}' {@code SessionFacts} has: no repository access, no port, no clock. Thymeleaf
 * picks the singulars it still owns; this class decides what is present, what is absent, and how much of it fits.
 *
 * <p>
 * <b>It deliberately emits no temporal value, which is where it parts company with {@code SessionFacts}.</b> Every date
 * arrives here already rendered - "13 May", "Sunday 2 August", "April to June 2026" - rather than as an {@link Instant}
 * the template formats. Facts are frozen into the outbox as jsonb and read back through a type-erased
 * {@code Map<String, Object>}, so a real {@code Instant} comes back out as the plain string it was written as, and
 * {@code FactRehydration} repairs exactly two hand-named key paths, both of them top-level ones {@code SessionFacts}
 * owns. This message's dates are nested two levels down, inside a community card's session rows, where no repair
 * reaches them and where the template's {@code atZone} call would fail on every real send while every preview built
 * from live facts passed. Emitting strings makes the round trip lossless by construction rather than by a third repair
 * somebody has to remember to add, and {@code RoundupFactsTest} pins it with a walk of the whole map to any depth. That
 * is also why this class formats at all, which {@code SessionFacts} never does: a quarter has no instant to hand a
 * template in the first place.
 *
 * <p>
 * <b>Presenters are named in one rendering and initialled in the other, and the fact carries both.</b> The HTML shows a
 * community's presenters as overlapping monogram avatars with no names beside them - badges carry involvement rather
 * than attribution - while the {@code .txt} alternative has no avatars to show and names them in full, as it always
 * did. Each presenter is therefore emitted as a {@code name} AND an {@code initials}, from one list built once, so the
 * two renderings of the same mail cannot disagree about who presented: a reader forwarding the plain text to somebody
 * whose client stripped the HTML is looking at the same people. First-time contributors stay a count and are never
 * listed. Somebody who gave a talk already did so publicly, and the talk is attributed to them on a page anyone in the
 * organization can open, so naming them here discloses nothing new and is the recognition the product exists to give.
 * "X presented for the first time" is a different statement: it is about a person's trajectory rather than about their
 * work, and landing in a manager's inbox it reads as a performance signal, which is how voluntary participation
 * curdles. So presenters are a list and {@code firstTimeContributorCount} is an int, and there is deliberately no fact
 * from which the second could be turned back into names.
 *
 * <p>
 * <b>A community's presenters are capped at five with an honest remainder, reversing an earlier decision not to cap
 * them at all.</b> That earlier decision was made when a single flat {@code presenterNames} list was the letter's ONLY
 * recognition surface: capping it would have handed recognition to whoever sorted first alphabetically and an "and 40
 * others" to everybody else, in the one message the feature exists to give it in. The letter no longer works that way.
 * Recognition is now carried by the session rows, which print the talk titles themselves, and by the avatar row, which
 * answers "how many people were involved" and nothing finer. A sixth avatar is not a sixth name, it is a sixth 28px
 * circle in a header row that has to stay one line at phone width, so the cap costs nobody their mention and buys the
 * card its shape. The remainder is stated rather than swallowed, the way every other cap here is.
 *
 * <p>
 * Every figure here is a fact rather than an interpretation: no health dial, no diagnosis, no comparison with the
 * quarter before. A roundup reports what the organization did; deciding whether that was good is the reader's.
 *
 * <p>
 * <b>Most of the figures are community-scoped, two are organization-wide, and the copy must never make one a subset of
 * the other.</b> {@code sessionCount}, {@code talkCount}, {@code presenterCount}, {@code resourceCount},
 * {@code communities} and {@code moreCommunitiesCount} are all derived from the sessions whose community
 * {@code CommunityActivity#activeCommunities} still names - a session held in a community archived since is dropped
 * from every one of them, because counting it and naming nothing leaves a reader with a total no listed community
 * accounts for, and the alternative, naming the closed community, invites people into a room that no longer exists.
 * Exactly two are NOT scoped that way: {@code firstTimeContributorCount} and {@code openTalkCount} come straight from
 * ports that answer for the whole organization with no community predicate at all
 * ({@code SessionActivity#firstTimePresenterCount} says so explicitly, and deliberately, since "never presented before"
 * is an organization-wide question).
 *
 * <p>
 * That difference is why the first-timer figure is worded as a standalone fact ("N people presented for the first
 * time") rather than as a share of the presenter count ("N of them"). A community archived between the quarter closing
 * and the mail draining, or a presenter erased in that same window, shrinks {@code presenterCount} while leaving
 * {@code firstTimeContributorCount} untouched, and a subset claim would then be arithmetic nonsense in an
 * organization-wide mail. Clamping the smaller figure to the larger one would only replace a visible contradiction with
 * an invented number - the surviving presenter may well be a returning speaker - so the fix is the sentence, not the
 * arithmetic. Both stay facts, as every figure here must be.
 *
 * <p>
 * There is one point past which that wording is not enough, and the templates handle it rather than this class: <b>when
 * {@code sessionCount} is 0 the first-timer sentence is suppressed outright.</b> Being unfiltered, the figure survives
 * a filter that removed every session the letter could name. {@code RoundupProducer}'s activity floor now refuses every
 * such quarter, so no SENT letter reaches that state any more - but {@code RoundupPreviewer} deliberately applies no
 * floor, because an administrator deciding whether to switch the roundup on is entitled to see the empty letter it
 * would have produced. "3 people presented for the first time." printed under "No sessions were held over the quarter."
 * is not a pair a reader can reconcile in a preview either. The fact itself stays in this map, honest and unchanged;
 * what the letter declines to do is state it where nothing it says can account for it.
 */
public final class RoundupFacts {

    /**
     * The sender monogram's two hexes. Fixed rather than resolved from an accent, because an organization has none to
     * resolve: {@code Organization#accentColor} is synced from a hub community's look and feel and the roundup is
     * enterprise-only, so it is null for every sender this ever renders. The pair is the shell's own neutral chip
     * surface and its body ink, so the organization's monogram sits on the card as a calm mark rather than borrowing
     * the brand tint the shell reserves for the one block that is asking for something.
     */
    private static final String SENDER_TINT_BACKGROUND = "#f6f0ec";
    private static final String SENDER_TINT_FOREGROUND = "#211a26";

    /**
     * The shell's existing trio, and the same three a community's own avatars are tinted from elsewhere in the app. A
     * presenter's colour is {@code Math.floorMod(name.hashCode(), 3)}, which is stable across JVMs and across sends
     * because {@code String#hashCode} is specified rather than implementation-defined. That stability is the whole
     * point: one person appears in as many cards as they presented in, and a colour that changed between two of them
     * would read as two different people.
     */
    private static final List<String> AVATAR_TINTS = List.of("#de3b78", "#6e3ceb", "#1f9d63");

    /**
     * Five avatars per community, and the sixth onwards is a count. See the class javadoc for why this cap exists and
     * why the uncapped list it replaces was right at the time.
     */
    private static final int PRESENTER_CAP = 5;

    private static final int MONTHS_PER_QUARTER = 3;

    /**
     * The topic cloud's five bands and the four ratios they switch at, taken verbatim from the Expertise page's
     * {@code TagConstellation}: {@code >= 0.8} is the heaviest, then 0.6, 0.4, 0.2, and everything below is the
     * lightest. {@link #MIDDLE_WEIGHT} is also the band every tag lands in when they all tied, which is what
     * {@code TagConstellation} answers too by ratioing a flat cloud to 0.5.
     */
    private static final int LIGHTEST_WEIGHT = 1;
    private static final int MIDDLE_WEIGHT = 3;
    private static final List<Double> WEIGHT_THRESHOLDS = List.of(0.2, 0.4, 0.6, 0.8);

    /** Named because {@link #isBelowActivityFloor} reads back exactly what {@link #of} wrote under it. */
    private static final String SESSION_COUNT = "sessionCount";

    /**
     * UTC and English, matching {@code _shell.html}'s own date line to the letter, and for {@link Quarter}'s reason: a
     * roundup is assembled once for a whole tenant and lands in inboxes spread across as many zones as it has offices,
     * so one fixed offset is the only way the same session reads as the same moment for everybody it reaches.
     *
     * <p>
     * Neither carries the year, which is not an omission. The headline already says which quarter of which year the
     * letter is about, and the next-up line is at most three months past it, so a year on every session row would be
     * the same four characters repeated down the whole message.
     */
    private static final DateTimeFormatter DAY_IN_QUARTER = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter NEXT_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private RoundupFacts() {
    }

    public static Map<String, Object> of(QuarterActivity activity) {

        var reading = Reading.of(activity);
        var cards = communityCards(reading, activity.sessionsPerCommunityCap());

        var facts = new LinkedHashMap<String, Object>();
        facts.put("sender", sender(activity.organizationName()));
        facts.put("sessionTitle", heading(activity.quarter()));
        facts.put("periodLabel", periodLabel(activity.quarter()));
        facts.put(SESSION_COUNT, reading.held().size());
        facts.put("talkCount", talkCount(reading.held()));
        facts.put("presenterCount", presenterNames(reading.held()).size());
        facts.put("resourceCount", resourceCount(reading.captured()));
        facts.put("firstTimeContributorCount", activity.firstTimePresenterCount());
        facts.put("topics", topicCloud(reading, activity.topics(), activity.topicCap()));
        facts.put("communities", cards.stream().limit(activity.communityCap()).toList());
        facts.put("moreCommunitiesCount", Math.max(0, cards.size() - activity.communityCap()));
        facts.put("openTalkCount", activity.openTalkCount());
        facts.put("communitiesUrl", activity.communitiesUrl());
        return Collections.unmodifiableMap(facts);
    }

    /**
     * Whether the assembled letter would report nothing at all: not one session it can still name. This is the activity
     * floor {@code RoundupProducer} refuses to send below, and it is deliberately a question about the FACTS rather
     * than about the reading they were assembled from.
     *
     * <p>
     * {@link #of} drops every session whose community {@code activeCommunities} no longer names, so "the quarter held
     * something" and "the letter can say the quarter held something" are not the same question. A tenant that archives
     * the one community which held everything last quarter, before the produce tick fires, reads as busy at the port
     * and as empty here. A floor measured before that filter would mail every present employee a letter opening "No
     * sessions were held over the quarter." - an artefact the letter must not produce. Measured after it, the floor and
     * the message are one judgement rather than two that can disagree.
     *
     * <p>
     * <b>It used to be two clauses, no session AND nothing captured, and the second is now redundant rather than
     * dropped.</b> Capture is measured through the quarter's own held sessions - {@code ArtifactActivity} is asked
     * which artifacts hang off THESE sessions, not which rows were created inside the window - so {@code resourceCount}
     * cannot be anything but 0 when {@code sessionCount} is 0. Keeping the clause would state a condition that no
     * reading can satisfy. It also closes, rather than opens, the edge case the old wording had to hand-wave: a tenant
     * that archived the community holding everything used to slip past the floor on its captured count alone and be
     * mailed a letter that could name nothing.
     */
    public static boolean isBelowActivityFloor(Map<String, Object> facts) {

        // Safe by construction: of() is the only writer of this key, and writes an int under it.
        return (Integer) facts.get(SESSION_COUNT) == 0;
    }

    /**
     * The quarter as the letter can still describe it, and <b>the one place the active-communities filter is
     * applied</b>. {@code held} and {@code upcoming} keep only what a still-named community holds; {@code captured}
     * keeps only what hangs off a surviving held session, so the resource figures and the resource links can never
     * describe a session no card lists. Every method below reads from here rather than from {@link QuarterActivity},
     * which is what stops a second, subtly different filter growing beside the first.
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

    /**
     * The organization's own monogram, the same three-part shape a community's is - see the {@code sender} fact in
     * {@code templates/mail/_shell.html}, which renders either without knowing which it was handed.
     */
    private static Map<String, String> sender(String organizationName) {

        var fact = new LinkedHashMap<String, String>();
        fact.put("name", organizationName);
        fact.put("initials", Initials.of(organizationName));
        fact.put("tintBackground", SENDER_TINT_BACKGROUND);
        fact.put("tintForeground", SENDER_TINT_FOREGROUND);
        return Collections.unmodifiableMap(fact);
    }

    /**
     * The agenda's own size, across every section of it, presented or not. Separate from the presenter count and it
     * must stay separate: a talk with two presenters is one talk, and one person presenting twice is one person. The
     * number band states both, so deriving either from the other would print the same figure twice under two labels.
     */
    private static int talkCount(List<HeldSession> held) {

        return held.stream().mapToInt(HeldSession::talkCount).sum();
    }

    /**
     * Everybody who presented, once each, in alphabetical order. Alphabetical rather than by how often or how recently
     * they spoke: this is recognition, and any other order is a ranking nobody agreed to be in. It is also what decides
     * which five avatars a busy community shows, so the order has to be one nobody can read a judgement into.
     *
     * <p>
     * An erased member is dropped. {@code UserProfileAccess} is total and resolves an id with no row left behind it to
     * the neutral "Deleted member" label, which is the right answer on a session page - it explains a gap somebody is
     * looking straight at - and the wrong one in an organization-wide quarterly digest, where it names nobody, thanks
     * nobody, and broadcasts that somebody left to every employee. Dropping it here rather than counting it separately
     * is what keeps {@code presenterCount} and the avatar rows derived from one list, so the mail can never say
     * twenty-three people presented and then show twenty-two.
     *
     * <p>
     * A known and accepted imprecision: identity here is the DISPLAY NAME, because {@link HeldSession} carries resolved
     * names and no ids, so two colleagues who happen to share one collapse into a single entry and the count is short
     * by one. Closing it means widening the port to carry ids alongside names, which would put a raw identifier one
     * careless template expression away from an inbox - the thing that port exists to prevent - to sharpen a headline
     * number nobody audits.
     */
    private static List<String> presenterNames(List<HeldSession> held) {

        return held.stream().flatMap(session -> session.presenterNames().stream())
                .filter(name -> !DirectoryUser.DELETED_MEMBER_LABEL.equals(name)).distinct().sorted().toList();
    }

    /**
     * One resource counts once however many of the quarter's sessions it is attached to. {@code ArtifactActivity}
     * answers one row PER SESSION and says so, because the letter shows the same recording under both sessions it
     * documents, and a row carries no artifact id to dedupe on. Identity here is therefore {@code (title, url)}: a
     * record's generated {@code equals} includes the session id, so {@code distinct()} on the rows themselves would
     * count the recording twice. A careful reader who counts the links in the letter and gets one more than the band
     * says is reading it right; that is accepted rather than papered over.
     */
    private static int resourceCount(List<CapturedArtifact> captured) {

        return (int) captured.stream().map(artifact -> Arrays.asList(artifact.title(), artifact.url())).distinct()
                .count();
    }

    /**
     * The quarter's tags as a weighted cloud, busiest first and alphabetical within a tie - the order the port answers
     * in, which is what makes the cap keep the tags the quarter was actually about.
     *
     * <p>
     * The cloud is dropped entirely when no held session survived the filter. {@link TagCount} is pre-aggregated across
     * the quarter's sessions and carries no session id, so unlike the artifacts it cannot be filtered row by row to the
     * sessions the letter still names; the only granularity available is all or nothing, and a cloud of what the
     * quarter was about, printed above a letter that reports no sessions at all, is exactly the contradiction the
     * first-timer suppression exists to prevent.
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

    /**
     * Where a tag sits between the quarter's least and most used one, in five bands. Relative to the tags SHOWN rather
     * than to every tag read, so the cloud always uses its whole scale: a cap that cut the long tail off would
     * otherwise leave every surviving tag bunched at the heavy end.
     *
     * <p>
     * When every tag tied, there is no scale to place them on and they all take the middle band. Sizing them all at the
     * top would claim the quarter was about all of them equally loudly, and all at the bottom would claim it was about
     * none of them; the middle says what is true, that they were used the same amount.
     *
     * <p>
     * <b>The band edges are {@code TagConstellation}'s, not a rounding of the ratio.</b> An earlier version placed a
     * tag with {@code 1 + round(ratio * 4)}, which normalises identically but switches bands at 0.125, 0.375, 0.625 and
     * 0.875. A tag at 0.85 was then the largest and pinkest word on the Expertise page and the second band, in body
     * ink, in the mail about the same quarter. It is one rule read twice, so the thresholds are stated here rather than
     * approximated.
     */
    private static int weightOf(int count, int min, int max) {

        if (max == min) {
            return MIDDLE_WEIGHT;
        }
        var ratio = (double) (count - min) / (max - min);
        return LIGHTEST_WEIGHT + (int) WEIGHT_THRESHOLDS.stream().filter(threshold -> ratio >= threshold).count();
    }

    /**
     * One card per community that held something in the quarter, busiest first and alphabetical within a tie. A
     * community that held nothing is not active in the quarter the roundup is about, however live it still is, so it is
     * absent from the list and from the remainder count alike - and so, with it, is the "next up" line it would have
     * carried. That last consequence is recorded rather than solved here.
     */
    private static List<Map<String, Object>> communityCards(Reading reading, int sessionCap) {

        return reading.held().stream().collect(Collectors.groupingBy(HeldSession::communityId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, Long> entry) -> entry.getValue()).reversed()
                        .thenComparing(entry -> reading.communities().get(entry.getKey()).name()))
                .map(entry -> communityCard(reading, entry.getKey(), sessionCap)).toList();
    }

    /**
     * A community's whole share of the letter: who it is, what it did, and what it does next. The past and the future
     * sit on one card because a reader should meet a community's name once rather than twice three hundred pixels
     * apart, which is what folding the old flat "what is coming" list into the cards bought.
     *
     * <p>
     * <b>The next session is the FIRST of the community's upcoming rows, and that rests on the port's ordering rather
     * than on any sort here.</b> {@code SessionActivity#scheduledAfter} answers soonest first, so a community's next
     * session is the first of its rows to arrive and every later one is by definition not next - which is also what
     * makes {@code moreUpcomingCount} a count of what comes AFTER the one named rather than an arbitrary remainder. If
     * that port ever stopped answering in order, this line would quietly start naming the wrong session.
     *
     * <p>
     * {@code badgeUrl} and {@code accentColor} are passed through as they arrive, nulls included: a community that
     * never uploaded a badge or chose a colour is the common case, and the shell already knows how to fall back to an
     * initials monogram on its neutral pair. Deciding the fallback here would put a hex in the facts that the shell
     * would then have to tell apart from a real one. The URL is already resolved when it gets here - see
     * {@code RoundupFactsAssembler#named} for why the storage key is translated before this class ever sees it.
     */
    private static Map<String, Object> communityCard(Reading reading, long communityId, int sessionCap) {

        var community = reading.communities().get(communityId);
        var sessions = reading.held().stream().filter(session -> session.communityId() == communityId).toList();
        var presenters = presenterNames(sessions);
        var next = reading.upcoming().stream().filter(session -> session.communityId() == communityId).toList();

        var card = new LinkedHashMap<String, Object>();
        card.put("name", community.name());
        card.put("initials", Initials.of(community.name()));
        card.put("badgeUrl", community.badgeUrl());
        card.put("accentColor", community.accentColor());
        card.put("summaryParts", summaryParts(sessions, resourceCount(capturedFor(reading, sessions))));
        card.put("presenters", presenters.stream().limit(PRESENTER_CAP).map(RoundupFacts::presenter).toList());
        card.put("morePresenterCount", Math.max(0, presenters.size() - PRESENTER_CAP));
        card.put("sessions", sessions.stream().limit(sessionCap).map(session -> sessionRow(reading, session)).toList());
        card.put("moreSessionCount", Math.max(0, sessions.size() - sessionCap));
        card.put("nextTitle", next.stream().findFirst().map(UpcomingSession::title).orElse(null));
        card.put("nextLabel",
                next.stream().findFirst().map(session -> NEXT_DAY.format(session.scheduledAt())).orElse(null));
        card.put("moreUpcomingCount", Math.max(0, next.size() - 1));
        return Collections.unmodifiableMap(card);
    }

    /**
     * The card's meta line as its PARTS - "2 sessions", "4 talks", "3 resources" - and the only place the
     * community-scoped figures are stated at all. A part whose count is zero is left out rather than printed as a zero:
     * a community that held two sessions and captured nothing has nothing to say about resources, and "0 resources"
     * says it in the most discouraging way available. Every part carries its singular arm, because a tenant's first
     * quarter is routinely one session with one talk and "1 sessions" is the sentence that says nobody read this before
     * sending it.
     *
     * <p>
     * <b>A list, not a joined string, because the two renderings join it differently.</b> The HTML meta line reads "2
     * sessions &middot; 4 talks &middot; 3 resources", which is the separator the shell already uses between a
     * presenter and a duration; the {@code .txt} joins with a comma, because a middot in plain text is a character a
     * reader has to decode rather than read. Joining here would have forced one of the two to be wrong, and the comma
     * is also genuinely ambiguous the moment a figure's noun could contain one.
     */
    private static List<String> summaryParts(List<HeldSession> sessions, int resourceCount) {

        return Stream.of(part(sessions.size(), "session", "sessions"), part(talkCount(sessions), "talk", "talks"),
                part(resourceCount, "resource", "resources")).filter(Objects::nonNull).toList();
    }

    private static String part(int count, String singular, String plural) {

        return count == 0 ? null : count + " " + (count == 1 ? singular : plural);
    }

    private static Map<String, String> presenter(String name) {

        var fact = new LinkedHashMap<String, String>();
        fact.put("name", name);
        fact.put("initials", Initials.of(name));
        fact.put("tint", AVATAR_TINTS.get(Math.floorMod(name.hashCode(), AVATAR_TINTS.size())));
        return Collections.unmodifiableMap(fact);
    }

    private static List<CapturedArtifact> capturedFor(Reading reading, List<HeldSession> sessions) {

        var sessionIds = sessions.stream().map(HeldSession::sessionId).collect(Collectors.toSet());
        return reading.captured().stream().filter(artifact -> sessionIds.contains(artifact.sessionId())).toList();
    }

    /**
     * One session the community held, in the order the port answers in, which is chronological. The fixed four-part
     * shape is what makes a card scannable: the date is its own column, so every "when" lines up down the whole letter.
     *
     * <p>
     * {@code title} stays null for a series occurrence nobody named, and {@code talkTitles} stays EMPTY when the agenda
     * carried slots but titled none of them. Both are rendered by being absent rather than by a placeholder word: a row
     * reading "Untitled talk" tells a reader strictly less than the session's own heading already did, and
     * {@code talkTitles} is deliberately shorter than {@code talkCount} for exactly that reason - the slot is counted
     * in the band and not named here.
     *
     * <p>
     * The titles stay a LIST here for the reason {@link #summaryParts} does: the HTML joins them with a middot and the
     * {@code .txt} with a comma, so a joiner in this class would have had to be wrong in one of the two renderings. An
     * empty list rather than a null also means each template asks the same emptiness question of this key that it asks
     * of every other collection it renders.
     */
    private static Map<String, Object> sessionRow(Reading reading, HeldSession session) {

        var row = new LinkedHashMap<String, Object>();
        row.put("dateLabel", DAY_IN_QUARTER.format(session.heldAt()));
        row.put("title", session.title());
        row.put("talkTitles", session.talkTitles());
        row.put("resources", reading.captured().stream().filter(artifact -> artifact.sessionId() == session.sessionId())
                .map(RoundupFacts::resource).toList());
        return Collections.unmodifiableMap(row);
    }

    /**
     * A resource as a row may print it: what it is called and where it lives. The type label the port also answers is
     * deliberately dropped - "Recording" beside a link titled "Recording of the May session" is the same word twice -
     * and the session id never reaches a template.
     */
    private static Map<String, String> resource(CapturedArtifact artifact) {

        var fact = new LinkedHashMap<String, String>();
        fact.put("title", artifact.title());
        fact.put("url", artifact.url());
        return Collections.unmodifiableMap(fact);
    }

    /** The card's headline. "Q2 2026" rather than the outbox's own "2026-Q2", which is a key, not a sentence. */
    private static String heading(Quarter quarter) {

        return "Q" + quarter.index() + " " + quarter.year();
    }

    /** "April to June 2026" - the months a reader recognises, since "Q2" alone means four things to four people. */
    private static String periodLabel(Quarter quarter) {

        var firstMonth = quarter.index() * MONTHS_PER_QUARTER - (MONTHS_PER_QUARTER - 1);
        return monthName(firstMonth) + " to " + monthName(firstMonth + MONTHS_PER_QUARTER - 1) + " " + quarter.year();
    }

    private static String monthName(int month) {

        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
