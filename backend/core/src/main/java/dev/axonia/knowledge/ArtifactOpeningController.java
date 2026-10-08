package dev.axonia.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Its own controller rather than another method on {@link ArtifactController}: a new constructor dependency there
 * breaks every other {@code @WebMvcTest} slice that lists that controller, as context-load errors no focused test run
 * can see.
 *
 * <p>
 * 204 rather than 201. The write is conflict-tolerant by design and the caller never reads the result, so "created"
 * would be a lie on every repeat open.
 */
@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class ArtifactOpeningController {

    private final ArtifactOpeningService artifactOpeningService;

    @PostMapping("/artifacts/{id}/openings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void record(@PathVariable Long id) {

        artifactOpeningService.record(id);
    }
}
