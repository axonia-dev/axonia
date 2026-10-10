package dev.axonia.knowledge.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.knowledge.application.ArtifactOpeningService;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@CoreWebMvcTest(controllers = ArtifactOpeningController.class)
class ArtifactOpeningControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ArtifactOpeningService artifactOpeningService;

    @Test
    void record() {

        // when
        var actual = mvc.post().uri("/axonia/artifacts/{id}/openings", 3L);

        // then
        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        verify(artifactOpeningService, times(1)).record(3L);
    }
}
