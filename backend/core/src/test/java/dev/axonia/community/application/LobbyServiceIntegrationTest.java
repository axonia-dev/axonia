package dev.axonia.community.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.adapter.out.persistence.CommunityKindRepository;
import dev.axonia.community.adapter.out.persistence.CommunityRepository;
import dev.axonia.community.adapter.out.persistence.JdbcCommunityKindStore;
import dev.axonia.community.adapter.out.persistence.JdbcCommunityStore;
import dev.axonia.community.adapter.out.persistence.JdbcMembershipLedger;
import dev.axonia.community.adapter.out.persistence.MembershipRepository;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.Lobby;
import dev.axonia.community.api.LobbyItem;
import dev.axonia.community.api.LobbyLately;
import dev.axonia.community.api.LobbySession;
import dev.axonia.community.api.LobbyShelf;
import dev.axonia.community.api.LobbyShelfKey;
import dev.axonia.community.api.LobbyStats;
import dev.axonia.community.api.spi.LibraryShowcase;
import dev.axonia.community.api.spi.SessionShowcase;
import dev.axonia.media.api.MediaLinks;

import static org.assertj.core.api.Assertions.assertThat;

class LobbyServiceIntegrationTest extends CoreJdbcTestSupport {

    private static final String ORG = "lobby";
    private static final String OTHER_ORG = "lobby-other";
    private static final String CALLER = "lobby-caller";
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private final StubSessions sessions = new StubSessions();
    private final StubLibrary library = new StubLibrary();

    private LobbyService sut;

    @BeforeEach
    void setUp() {

        kind(ORG);
        kind(OTHER_ORG);
        sut = lobbyService(List.of(sessions), List.of(library));
    }

    @Test
    void lobbyReturnsBillboardAndShelvesWithSignals() {

        // given
        var atlas = community("Atlas");
        member(atlas, CALLER, CommunityRole.ORGANIZER, NOW.minus(Duration.ofDays(90)));
        var borealis = community("Borealis");
        member(borealis, "u0", CommunityRole.MEMBER, NOW.minus(Duration.ofDays(60)));
        member(borealis, "u3", CommunityRole.MEMBER, NOW.minus(Duration.ofDays(3)));
        member(borealis, "u1", CommunityRole.MEMBER, NOW.minus(Duration.ofDays(1)));
        member(borealis, "u2", CommunityRole.MEMBER, NOW.minus(Duration.ofDays(2)));
        var cosmos = community("Cosmos");
        IntStream.rangeClosed(1, 13).forEach(
                i -> member(cosmos, "c" + i, CommunityRole.MEMBER, NOW.minus(Duration.ofDays(40)).plusSeconds(i)));
        var delta = community("Delta");
        var echo = community("Echo");
        var foxtrot = community("Foxtrot");
        var gamma = community("Gamma");
        var kickoff = new LobbySession("Kickoff", NOW.plus(Duration.ofDays(2)), true, "UTC");
        var retro = new LobbySession("Retro", NOW.minus(Duration.ofDays(7)), true, "UTC");
        sessions.next.put(borealis, kickoff.scheduledAt());
        sessions.signals.put(borealis, new SessionShowcase.SessionSignals(kickoff, retro, 4, "Virtual threads"));
        library.signals.put(borealis, new LibraryShowcase.LibrarySignals(2, "Style guide"));

        // when
        var actual = sut.lobby();

        // then
        assertThat(ids(actual.billboard())).containsExactly(borealis, cosmos, delta, echo, foxtrot);
        assertThat(actual.shelves()).extracting(LobbyShelf::key).containsExactly(LobbyShelfKey.JOINED,
                LobbyShelfKey.THIS_WEEK, LobbyShelfKey.OTHERS);
        assertThat(ids(shelf(actual, LobbyShelfKey.JOINED).items())).containsExactly(atlas);
        assertThat(ids(shelf(actual, LobbyShelfKey.THIS_WEEK).items())).containsExactly(borealis);
        assertThat(ids(shelf(actual, LobbyShelfKey.OTHERS).items())).containsExactly(cosmos, delta, echo, foxtrot,
                gamma);
        assertThat(actual.shelves()).extracting(LobbyShelf::hasMore).containsOnly(false);

        var featured = actual.billboard().getFirst();
        assertThat(featured.listing().community().name()).isEqualTo("Borealis");
        assertThat(featured.listing().community().kind()).isEqualTo("Guild");
        assertThat(featured.listing().community().memberCount()).isEqualTo(4L);
        assertThat(featured.listing().role()).isNull();
        assertThat(featured.nextSession()).isEqualTo(kickoff);
        assertThat(featured.nextSessionThisWeek()).isTrue();
        assertThat(featured.latestSession()).isEqualTo(retro);
        assertThat(featured.joinedLast30Days()).isEqualTo(3);
        assertThat(featured.faces()).containsExactly("u1", "u2", "u3", "u0");
        assertThat(featured.stats()).isEqualTo(new LobbyStats(4, 2));
        assertThat(featured.lately()).isEqualTo(new LobbyLately("Style guide", "Virtual threads"));

        assertThat(actual.billboard().get(1).faces()).hasSize(12).startsWith("c13", "c12").doesNotContain("c1");
        assertThat(shelf(actual, LobbyShelfKey.JOINED).items().getFirst().listing().role())
                .isEqualTo(CommunityRole.ORGANIZER);
    }

