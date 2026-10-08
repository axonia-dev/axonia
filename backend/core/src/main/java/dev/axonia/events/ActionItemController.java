package dev.axonia.events;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.Pageables;
import lombok.RequiredArgsConstructor;

/**
 * The action item aggregate's own HTTP adapter - kept off {@link CommunityController}, the same shape
 * {@link TopicController} already uses for topics.
 */
@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class ActionItemController {

    private final ActionItemService actionItemService;

    @GetMapping("/communities/{slug}/action-items")
    Page<ActionItem> list(@PathVariable String slug, ActionItemQuery query,
            @PageableDefault(size = 20, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {

        var sortedPageable = Pageables.requireSortIn(pageable, Set.of("created_at", "checkpoint", "updated_at"));
        return actionItemService.list(slug, query, sortedPageable);
    }

    @PostMapping("/communities/{slug}/action-items")
    @ResponseStatus(HttpStatus.CREATED)
    ActionItem raise(@PathVariable String slug, @Valid @RequestBody RaiseActionItemRequest request) {

        return actionItemService.raise(slug, request);
    }

    @PutMapping("/action-items/{id}")
    ActionItem update(@PathVariable Long id, @Valid @RequestBody UpdateActionItemRequest request) {

        return actionItemService.update(id, request);
    }

    @DeleteMapping("/action-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable Long id) {

        actionItemService.remove(id);
    }

    @PostMapping("/action-items/{id}/close")
    ActionItem close(@PathVariable Long id) {

        return actionItemService.close(id);
    }

    @PostMapping("/action-items/{id}/drop")
    ActionItem drop(@PathVariable Long id) {

        return actionItemService.drop(id);
    }

    @PostMapping("/action-items/{id}/reopen")
    ActionItem reopen(@PathVariable Long id) {

        return actionItemService.reopen(id);
    }
}
