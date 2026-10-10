package dev.axonia.expertise.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.expertise.application.ExpertiseMapService;
import dev.axonia.expertise.application.ExpertiseService;
import dev.axonia.expertise.application.MapContributions;
import dev.axonia.expertise.application.Practitioner;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.TopicMap;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
class ExpertiseController {

    private final ExpertiseService expertiseService;
    private final ExpertiseMapService expertiseMapService;

    @GetMapping("/communities/{slug}/expertise/search")
    List<Practitioner> search(@PathVariable String slug, @RequestParam String term,
            @RequestParam(defaultValue = "false") boolean demonstratedOnly) {

        return expertiseService.whoKnows(slug, term, demonstratedOnly);
    }

    @GetMapping("/communities/{slug}/expertise/areas")
    List<ExpertiseArea> areas(@PathVariable String slug, @RequestParam(required = false) String search) {

        return expertiseService.areas(slug, search);
    }

    @GetMapping("/communities/{slug}/expertise/suggestions")
    List<Practitioner> suggestions(@PathVariable String slug, @RequestParam(required = false) List<String> tags) {

        return expertiseService.suggestSpeakers(slug, tags == null ? List.of() : tags);
    }

    @GetMapping("/communities/{slug}/expertise/map")
    TopicMap map(@PathVariable String slug) {

        return expertiseMapService.topicMap(slug);
    }

    @GetMapping("/communities/{slug}/expertise/map/contributions")
    MapContributions mapContributions(@PathVariable String slug) {

        return expertiseMapService.contributions(slug);
    }
}