    @Test
    void signalsAreFetchedForReturnedItemsOnly() {

        // given
        var plain = plainCommunities(30);
        var firstTwenty = Set.copyOf(plain.subList(0, 20));

        // when
        var actual = sut.lobby();

        // then
        assertThat(shelf(actual, LobbyShelfKey.OTHERS).items()).hasSize(20);
        assertThat(shelf(actual, LobbyShelfKey.OTHERS).hasMore()).isTrue();
        assertThat(sessions.nextAskedFor).containsExactly(Set.copyOf(plain));
        assertThat(sessions.signalsAskedFor).singleElement().isEqualTo(firstTwenty);
        assertThat(library.askedFor).singleElement().isEqualTo(firstTwenty);
    }

    @Test
    void emptyCommunityHasNullsAndZeros() {

        // given
        var quiet = community("Quiet");
        member(quiet, "lonely", CommunityRole.ORGANIZER, NOW.minus(Duration.ofDays(60)));
        var withoutContributors = lobbyService(List.of(), List.of());

        // when
        var actual = withoutContributors.lobby();

        // then
        assertThat(actual.billboard()).singleElement().satisfies(item -> {
            assertThat(item.nextSession()).isNull();
            assertThat(item.nextSessionThisWeek()).isFalse();
            assertThat(item.latestSession()).isNull();
            assertThat(item.joinedLast30Days()).isZero();
            assertThat(item.faces()).containsExactly("lonely");
            assertThat(item.stats()).isEqualTo(new LobbyStats(0, 0));
            assertThat(item.lately()).isEqualTo(new LobbyLately(null, null));
        });
        assertThat(actual.shelves()).extracting(LobbyShelf::key).containsExactly(LobbyShelfKey.EXPLORE);
    }

    @Test
    void contributorSilentOnACommunityLeavesItEmpty() {

        // given
        community("Quiet");

        // when
        var actual = sut.lobby();

        // then
        assertThat(actual.billboard()).singleElement().satisfies(item -> {
            assertThat(item.nextSession()).isNull();
            assertThat(item.stats()).isEqualTo(new LobbyStats(0, 0));
            assertThat(item.lately()).isEqualTo(new LobbyLately(null, null));
            assertThat(item.faces()).isEmpty();
        });
    }

    @Test
    void organizationWithoutCommunitiesIsAnEmptyLobby() {

        // when
        var actual = sut.lobby();

        // then
        assertThat(actual.billboard()).isEmpty();
        assertThat(actual.shelves()).isEmpty();
        assertThat(sessions.nextAskedFor).isEmpty();
        assertThat(sessions.signalsAskedFor).isEmpty();
    }

