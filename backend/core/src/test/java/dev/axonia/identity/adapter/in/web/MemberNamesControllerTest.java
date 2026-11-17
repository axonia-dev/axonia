package dev.axonia.identity.adapter.in.web;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.identity.application.MemberNameSettings;
import dev.axonia.identity.application.MemberNameSettings.PreviewRow;
import dev.axonia.identity.api.spi.MemberNamesGate;
import dev.axonia.identity.application.port.out.NamePatternLock;
import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.NamePatterns;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = MemberNamesController.class)
class MemberNamesControllerTest extends ControllerTestSupport {

    private static final String HOUSE_FORMAT = "{family}, {given}-NORTHWIND";

    @MockitoBean
    MemberNameSettings settings;

    @Test
    void readsThePatterns() {

        // given
        when(settings.patterns()).thenReturn(List.of(HOUSE_FORMAT));

        // when
        var response = mvc.get().uri("/axonia/member-names").exchange();

        // then
        response.assertThat().hasStatus(200).bodyJson().extractingPath("$.patterns[0]").isEqualTo(HOUSE_FORMAT);
    }

    @Test
    void previewsTheRowsAPatternListReads() {

        // given
        when(settings.preview(List.of(HOUSE_FORMAT)))
                .thenReturn(List.of(new PreviewRow("Rochat, Aurélia-NORTHWIND", "Aurélia Rochat", "Aurélia", 0),
                        new PreviewRow("octocat", "octocat", null, null)));

        // when
        var response = mvc.post().uri("/axonia/member-names/preview").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new Body(List.of(HOUSE_FORMAT)))).exchange();

        // then
        var json = response.assertThat().hasStatus(200).bodyJson();
        json.extractingPath("$.rows[0].received").isEqualTo("Rochat, Aurélia-NORTHWIND");
        json.extractingPath("$.rows[0].displayName").isEqualTo("Aurélia Rochat");
        json.extractingPath("$.rows[0].givenName").isEqualTo("Aurélia");
        json.extractingPath("$.rows[0].matched").isEqualTo(0);
        json.extractingPath("$.rows[1].givenName").isNull();
        json.extractingPath("$.rows[1].matched").isNull();
    }

    /** The real validation, over collaborators it never reaches: a refused list is refused before any read. */
    private final MemberNameSettings validating = new MemberNameSettings(mock(MemberNamesGate.class),
            mock(NamePatternStore.class), mock(UserStore.class), mock(NamePatternLock.class));

    @Test
    void aPreviewOfAnInvalidPatternIs400NamingThePatternAtFault() {

        // given
        when(settings.preview(any())).thenAnswer(call -> validating.preview(call.getArgument(0)));

        // when
        var response = mvc.post().uri("/axonia/member-names/preview").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new Body(List.of("{family}, {given}", "{given}")))).exchange();

        // then
        var json = response.assertThat().hasStatus(400).bodyJson();
        json.extractingPath("$.code").isEqualTo("NAME_PATTERN_INVALID");
        json.extractingPath("$.field").isEqualTo("patterns[1]");
    }

    @Test
    void savingTooManyPatternsIs400NamingTheList() {

        // given
        when(settings.save(any())).thenAnswer(call -> validating.save(call.getArgument(0)));

        // when
        var response = mvc.put().uri("/axonia/member-names").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper
                        .writeValueAsString(new Body(Collections.nCopies(NamePatterns.MAX + 1, "{given} {family}"))))
                .exchange();

        // then
        var json = response.assertThat().hasStatus(400).bodyJson();
        json.extractingPath("$.code").isEqualTo("NAME_PATTERNS_TOO_MANY");
        json.extractingPath("$.field").isEqualTo("patterns");
    }

    @Test
    void savesAndAnswersHowManyNamesChanged() {

        // given
        when(settings.save(List.of(HOUSE_FORMAT))).thenReturn(2);

        // when
        var response = mvc.put().uri("/axonia/member-names").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new Body(List.of(HOUSE_FORMAT)))).exchange();

        // then
        response.assertThat().hasStatus(200).bodyJson().extractingPath("$.changed").isEqualTo(2);
    }

    @Test
    void savingWithoutMemberWriteIs403() {

        // given
        when(settings.save(any())).thenThrow(new ForbiddenException("member:write required"));

        // when
        var response = mvc.put().uri("/axonia/member-names").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new Body(List.of(HOUSE_FORMAT)))).exchange();

        // then
        response.assertThat().hasStatus(403);
    }

    @Test
    void aBodyWithoutAPatternListIs400() {

        // when
        var response = mvc.put().uri("/axonia/member-names").contentType(MediaType.APPLICATION_JSON).content("{}")
                .exchange();

        // then
        response.assertThat().hasStatus(400);
        verifyNoInteractions(settings);
    }

    private record Body(List<String> patterns) {
    }
}
