package dev.axonia.learning.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.learning.adapter.out.communication.OpenDoorNotices;

/** The panel entry of the open-to-questions notice: the community on its line, the search for the area as its link. */
@Configuration(proxyBeanMethods = false)
public class OpenDoorMessages {

    @Bean
    InAppEntryShape openToQuestionsEntry() {

        return new InAppEntryShape(OpenDoorNotices.TYPE, "communityName", "searchUrl");
    }
}
