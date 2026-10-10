package dev.axonia.events.adapter.in.web;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.application.CreateSeriesRequest;
import dev.axonia.events.application.SeriesService;
import dev.axonia.events.application.UpdateSeriesRequest;
import dev.axonia.events.domain.Series;
import dev.axonia.shared.api.web.Pageables;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;

    @GetMapping("/communities/{slug}/series")
    Page<Series> list(@PathVariable String slug,
            @PageableDefault(size = 20, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {

        return seriesService.list(slug, Pageables.requireSortIn(pageable, Set.of("created_at")));
    }

    @PostMapping("/communities/{slug}/series")
    @ResponseStatus(HttpStatus.CREATED)
    Series start(@PathVariable String slug, @Valid @RequestBody CreateSeriesRequest request) {

        return seriesService.start(slug, request);
    }

    @GetMapping("/series/{id}")
    Series get(@PathVariable Long id) {

        return seriesService.get(id);
    }

    @PutMapping("/series/{id}")
    Series update(@PathVariable Long id, @Valid @RequestBody UpdateSeriesRequest request) {

        return seriesService.update(id, request);
    }

    @PostMapping("/series/{id}/pause")
    Series pause(@PathVariable Long id) {

        return seriesService.pause(id);
    }

    @PostMapping("/series/{id}/resume")
    Series resume(@PathVariable Long id) {

        return seriesService.resume(id);
    }

    @PostMapping("/series/{id}/end")
    Series end(@PathVariable Long id) {

        return seriesService.end(id);
    }
}
