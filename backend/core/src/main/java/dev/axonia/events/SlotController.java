package dev.axonia.events;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/sessions/{sessionId}/sections/{sectionId}/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Session add(@PathVariable Long sessionId, @PathVariable Long sectionId,
            @Valid @RequestBody AddSlotRequest request) {

        return slotService.addSlot(sessionId, sectionId, request);
    }

    @PostMapping("/from-backlog")
    @ResponseStatus(HttpStatus.CREATED)
    Session fromBacklog(@PathVariable Long sessionId, @PathVariable Long sectionId,
            @Valid @RequestBody QueueTopicRequest request) {

        return slotService.queueTopic(sessionId, sectionId, request);
    }

    @PostMapping("/recorded")
    @ResponseStatus(HttpStatus.CREATED)
    RecordedSlot record(@PathVariable Long sessionId, @PathVariable Long sectionId,
            @Valid @RequestBody RecordSlotRequest request) {

        return slotService.recordSlot(sessionId, sectionId, request);
    }

    @PostMapping("/{slotId}/presenters/me")
    @ResponseStatus(HttpStatus.CREATED)
    Session signUp(@PathVariable Long sessionId, @PathVariable Long sectionId, @PathVariable Long slotId) {

        return slotService.signUp(sessionId, sectionId, slotId);
    }

    @PostMapping("/{slotId}/presenters")
    @ResponseStatus(HttpStatus.CREATED)
    PresenterAssignment addPresenter(@PathVariable Long sessionId, @PathVariable Long sectionId,
            @PathVariable Long slotId, @Valid @RequestBody AddPresenterRequest request) {

        return slotService.addPresenter(sessionId, sectionId, slotId, request.presenter());
    }

    @DeleteMapping("/{slotId}/presenters/me")
    Session withdraw(@PathVariable Long sessionId, @PathVariable Long sectionId, @PathVariable Long slotId) {

        return slotService.withdraw(sessionId, sectionId, slotId);
    }

    @PutMapping("/{slotId}")
    Session edit(@PathVariable Long sessionId, @PathVariable Long sectionId, @PathVariable Long slotId,
            @Valid @RequestBody EditSlotRequest request) {

        return slotService.editSlot(sessionId, sectionId, slotId, request);
    }

    @DeleteMapping("/{slotId}")
    Session remove(@PathVariable Long sessionId, @PathVariable Long sectionId, @PathVariable Long slotId) {

        return slotService.removeSlot(sessionId, sectionId, slotId);
    }

    @DeleteMapping("/{slotId}/presenters/{userId}")
    Session removePresenter(@PathVariable Long sessionId, @PathVariable Long sectionId, @PathVariable Long slotId,
            @PathVariable String userId) {

        return slotService.removePresenter(sessionId, sectionId, slotId, userId);
    }

    @PutMapping("/order")
    Session reorder(@PathVariable Long sessionId, @PathVariable Long sectionId,
            @Valid @RequestBody ReorderSlotsRequest request) {

        return slotService.reorderSlots(sessionId, sectionId, request);
    }
}
