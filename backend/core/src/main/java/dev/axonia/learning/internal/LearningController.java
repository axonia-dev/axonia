package dev.axonia.learning.internal;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.learning.HeldArea;
import dev.axonia.learning.Learner;
import dev.axonia.learning.LearningPage;
import dev.axonia.learning.LearningService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/communities/{slug}/learning")
@RequiredArgsConstructor
class LearningController {

    private final LearningService learningService;

    @GetMapping
    LearningPage page(@PathVariable String slug) {

        return learningService.page(slug);
    }

    @GetMapping("/areas/{tag}/learners")
    List<Learner> learners(@PathVariable String slug, @PathVariable String tag) {

        return learningService.learners(slug, tag);
    }

    @PostMapping("/intents")
    HeldArea add(@PathVariable String slug, @RequestBody AddRequest request) {

        return learningService.add(slug, Objects.requireNonNullElse(request.tag(), ""));
    }

    @DeleteMapping("/intents/{tag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String slug, @PathVariable String tag) {

        learningService.remove(slug, tag);
    }

    record AddRequest(String tag) {
    }
}
