package dev.axonia.expertise.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.expertise.application.ExpertiseCurationService;
import dev.axonia.expertise.application.TagUsageReport;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.MergeSuggestion;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ExpertiseCurationController.class)
class ExpertiseCurationControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ExpertiseCurationService expertiseCurationService;

    @Test
    void vocabulary_returnsTheAreasInTheSameShapeAsTheAreasEndpoint() throws JacksonException {

        // given - "kafak" is merged into "kafka", and nobody visible carries "helm"
        var areas = List.of(new ExpertiseArea("kafka", List.of("kafak", "kafka"), 3),
                new ExpertiseArea("helm", List.of("helm"), 0));
        when(expertiseCurationService.vocabulary("eng")).thenReturn(areas);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/curation/vocabulary", "eng").exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(areas));
        actual.assertThat().bodyJson().extractingPath("$[1].peopleCount").isEqualTo(0);
    }

    @Test
    void suggestions_returnsMergeSuggestions() throws JacksonException {

        // given
        var suggestion = new MergeSuggestion("apache-kafka", "kafka", 0.92);
        when(expertiseCurationService.suggestions("eng")).thenReturn(List.of(suggestion));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/curation/suggestions", "eng");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(List.of(suggestion)));
    }

    @Test
    void merge_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/merge", "eng")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variant\":\"apache-kafka\",\"canonical\":\"kafka\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).merge("eng", "apache-kafka", "kafka");
    }

    @Test
    void merge_rejectsBlankVariant() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/merge", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"\",\"canonical\":\"kafka\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void split_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/split", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"apache-kafka\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).split("eng", "apache-kafka");
    }

    @Test
    void split_rejectsBlankVariant() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/split", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void mergeBatch_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/merge-batch", "eng")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variants\":[\"yep\",\"ok\"],\"canonical\":\"yes\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).mergeMany("eng", List.of("yep", "ok"), "yes");
    }

    @Test
    void mergeBatch_rejectsEmptyVariants() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/merge-batch", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"variants\":[],\"canonical\":\"yes\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void dismiss_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/dismiss", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tagA\":\"spring\",\"tagB\":\"spring-boot\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).dismiss("eng", "spring", "spring-boot");
    }

    @Test
    void dismiss_rejectsBlankTag() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/dismiss", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tagA\":\"\",\"tagB\":\"spring-boot\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void usage_returnsTheCountsPerKindAndTheDeclarations() throws JacksonException {

        // given
        var report = new TagUsageReport(3, 2, 0, 4, 0);
        when(expertiseCurationService.usage("eng", "kafka")).thenReturn(report);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/curation/tags/{tag}/usage", "eng", "kafka")
                .exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(report));
        actual.assertThat().bodyJson().extractingPath("$.declarations").isEqualTo(4);
    }

    @Test
    void rename_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/rename", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"kafak\",\"to\":\"kafka\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).rename("eng", "kafak", "kafka");
    }

    @Test
    void rename_rejectsBlankTarget() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/rename", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"kafak\",\"to\":\"\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void delete_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/delete", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"misc\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).delete("eng", "misc");
    }

    @Test
    void delete_rejectsBlankTag() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/delete", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void undismiss_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/curation/undismiss", "eng")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tagA\":\"spring\",\"tagB\":\"spring-boot\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseCurationService).undismiss("eng", "spring", "spring-boot");
    }
}
