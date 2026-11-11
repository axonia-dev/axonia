package dev.axonia.learning.configuration;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.learning.adapter.out.communication.OpenDoorNotices;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDoorMessagesTest {

    @Test
    void thePanelEntryReadsTheCommunityAndTheSearch() {

        assertThat(new OpenDoorMessages().openToQuestionsEntry())
                .isEqualTo(new InAppEntryShape(OpenDoorNotices.TYPE, "communityName", "searchUrl"));
    }
}
