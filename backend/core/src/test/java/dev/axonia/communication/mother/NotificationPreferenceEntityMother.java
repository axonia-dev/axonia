package dev.axonia.communication.mother;

import dev.axonia.communication.internal.NotificationPreferenceEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationPreferenceEntityMother {

    public NotificationPreferenceEntity.NotificationPreferenceEntityBuilder any() {

        return NotificationPreferenceEntity.builder().organizationId("default").userId("user-1")
                .messageType("session_upcoming").enabled(true);
    }
}
