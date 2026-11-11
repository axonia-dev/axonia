package dev.axonia.learning.configuration;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.learning.domain.OpenDoorSubject;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDoorMessagesTest {

    @Test
    void thePanelEntryReadsTheAreaLineAndTheSearch() {

        assertThat(new OpenDoorMessages().openToQuestionsEntry())
                .isEqualTo(new InAppEntryShape(OpenDoorSubject.MESSAGE_TYPE, "line", "searchUrl"));
    }
}