    @Test
    void archivedCommunitiesNeverAppear() {

        // given
        var open = community("Open");
        var hidden = community("Hidden", ORG, "ARCHIVED");
        member(hidden, "someone", CommunityRole.MEMBER, NOW.minus(Duration.ofDays(1)));

        // when
        var lobby = sut.lobby();
        var search = sut.search("", PageRequest.of(0, 20));

        // then
        assertThat(ids(lobby.billboard())).containsExactly(open);
        assertThat(lobby.shelves()).flatExtracting(LobbyShelf::items).extracting(this::id).containsExactly(open);
        assertThat(ids(search.getContent())).containsExactly(open);
    }

    @Test
    void shelfPageTwoFollowsPageOne() {

        // given
        var plain = plainCommunities(30);

        // when
        var first = sut.shelf(LobbyShelfKey.OTHERS, PageRequest.of(0, 8));
        var second = sut.shelf(LobbyShelfKey.OTHERS, PageRequest.of(1, 8));

        // then
        assertThat(ids(first.getContent())).containsExactlyElementsOf(plain.subList(0, 8));
        assertThat(ids(second.getContent())).containsExactlyElementsOf(plain.subList(8, 16));
        assertThat(second.getTotalElements()).isEqualTo(30);
    }

    @Test
    void pastTheEndIsEmpty() {

        // given
        plainCommunities(30);

        // when
        var actual = sut.shelf(LobbyShelfKey.OTHERS, PageRequest.of(10, 8));

        // then
        assertThat(actual.getContent()).isEmpty();
        assertThat(actual.getTotalElements()).isEqualTo(30);
    }

    @Test
    void absentShelfIsAnEmptyPage() {

        // given
        plainCommunities(30);

        // when
        var actual = sut.shelf(LobbyShelfKey.GROWING, PageRequest.of(0, 8));

        // then
        assertThat(actual.getContent()).isEmpty();
        assertThat(actual.getTotalElements()).isZero();
    }

    @Test
    void searchMatchesCaseInsensitivelyAcrossJoinedAndNot() {

        // given
        var joined = community("Guild Alpha");
        member(joined, CALLER, CommunityRole.MEMBER, NOW.minus(Duration.ofDays(5)));
        var notJoined = community("backend guild");
        community("Circle");

        // when
        var actual = sut.search("GUILD", PageRequest.of(0, 20));

        // then
        assertThat(ids(actual.getContent())).containsExactly(notJoined, joined);
        assertThat(actual.getContent()).extracting(item -> item.listing().role()).containsExactly(null,
                CommunityRole.MEMBER);
    }

    @Test
    void searchMatchesExactlyAsTheDirectoryDoes() {

        // given
        var percent = community("100% Club");
        community("1000 Club");

        // when
        var actual = sut.search("0%", PageRequest.of(0, 20));

        // then
        assertThat(ids(actual.getContent())).containsExactly(percent);
    }

    @Test
    void joinsStampedAfterNowAreNotGrowth() {

        // given
        var guild = community("Guild");
        member(guild, "early", CommunityRole.MEMBER, NOW.plus(Duration.ofHours(1)));

        // when
        var actual = sut.lobby();

        // then
        assertThat(actual.billboard()).singleElement().satisfies(item -> assertThat(item.joinedLast30Days()).isZero());
    }

    @Test
    void searchWithoutTextListsEveryCommunityByName() {

        // given
        var bravo = community("bravo");
        var alpha = community("Alpha");

        // when
        var actual = sut.search(null, PageRequest.of(0, 20));

        // then
        assertThat(ids(actual.getContent())).containsExactly(alpha, bravo);
    }

    @Test
    void anotherOrganizationsCommunitiesNeverAppear() {

        // given
        var ours = community("Ours");
        community("Theirs", OTHER_ORG, "ACTIVE");

        // when
        var lobby = sut.lobby();
        var search = sut.search("", PageRequest.of(0, 20));

        // then
        assertThat(ids(lobby.billboard())).containsExactly(ours);
        assertThat(ids(search.getContent())).containsExactly(ours);
    }

