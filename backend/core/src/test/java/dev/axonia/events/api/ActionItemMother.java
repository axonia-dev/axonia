package dev.axonia.events.api;

import java.util.List;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ActionItemMother {

    public ActionItem.ActionItemBuilder any() {

        return ActionItem.builder().id(1L).communityId(1L).title("Audit the CI pipeline").description("")
                .tags(List.of()).raisedByUserId("alice").raisedByDisplayName("Alice")
                .checkpoint(Checkpoint.builder().pinned(false).build()).carryCount(0).outcome(ActionItemOutcome.OPEN);
    }
}
