package dev.axonia.events.api;

public interface Slots {

    RecordedSlot recordSlot(Long sessionId, Long sectionId, RecordSlotRequest request);

    Session signUp(Long sessionId, Long sectionId, Long slotId);

    PresenterAssignment addPresenter(Long sessionId, Long sectionId, Long slotId, String presenter);
}
