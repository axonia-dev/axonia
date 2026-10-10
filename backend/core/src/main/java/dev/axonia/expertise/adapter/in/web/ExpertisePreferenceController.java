package dev.axonia.expertise.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.expertise.application.AreaEvidencePage;
import dev.axonia.expertise.application.ExpertisePreferenceService;
import dev.axonia.expertise.application.SelfView;
import dev.axonia.expertise.domain.SourceType;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
class ExpertisePreferenceController {

    private final ExpertisePreferenceService expertisePreferenceService;

    @GetMapping("/expertise/me")
    SelfView selfView() {

        return expertisePreferenceService.selfView();
    }

    @GetMapping("/communities/{slug}/expertise/me")
    SelfView selfViewIn(@PathVariable String slug) {

        return expertisePreferenceService.selfViewIn(slug);
    }

    @PutMapping("/expertise/me/opt-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void optOut(@Valid @RequestBody OptOutRequest request) {

        expertisePreferenceService.optOut(request.optedOut());
    }

    @PutMapping("/expertise/me/hidden/{tag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hideTag(@PathVariable String tag) {

        expertisePreferenceService.hideTag(tag);
    }

    @DeleteMapping("/expertise/me/hidden/{tag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unhideTag(@PathVariable String tag) {

        expertisePreferenceService.unhideTag(tag);
    }

    @GetMapping("/expertise/me/areas/{tag}/evidence")
    AreaEvidencePage areaEvidence(@PathVariable String tag, @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        return expertisePreferenceService.areaEvidence(tag, offset, limit);
    }

    @GetMapping("/communities/{slug}/expertise/me/areas/{tag}/evidence")
    AreaEvidencePage areaEvidenceIn(@PathVariable String slug, @PathVariable String tag,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {

        return expertisePreferenceService.areaEvidenceIn(slug, tag, offset, limit);
    }

    @PostMapping("/expertise/me/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@Valid @RequestBody DismissRequest request) {

        expertisePreferenceService.dismissSignal(request.sourceType(), request.sourceId(), request.tag());
    }

    record OptOutRequest(boolean optedOut) {
    }

    record DismissRequest(@NotNull SourceType sourceType, @NotNull Long sourceId, @NotBlank String tag) {
    }
}