    private LobbyService lobbyService(List<SessionShowcase> sessionShowcases, List<LibraryShowcase> libraries) {

        return new LobbyService(new JdbcCommunityStore(communityRepository, jdbcAggregateTemplate),
                new JdbcMembershipLedger(membershipRepository), new JdbcCommunityKindStore(communityKindRepository),
                () -> CALLER, () -> ORG, new MediaLinks("https://media.test"), sessionShowcases, libraries,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private List<Long> plainCommunities(int count) {

        return IntStream.rangeClosed(1, count).mapToObj(i -> community("Plain %02d".formatted(i))).toList();
    }

    private static LobbyShelf shelf(Lobby lobby, LobbyShelfKey key) {

        return lobby.shelves().stream().filter(shelf -> shelf.key() == key).findFirst().orElseThrow();
    }

    private List<Long> ids(List<LobbyItem> items) {

        return items.stream().map(this::id).toList();
    }

    private Long id(LobbyItem item) {

        return item.listing().community().id();
    }

    private void kind(String organizationId) {

        jdbc.update("""
                INSERT INTO community_kind (organization_id, label, created_at, updated_at, version)
                VALUES (:org, 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("org", organizationId));
    }

    private long community(String name) {

        return community(name, ORG, "ACTIVE");
    }

    private long community(String name, String organizationId, String status) {

        return jdbc.queryForObject("""
                INSERT INTO communities (organization_id, slug, name, kind_id, description, status,
                        can_start_series, can_edit_structure, can_queue_topics, created_at, updated_at, version)
                VALUES (:org, :slug, :name,
                        (SELECT min(id) FROM community_kind WHERE organization_id = :org), 'd', :status,
                        false, false, false, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("org", organizationId).addValue("name", name)
                .addValue("slug", name.toLowerCase().replace(' ', '-')).addValue("status", status), Long.class);
    }

    private void member(long communityId, String userId, CommunityRole role, Instant joinedAt) {

        jdbc.update("""
                INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, version)
                VALUES ((SELECT organization_id FROM communities WHERE id = :communityId), :communityId, :userId,
                        :role, :joinedAt, now(), 0)
                """, new MapSqlParameterSource("communityId", communityId).addValue("userId", userId)
                .addValue("role", role.name()).addValue("joinedAt", Timestamp.from(joinedAt)));
    }

    private static final class StubSessions implements SessionShowcase {

        private final Map<Long, Instant> next = new HashMap<>();
        private final Map<Long, SessionSignals> signals = new HashMap<>();
        private final List<Set<Long>> nextAskedFor = new ArrayList<>();
        private final List<Set<Long>> signalsAskedFor = new ArrayList<>();

        @Override
        public Map<Long, Instant> nextSessionsAt(Set<Long> communityIds, Instant now) {

            nextAskedFor.add(new HashSet<>(communityIds));
            return only(next, communityIds);
        }

        @Override
        public Map<Long, SessionSignals> signals(Set<Long> communityIds, Instant now) {

            signalsAskedFor.add(new HashSet<>(communityIds));
            return only(signals, communityIds);
        }
    }

    private static final class StubLibrary implements LibraryShowcase {

        private final Map<Long, LibrarySignals> signals = new HashMap<>();
        private final List<Set<Long>> askedFor = new ArrayList<>();

        @Override
        public Map<Long, LibrarySignals> signals(Set<Long> communityIds) {

            askedFor.add(new HashSet<>(communityIds));
            return only(signals, communityIds);
        }
    }

    private static <V> Map<Long, V> only(Map<Long, V> answers, Set<Long> ids) {

        var result = new HashMap<Long, V>();
        Stream.of(ids.toArray(Long[]::new)).filter(answers::containsKey).forEach(id -> result.put(id, answers.get(id)));
        return result;
    }
}
