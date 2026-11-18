package dev.axonia.community.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.community.api.CommunityListing;
import dev.axonia.community.api.CommunityMother;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.Lobby;
import dev.axonia.community.api.LobbyItem;
import dev.axonia.community.api.LobbyLately;
import dev.axonia.community.api.LobbySession;
import dev.axonia.community.api.LobbyShelf;
import dev.axonia.community.api.LobbyShelfKey;
import dev.axonia.community.api.LobbyStats;
import dev.axonia.community.application.LobbyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = LobbyController.class)
class LobbyControllerTest extends ControllerTestSupport {

    private static final LobbyItem ITEM = LobbyItem.builder()
            .listing(new CommunityListing(CommunityMother.any().build(), CommunityRole.MEMBER))
            .nextSession(new LobbySession("Kickoff", Instant.parse("2026-11-19T17:00:00Z"), true, "Europe/Zurich"))
            .nextSessionThisWeek(true).joinedLast30Days(3).faces(List.of("u1", "u2")).stats(new LobbyStats(4, 2))
            .lately(new LobbyLately("Style guide", null)).build();

    @MockitoBean
    private LobbyService lobbyService;

    @Test
    void lobby() {

        // given
        when(lobbyService.lobby()).thenReturn(
                new Lobby(List.of(ITEM), List.of(new LobbyShelf(LobbyShelfKey.JOINED, List.of(ITEM), true))));

        // when
        var actual = mvc.get().uri("/axonia/lobby").exchange();

        // then
        assertThat(actual).hasStatusOk();
        var json = assertThat(actual).bodyJson();
        json.extractingPath("$.billboard[0].slug").isEqualTo("backend-guild");
        json.extractingPath("$.billboard[0].name").isEqualTo("Backend Guild");
        json.extractingPath("$.billboard[0].memberCount").isEqualTo(1);
        json.extractingPath("$.billboard[0].role").isEqualTo("MEMBER");
        json.extractingPath("$.billboard[0].nextSession.title").isEqualTo("Kickoff");
        json.extractingPath("$.billboard[0].nextSession.scheduledAt").isEqualTo("2026-11-19T17:00:00Z");
        json.extractingPath("$.billboard[0].nextSession.hasTime").isEqualTo(true);
        json.extractingPath("$.billboard[0].nextSession.zone").isEqualTo("Europe/Zurich");
        json.extractingPath("$.billboard[0].nextSessionThisWeek").isEqualTo(true);
        json.extractingPath("$.billboard[0].latestSession").isNull();
        json.extractingPath("$.billboard[0].joinedLast30Days").isEqualTo(3);
        json.extractingPath("$.billboard[0].faces").isEqualTo(List.of("u1", "u2"));
        json.extractingPath("$.billboard[0].stats.sessionsHeld").isEqualTo(4);
        json.extractingPath("$.billboard[0].stats.artifactsShared").isEqualTo(2);
        json.extractingPath("$.billboard[0].lately.artifactTitle").isEqualTo("Style guide");
        json.extractingPath("$.billboard[0].lately.topicTitle").isNull();
        json.doesNotHavePath("$.billboard[0].listing");
        json.doesNotHavePath("$.billboard[0].community");
        json.extractingPath("$.shelves[0].key").isEqualTo("JOINED");
        json.extractingPath("$.shelves[0].hasMore").isEqualTo(true);
        json.extractingPath("$.shelves[0].items[0].slug").isEqualTo("backend-guild");
    }

    @Test
    void shelfDefaultsItsSizeToTheShelf() {

        // given
        when(lobbyService.shelf(any(), any())).thenReturn(new PageImpl<>(List.of(ITEM), PageRequest.of(1, 8), 9));

        // when
        var actual = mvc.get().uri("/axonia/lobby/shelves/JOINED").queryParam("page", "1").exchange();

        // then
        assertThat(actual).hasStatusOk().bodyJson().extractingPath("$.content[0].slug").isEqualTo("backend-guild");
        verify(lobbyService).shelf(LobbyShelfKey.JOINED, PageRequest.of(1, 8));
    }

    @Test
    void gridShelvesDefaultToTwenty() {

        // given
        when(lobbyService.shelf(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when
        var actual = mvc.get().uri("/axonia/lobby/shelves/OTHERS").exchange();

        // then
        assertThat(actual).hasStatusOk();
        verify(lobbyService).shelf(LobbyShelfKey.OTHERS, PageRequest.of(0, 20));
    }

    @ParameterizedTest
    @CsvSource({"JOINED, 8", "THIS_WEEK, 8", "GROWING, 8", "OTHERS, 20", "EXPLORE, 20"})
    void everyShelfHasItsDefaultSize(LobbyShelfKey key, int size) {

        // given
        when(lobbyService.shelf(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, size), 0));

        // when
        var actual = mvc.get().uri("/axonia/lobby/shelves/{key}", key).exchange();

        // then
        assertThat(actual).hasStatusOk();
        verify(lobbyService).shelf(key, PageRequest.of(0, size));
    }

    @Test
    void unknownShelfKeyIs400() {

        // when
        var actual = mvc.get().uri("/axonia/lobby/shelves/SOMEWHERE").exchange();

        // then
        assertThat(actual).hasStatus(400);
        verifyNoInteractions(lobbyService);
    }

    @Test
    void sizeAbove50IsCapped() {

        // given
        when(lobbyService.shelf(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        // when
        var actual = mvc.get().uri("/axonia/lobby/shelves/OTHERS").queryParam("size", "500").exchange();

        // then
        assertThat(actual).hasStatusOk();
        verify(lobbyService).shelf(LobbyShelfKey.OTHERS, PageRequest.of(0, 50));
    }

    @Test
    void negativePageAndSizeAreClamped() {

        // given
        when(lobbyService.shelf(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        // when
        var actual = mvc.get().uri("/axonia/lobby/shelves/OTHERS").queryParam("page", "-3").queryParam("size", "0")
                .exchange();

        // then
        assertThat(actual).hasStatusOk();
        verify(lobbyService).shelf(LobbyShelfKey.OTHERS, PageRequest.of(0, 1));
    }

    @Test
    void search() {

        // given
        when(lobbyService.search(any(), any())).thenReturn(new PageImpl<>(List.of(ITEM), PageRequest.of(0, 20), 1));

        // when
        var actual = mvc.get().uri("/axonia/lobby/search").queryParam("q", "guild").exchange();

        // then
        assertThat(actual).hasStatusOk().bodyJson().extractingPath("$.content[0].role").isEqualTo("MEMBER");
        verify(lobbyService).search(eq("guild"), eq(PageRequest.of(0, 20)));
    }

    @Test
    void searchSizeIsCapped() {

        // given
        when(lobbyService.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        // when
        var actual = mvc.get().uri("/axonia/lobby/search").queryParam("page", "2").queryParam("size", "99").exchange();

        // then
        assertThat(actual).hasStatusOk();
        verify(lobbyService).search(null, PageRequest.of(2, 50));
    }
}
