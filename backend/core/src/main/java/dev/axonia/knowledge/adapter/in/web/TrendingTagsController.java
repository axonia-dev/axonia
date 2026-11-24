package dev.axonia.knowledge.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.knowledge.application.TrendingTagsService;
import dev.axonia.knowledge.domain.TagCount;
import lombok.RequiredArgsConstructor;

/**
 * Its own controller rather than another method on {@link ArtifactController}: a new constructor dependency there
 * breaks every other {@code @WebMvcTest} slice that lists that controller.
 */
@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class TrendingTagsController {

    private final TrendingTagsService trendingTagsService;

    @GetMapping("/communities/{slug}/artifacts/trending-tags")
    List<TagCount> trendingTags(@PathVariable String slug) {

        return trendingTagsService.trendingTags(slug);
    }
}
