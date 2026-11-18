package dev.axonia.community.adapter.in.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.community.api.Lobby;
import dev.axonia.community.api.LobbyItem;
import dev.axonia.community.api.LobbyShelfKey;
import dev.axonia.community.application.LobbyService;
import lombok.RequiredArgsConstructor;

/** The communities lobby. It lives under {@code /lobby}, so no literal path can shadow a community slug. */
@RestController
@RequestMapping("/${spring.application.name}/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private static final int SEARCH_PAGE_SIZE = 20;

    private final LobbyService lobbyService;

    @GetMapping
    Lobby lobby() {

        return lobbyService.lobby();
    }

    @GetMapping("/shelves/{key}")
    Page<LobbyItem> shelf(@PathVariable LobbyShelfKey key, @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {

        var requested = size == null ? LobbyService.pageSize(key) : size;
        return lobbyService.shelf(key, pageRequest(page, requested));
    }

    @GetMapping("/search")
    Page<LobbyItem> search(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + SEARCH_PAGE_SIZE) int size) {

        return lobbyService.search(q, pageRequest(page, size));
    }

    private static PageRequest pageRequest(int page, int size) {

        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, LobbyService.MAX_PAGE_SIZE));
    }
}
