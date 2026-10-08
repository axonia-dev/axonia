package dev.axonia.communication;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = NotificationPreferenceController.class)
class NotificationPreferenceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private NotificationPreferenceService notificationPreferenceService;

    @Test
    void preferences_returnsTheCurrentUsersPreferences() throws JacksonException {

        // given
        var preferences = List.of(new NotificationPreference(MessageType.SESSION_WRAP_UP, false));
        when(notificationPreferenceService.preferences()).thenReturn(preferences);

        // when
        var actual = mvc.get().uri("/axonia/me/notification-preferences");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(preferences));
    }

    @Test
    void set_callsServiceAndReturns204() {

        // when
        var actual = mvc.put().uri("/axonia/me/notification-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"session_wrap_up\",\"enabled\":false}");

        // then
        actual.assertThat().hasStatus(204);
        verify(notificationPreferenceService).set(MessageType.SESSION_WRAP_UP, false);
    }

    @Test
    void set_rejectsAMissingType() {

        // when
        var actual = mvc.put().uri("/axonia/me/notification-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}");

        // then
        actual.assertThat().hasStatus(400);
    }
}
