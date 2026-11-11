package dev.axonia.learning.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.learning.adapter.out.communication.OpenDoorNotices;
import dev.axonia.learning.domain.OpenDoorSubject;

/**
 * The panel entry of the open-to-questions notice: the area on its line, the search for the area as its link. The
 * community is its sender, as on every other community message.
 */
@Configuration(proxyBeanMethods = false)
public class OpenDoorMessages {

    @Bean
    InAppEntryShape openToQuestionsEntry() {

        return new InAppEntryShape(OpenDoorSubject.MESSAGE_TYPE, OpenDoorNotices.LINE_FACT, OpenDoorNotices.LINK_FACT);
    }
}
