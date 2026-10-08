package dev.axonia.events;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ConflictException;
import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.mother.AttendanceMother;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = AttendanceController.class)
class AttendanceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private AttendanceService attendanceService;

    @Test
    void signal_returns201() throws JacksonException {

        // given
        var attendance = AttendanceMother.any().build();
        when(attendanceService.signal(9L)).thenReturn(attendance);

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/attendance", 9L);

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(attendance));
    }

    @Test
    void signal_alreadyGoingReturns409WithCode() {

        // given
        when(attendanceService.signal(9L)).thenThrow(new ConflictException("ALREADY_GOING", "dup"));

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/attendance", 9L);

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ALREADY_GOING");
    }

    @Test
    void unsignal_returns204() {

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{id}/attendance/me", 9L);

        // then
        actual.assertThat().hasStatus(204);
    }

    @Test
    void list_returnsAttendancePage() throws JacksonException {

        // given
        when(attendanceService.list(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(AttendanceMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/sessions/{id}/attendance", 9L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void list_unsupportedSortIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/sessions/{id}/attendance", 9L).queryParam("sort", "user_id,desc");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }
}
