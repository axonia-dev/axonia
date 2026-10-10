package dev.axonia.community.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.community.application.CommunityKindService;
import dev.axonia.community.application.CreateKindRequest;
import dev.axonia.community.application.RenameKindRequest;
import dev.axonia.community.domain.CommunityKindMother;
import dev.axonia.shared.api.error.ConflictException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = CommunityKindController.class)
class CommunityKindControllerTest extends ControllerTestSupport {

    @MockitoBean
    private CommunityKindService communityKindService;

    @Test
    void list() throws JacksonException {

        // given
        var kinds = List.of(CommunityKindMother.any().build());
        when(communityKindService.list()).thenReturn(kinds);

        // when
        var actual = mvc.get().uri("/axonia/community-kinds");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(kinds));
    }

    @Test
    void create() throws JacksonException {

        // given
        var kind = CommunityKindMother.any().label("Tribe").build();
        when(communityKindService.create(refEq(new CreateKindRequest("Tribe")))).thenReturn(kind);

        // when
        var actual = mvc.post().uri("/axonia/community-kinds").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":"Tribe"}""");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(kind));
    }

    @Test
    void create_blankLabelRejected() {

        // when
        var actual = mvc.post().uri("/axonia/community-kinds").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":""}""");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void rename() throws JacksonException {

        // given
        var kind = CommunityKindMother.any().id(1L).label("Chapter").build();
        when(communityKindService.rename(eq(1L), refEq(new RenameKindRequest("Chapter")))).thenReturn(kind);

        // when
        var actual = mvc.patch().uri("/axonia/community-kinds/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label":"Chapter"}""");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(kind));
    }

    @Test
    void rename_blankLabelRejected() {

        // when
        var actual = mvc.patch().uri("/axonia/community-kinds/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label":""}""");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void rename_duplicateLabelConflict() {

        // given
        doThrow(new ConflictException("KIND_EXISTS", "exists")).when(communityKindService).rename(eq(1L),
                refEq(new RenameKindRequest("Chapter")));

        // when
        var actual = mvc.patch().uri("/axonia/community-kinds/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label":"Chapter"}""");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("KIND_EXISTS");
    }

    @Test
    void delete_withoutReassignment() {

        // when
        var actual = mvc.delete().uri("/axonia/community-kinds/{id}", 1L);

        // then
        actual.assertThat().hasStatus(204);
        verify(communityKindService).delete(1L, null);
    }

    @Test
    void delete_withReassignment() {

        // when
        var actual = mvc.delete().uri("/axonia/community-kinds/{id}?reassignTo={target}", 1L, 2L);

        // then
        actual.assertThat().hasStatus(204);
        verify(communityKindService).delete(1L, 2L);
    }

    @Test
    void delete_inUseWithoutTargetConflict() {

        // given
        doThrow(new ConflictException("KIND_IN_USE", "in use")).when(communityKindService).delete(eq(1L), eq(null));

        // when
        var actual = mvc.delete().uri("/axonia/community-kinds/{id}", 1L);

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("KIND_IN_USE");
    }
}
