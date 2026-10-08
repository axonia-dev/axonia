package dev.axonia.identity;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.identity.mother.DirectoryUserMother;
import dev.axonia.identity.mother.UserMother;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@CoreWebMvcTest(controllers = UserController.class)
class UserControllerTest extends ControllerTestSupport {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private MyOrganizationsService myOrganizationsService;

    @MockitoBean
    private AvatarOverrideService avatarOverrideService;

    @Test
    void me() throws JacksonException {

        // given
        var user = UserMother.any().build();
        when(userService.me()).thenReturn(user);

        // when
        var actual = mvc.get().uri("/axonia/me");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(user));
    }

    @Test
    void resolve() throws JacksonException {

        // given
        var alice = DirectoryUserMother.any().build();
        var bob = DirectoryUserMother.any().id("22222222-2222-2222-2222-222222222222").displayName("Bob Brandt")
                .build();
        when(userService.resolve(List.of(alice.id(), bob.id()))).thenReturn(List.of(alice, bob));

        // when
        var actual = mvc.get().uri("/axonia/users").queryParam("ids", alice.id() + "," + bob.id());

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(List.of(alice, bob)));
    }

    @Test
    void resolve_missingIdsParam() {

        // when
        var actual = mvc.get().uri("/axonia/users");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void resolve_tooManyIdsIsRejected() {

        // given
        var ids = java.util.stream.IntStream.rangeClosed(1, 101).mapToObj(i -> "u" + i)
                .collect(java.util.stream.Collectors.joining(","));

        // when
        var actual = mvc.get().uri("/axonia/users?ids={ids}", ids);

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void uploadingAnAvatarReturnsTheRefreshedProfile() throws JacksonException {

        // given
        var file = new MockMultipartFile("file", "me.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        var user = UserMother.any().avatarUrl("https://cdn.example/own.png").build();
        when(userService.me()).thenReturn(user);

        // when
        var actual = mvc.perform(multipart(HttpMethod.PUT, "/axonia/me/avatar").file(file));

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(user));
        verify(avatarOverrideService).set(argThat(upload -> "image/png".equals(upload.contentType())));
    }

    @Test
    void uploadingAnUnreadableAvatarIsRejected() {

        // given - a MockMultipartFile whose getBytes() throws simulates a broken upload stream
        var broken = new MockMultipartFile("file", "me.png", MediaType.IMAGE_PNG_VALUE, new byte[]{0}) {

            @Override
            public byte[] getBytes() throws IOException {

                throw new IOException("simulated disk error");
            }
        };

        // when
        var actual = mvc.perform(multipart(HttpMethod.PUT, "/axonia/me/avatar").file(broken));

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_IMAGE");
    }

    @Test
    void clearingAnAvatarReturnsTheRefreshedProfile() throws JacksonException {

        // given
        var user = UserMother.any().avatarUrl(null).build();
        when(userService.me()).thenReturn(user);

        // when
        var actual = mvc.delete().uri("/axonia/me/avatar");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(user));
        verify(avatarOverrideService).clear();
    }

    @Test
    void declaringATimeZoneReturnsTheRefreshedProfile() throws JacksonException {

        // given
        var user = UserMother.any().timeZone("America/New_York").observedTimeZone("Europe/Brussels").timeZoneIsOwn(true)
                .build();
        when(userService.declareTimeZone("America/New_York")).thenReturn(user);

        // when
        var actual = mvc.put().uri("/axonia/me/time-zone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"zone\":\"America/New_York\"}");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(user));
    }

    /**
     * The zone is read back by {@code ZoneId.of} on every projection and on every message rendered for this member, so
     * an unresolvable one has to be refused here rather than stored and thrown on somewhere the member cannot reach.
     */
    @Test
    void declaringAnUnknownZoneIsRefusedAndNeverReachesTheService() {

        // when
        var actual = mvc.put().uri("/axonia/me/time-zone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"zone\":\"Mars/Olympus_Mons\"}");

        // then
        actual.assertThat().hasStatus(HttpStatus.BAD_REQUEST);
        verify(userService, never()).declareTimeZone(any());
    }

    @Test
    void clearingATimeZoneDropsTheDeclaration() throws JacksonException {

        // given
        var user = UserMother.any().timeZone("Europe/Brussels").observedTimeZone("Europe/Brussels").timeZoneIsOwn(false)
                .build();
        when(userService.clearDeclaredTimeZone()).thenReturn(user);

        // when
        var actual = mvc.delete().uri("/axonia/me/time-zone");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.timeZoneIsOwn").isEqualTo(false);
    }

    @Test
    void theBrowserReportsItsOwnZone() throws JacksonException {

        // given
        var user = UserMother.any().timeZone("Europe/Brussels").observedTimeZone("Europe/Brussels").timeZoneIsOwn(false)
                .build();
        when(userService.observeTimeZone("Europe/Brussels")).thenReturn(user);

        // when
        var actual = mvc.put().uri("/axonia/me/time-zone/observed").contentType(MediaType.APPLICATION_JSON)
                .content("{\"zone\":\"Europe/Brussels\"}");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(user));
    }
}
