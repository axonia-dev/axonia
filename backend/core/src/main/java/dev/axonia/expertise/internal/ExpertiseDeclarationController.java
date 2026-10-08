package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.expertise.ExpertiseDeclarationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
class ExpertiseDeclarationController {

    private final ExpertiseDeclarationService expertiseDeclarationService;

    @PostMapping("/communities/{slug}/expertise/declarations")
    DeclaredArea declare(@PathVariable String slug, @RequestBody DeclareRequest request) {

        var declaration = expertiseDeclarationService.declare(slug, Objects.requireNonNullElse(request.tag(), ""));
        return new DeclaredArea(declaration.tag(), declaration.declaredAt());
    }

    @DeleteMapping("/communities/{slug}/expertise/declarations/{tag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String slug, @PathVariable String tag) {

        expertiseDeclarationService.remove(slug, tag);
    }

    record DeclareRequest(String tag) {
    }

    record DeclaredArea(String tag, Instant declaredAt) {
    }
}
