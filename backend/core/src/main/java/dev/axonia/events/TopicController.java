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

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping("/communities/{slug}/topics")
    Page<Topic> list(@PathVariable String slug, TopicQuery query,
            @PageableDefault(size = 20, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {

        var sortedPageable = Pageables.requireSortIn(pageable, Set.of("created_at"));
        if (query.phase() == TopicPhase.PAST) {
            return topicService.listPast(slug, query.q(), sortedPageable);
        }
        return topicService.list(slug, query, sortedPageable);
    }

    @GetMapping("/communities/{slug}/topics/counts")
    TopicCounts counts(@PathVariable String slug) {

        return topicService.counts(slug);
    }

    @PostMapping("/communities/{slug}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    Topic propose(@PathVariable String slug, @Valid @RequestBody ProposeTopicRequest request) {

        return topicService.propose(slug, request);
    }

    @GetMapping("/topics/{id}")
    Topic get(@PathVariable Long id) {

        return topicService.get(id);
    }

    @PutMapping("/topics/{id}")
    Topic update(@PathVariable Long id, @Valid @RequestBody UpdateTopicRequest request) {

        return topicService.update(id, request);
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable Long id) {

        topicService.remove(id);
    }

    @DeleteMapping("/topics/{id}/schedule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void returnToBacklog(@PathVariable Long id) {

        topicService.returnToBacklog(id);
    }

    @PostMapping("/topics/{id}/votes")
    @ResponseStatus(HttpStatus.CREATED)
    Topic vote(@PathVariable Long id) {

        return topicService.vote(id);
    }

    @DeleteMapping("/topics/{id}/votes/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unvote(@PathVariable Long id) {

        topicService.unvote(id);
    }
}
