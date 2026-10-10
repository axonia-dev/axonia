package dev.axonia.learning.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.learning.api.HeldArea;
import dev.axonia.learning.api.Learner;
import dev.axonia.learning.api.LearningPage;
import dev.axonia.learning.api.WantedArea;
import dev.axonia.learning.application.LearningService;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ValidationException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = LearningController.class)
class LearningControllerTest extends ControllerTestSupport {

    private static final Instant DECLARED_AT = Instant.parse("2026-09-12T12:00:00Z");

    @MockitoBean
    private LearningService learningService;

    @Test
    void thePageCarriesTheMembersAreasAndTheWantedList() {

        // given
        when(learningService.page("platform")).thenReturn(new LearningPage(true, false, 10,
                List.of(new HeldArea("kafka", DECLARED_AT)), List.of(new WantedArea("kafka", 4, 0))));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/learning", "platform").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"member": true, "archived": false, "limit": 10,
                 "mine": [{"tag": "kafka", "declaredAt": "2026-09-12T12:00:00Z"}],
                 "wanted": [{"tag": "kafka", "learners": 4, "canBeAsked": 0}]}
                """);
    }

    @Test
    void theLearnersOfAnArea() {

        // given
        when(learningService.learners("platform", "kafka"))
                .thenReturn(List.of(new Learner("alice", "Alice", null, DECLARED_AT)));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/learning/areas/{tag}/learners", "platform", "kafka")
                .exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"userId": "alice", "displayName": "Alice", "declaredAt": "2026-09-12T12:00:00Z"}]
                """);
    }

    @Test
    void addingReturnsTheAreaAndItsDate() {

        // given
        when(learningService.add("platform", "Kafka")).thenReturn(new HeldArea("kafka", DECLARED_AT));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/learning/intents", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"Kafka\"}").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"tag\": \"kafka\", \"declaredAt\": \"2026-09-12T12:00:00Z\"}");
    }

    @Test
    void aMissingTagReachesTheServiceAsBlank() {

        // given
        when(learningService.add("platform", "")).thenThrow(new ValidationException("TAG_BLANK", "A tag needs a name"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/learning/intents", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("TAG_BLANK");
    }

    @Test
    void theCapIsAConflictCarryingItsCode() {

        // given
        when(learningService.add("platform", "kafka"))
                .thenThrow(new ConflictException("LEARNING_INTENT_LIMIT", "At most 10"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/learning/intents", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"kafka\"}").exchange();

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("LEARNING_INTENT_LIMIT");
    }

    @Test
    void removingIsNoContent() {

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/learning/intents/{tag}", "platform", "kafka")
                .exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(learningService).remove("platform", "kafka");
    }
}
