package dev.axonia.expertise.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.expertise.application.NamedAreaCurationService;
import dev.axonia.expertise.domain.NamedArea;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/communities/{slug}/expertise/curation/named-areas")
@RequiredArgsConstructor
class NamedAreaController {

    private final NamedAreaCurationService namedAreaCurationService;

    @GetMapping
    List<NamedArea> namedAreas(@PathVariable String slug) {

        return namedAreaCurationService.namedAreas(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NamedArea create(@PathVariable String slug, @Valid @RequestBody NameRequest request) {

        return namedAreaCurationService.createNamedArea(slug, request.name());
    }

    @PostMapping("/{id}/rename")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rename(@PathVariable String slug, @PathVariable long id, @Valid @RequestBody NameRequest request) {

        namedAreaCurationService.renameNamedArea(slug, id, request.name());
    }

    @PostMapping("/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String slug, @PathVariable long id) {

        namedAreaCurationService.deleteNamedArea(slug, id);
    }

    @PostMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void place(@PathVariable String slug, @PathVariable long id, @Valid @RequestBody PlaceRequest request) {

        namedAreaCurationService.placeInNamedArea(slug, id, request.tag());
    }

    @PostMapping("/tags/{tag}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String slug, @PathVariable String tag) {

        namedAreaCurationService.removeFromNamedArea(slug, tag);
    }

    /** Blank and length are the service's rules, so the refusal carries their codes. */
    record NameRequest(@NotNull String name) {
    }

    record PlaceRequest(@NotBlank String tag) {
    }
}
