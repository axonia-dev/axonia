package dev.axonia.knowledge;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;
    private final ArtifactCreditService artifactCreditService;

    @GetMapping("/communities/{slug}/artifacts")
    Page<Artifact> search(@PathVariable String slug, @ModelAttribute ArtifactSearchRequest request,
            @PageableDefault(size = 20, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {

        return artifactService.search(slug, request.toFilter(), pageable);
    }

    @GetMapping("/communities/{slug}/artifacts/sessions")
    List<ArtifactSession> sessions(@PathVariable String slug) {

        return artifactService.sessions(slug);
    }

    @GetMapping("/communities/{slug}/artifacts/stats")
    LibraryStats stats(@PathVariable String slug) {

        return artifactService.stats(slug);
    }

    @PostMapping("/communities/{slug}/artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    Artifact capture(@PathVariable String slug, @Valid @RequestBody CreateArtifactRequest request) {

        return artifactService.capture(slug, request);
    }

    @GetMapping("/artifacts/{id}")
    Artifact get(@PathVariable Long id) {

        return artifactService.get(id);
    }

    @PutMapping("/artifacts/{id}")
    Artifact edit(@PathVariable Long id, @Valid @RequestBody UpdateArtifactRequest request) {

        return artifactService.edit(id, request);
    }

    @PutMapping("/artifacts/{id}/contributor")
    Artifact changeContributor(@PathVariable Long id, @Valid @RequestBody ChangeContributorRequest request) {

        return artifactCreditService.changeContributor(id, request);
    }

    @DeleteMapping("/artifacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable Long id) {

        artifactService.remove(id);
    }

    @PostMapping("/artifacts/{id}/archive")
    Artifact archive(@PathVariable Long id) {

        return artifactService.archive(id);
    }

    @PostMapping("/artifacts/{id}/restore")
    Artifact restore(@PathVariable Long id) {

        return artifactService.restore(id);
    }
}
