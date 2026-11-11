package dev.axonia.learning.adapter.in.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.expertise.api.DeclarationMade;
import dev.axonia.learning.application.LearningService;
import lombok.RequiredArgsConstructor;

/** Tells the learners of an area someone just opened up on, inside the declaring request. */
@Component
@RequiredArgsConstructor
class OpenDoorListener {

    private final LearningService learning;

    @EventListener
    void on(DeclarationMade event) {

        learning.notifyLearners(event);
    }
}
