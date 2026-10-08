package dev.axonia.expertise.internal;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.expertise.ExpertiseArea;
import dev.axonia.expertise.ExpertiseCurationService;
import dev.axonia.expertise.MergeSuggestion;
import dev.axonia.expertise.TagUsageReport;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
class ExpertiseCurationController {

    private final ExpertiseCurationService expertiseCurationService;

    @GetMapping("/communities/{slug}/expertise/curation/vocabulary")
    List<ExpertiseArea> vocabulary(@PathVariable String slug) {

        return expertiseCurationService.vocabulary(slug);
    }

    @GetMapping("/communities/{slug}/expertise/curation/suggestions")
    List<MergeSuggestion> suggestions(@PathVariable String slug) {

        return expertiseCurationService.suggestions(slug);
    }

    @PostMapping("/communities/{slug}/expertise/curation/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void merge(@PathVariable String slug, @Valid @RequestBody MergeRequest request) {

        expertiseCurationService.merge(slug, request.variant(), request.canonical());
    }

    @PostMapping("/communities/{slug}/expertise/curation/split")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void split(@PathVariable String slug, @Valid @RequestBody SplitRequest request) {

        expertiseCurationService.split(slug, request.variant());
    }

    @PostMapping("/communities/{slug}/expertise/curation/merge-batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mergeBatch(@PathVariable String slug, @Valid @RequestBody MergeBatchRequest request) {

        expertiseCurationService.mergeMany(slug, request.variants(), request.canonical());
    }

    @PostMapping("/communities/{slug}/expertise/curation/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable String slug, @Valid @RequestBody DismissRequest request) {

        expertiseCurationService.dismiss(slug, request.tagA(), request.tagB());
    }

    @PostMapping("/communities/{slug}/expertise/curation/undismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void undismiss(@PathVariable String slug, @Valid @RequestBody DismissRequest request) {

        expertiseCurationService.undismiss(slug, request.tagA(), request.tagB());
    }

    @GetMapping("/communities/{slug}/expertise/curation/tags/{tag}/usage")
    TagUsageReport usage(@PathVariable String slug, @PathVariable String tag) {

        return expertiseCurationService.usage(slug, tag);
    }

    @PostMapping("/communities/{slug}/expertise/curation/rename")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rename(@PathVariable String slug, @Valid @RequestBody RenameRequest request) {

        expertiseCurationService.rename(slug, request.from(), request.to());
    }

    @PostMapping("/communities/{slug}/expertise/curation/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String slug, @Valid @RequestBody DeleteTagRequest request) {

        expertiseCurationService.delete(slug, request.tag());
    }

    record MergeRequest(@NotBlank String variant, @NotBlank String canonical) {
    }

    record RenameRequest(@NotBlank String from, @NotBlank String to) {
    }

    record DeleteTagRequest(@NotBlank String tag) {
    }

    record SplitRequest(@NotBlank String variant) {
    }

    record MergeBatchRequest(@NotEmpty List<@NotBlank String> variants, @NotBlank String canonical) {
    }

    record DismissRequest(@NotBlank String tagA, @NotBlank String tagB) {
    }
}
