package dev.axonia.events.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.api.FeedbackAnswer;
import dev.axonia.events.api.SessionFeedbackView;
import dev.axonia.events.application.SessionFeedbackService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/sessions/{id}/feedback")
@RequiredArgsConstructor
public class SessionFeedbackController {

    private final SessionFeedbackService sessionFeedbackService;

    @GetMapping
    SessionFeedbackView view(@PathVariable Long id) {

        return sessionFeedbackService.view(id);
    }

    @PutMapping("/me")
    SessionFeedbackView give(@PathVariable Long id, @Valid @RequestBody FeedbackAnswer answer) {

        return sessionFeedbackService.give(id, answer);
    }
}
