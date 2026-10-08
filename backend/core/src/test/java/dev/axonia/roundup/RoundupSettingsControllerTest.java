package dev.axonia.roundup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.ForbiddenException;
import dev.axonia.communication.RenderedMessage;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationWriteGate;
import dev.axonia.organization.Organizations;
import dev.axonia.roundup.internal.RoundupAvailability;
import dev.axonia.roundup.internal.RoundupPreviewer;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The roundup's own switch and its preview, and the two things all three handlers refuse: a caller without
 * {@code communication:write}, and an Organization the roundup is not available to, which has no provisioned directory
 * to address a roundup to however the switch is set.
 */
@CoreWebMvcTest(controllers = RoundupSettingsController.class)
class RoundupSettingsControllerTest extends ControllerTestSupport {

    private static final String ORGANIZATION_ID = "acme";

    @MockitoBean
    private OrganizationWriteGate gate;

    @MockitoBean
    private Organizations organizations;

    @MockitoBean
    private RoundupSettingsService settings;

    @MockitoBean
    private RoundupPreviewer previewer;

    @MockitoBean
    private RoundupAvailability availability;

    @BeforeEach
    void aTenantTheRoundupIsAvailableTo() {

        when(organizations.current()).thenReturn(
                Organization.builder().id(ORGANIZATION_ID).name("Acme Corp").communicationsEnabled(true).build());
        when(availability.in(ORGANIZATION_ID)).thenReturn(true);
    }

    @Test
    void settings_returnsTheOrganizationsOwnSwitch() {

        when(settings.forCurrentOrganization()).thenReturn(new RoundupSettings(ORGANIZATION_ID, true));

        var actual = mvc.get().uri("/axonia/roundup/settings");

        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.enabled").isEqualTo(true);
    }

    @Test
    void setEnabled_switchesTheRoundupOnAndAnswersWithTheStoredState() {

        when(settings.setEnabled(true)).thenReturn(new RoundupSettings(ORGANIZATION_ID, true));

        var actual = mvc.put().uri("/axonia/roundup/settings").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateRoundupEnabledRequest(true)));

        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.enabled").isEqualTo(true);
    }

    /** Absent is not off: a body that says nothing is a client bug, and silently reading it as "off" would hide it. */
    @Test
    void setEnabled_rejectsABodyThatSaysNothing() {

        var actual = mvc.put().uri("/axonia/roundup/settings").contentType(MediaType.APPLICATION_JSON).content("{}");

        actual.assertThat().hasStatus(400);
    }

    @Test
    void preview_rendersTheMostRecentlyClosedQuarterForTheCallerLookingAtIt() {

        when(previewer.preview(ORGANIZATION_ID, "Acme Corp", DEFAULT_TEST_USER))
                .thenReturn(new RenderedMessage("April to June 2026 across your communities", "<p>hello</p>"));

        var actual = mvc.get().uri("/axonia/roundup/preview");

        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.subject")
                .isEqualTo("April to June 2026 across your communities");
    }

    @Test
    void settings_refusesACallerWithoutCommunicationWrite() {

        doThrow(new ForbiddenException("missing permission")).when(gate).requireCommunicationWrite();

        mvc.get().uri("/axonia/roundup/settings").assertThat().hasStatus(403);

        verifyNoInteractions(settings);
    }

    @Test
    void setEnabled_refusesACallerWithoutCommunicationWrite() {

        doThrow(new ForbiddenException("missing permission")).when(gate).requireCommunicationWrite();

        mvc.put().uri("/axonia/roundup/settings").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateRoundupEnabledRequest(false))).assertThat()
                .hasStatus(403);

        verify(settings, never()).setEnabled(false);
    }

    @Test
    void preview_refusesACallerWithoutCommunicationWrite() {

        doThrow(new ForbiddenException("missing permission")).when(gate).requireCommunicationWrite();

        mvc.get().uri("/axonia/roundup/preview").assertThat().hasStatus(403);

        verifyNoInteractions(previewer);
    }

    /**
     * An Organization the roundup is not available to (see {@link RoundupAvailabilityPolicy}) has no directory a
     * roundup could be addressed to, so all three refuse with the same not-applicable {@code 403}
     * {@code OrganizationExportController} already uses - never a 500 from something further down failing to find
     * recipients.
     */
    @Test
    void everyRoundupControlRefusesAnOrganizationTheRoundupIsNotAvailableTo() {

        when(organizations.current())
                .thenReturn(Organization.builder().id("rust-hub").name("Rust Hub").communicationsEnabled(true).build());
        when(availability.in("rust-hub")).thenReturn(false);

        mvc.get().uri("/axonia/roundup/settings").assertThat().hasStatus(403);
        mvc.put().uri("/axonia/roundup/settings").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateRoundupEnabledRequest(true))).assertThat()
                .hasStatus(403);
        mvc.get().uri("/axonia/roundup/preview").assertThat().hasStatus(403);

        verifyNoInteractions(settings, previewer);
    }

    /**
     * The permission is checked before the organization is read: a caller who may not manage communication has no
     * business learning which kind of tenant they are pointed at from the shape of the refusal.
     */
    @Test
    void thePermissionIsCheckedBeforeTheOrganizationIsRead() {

        doThrow(new ForbiddenException("missing permission")).when(gate).requireCommunicationWrite();

        mvc.get().uri("/axonia/roundup/settings").assertThat().hasStatus(403);

        verifyNoInteractions(organizations, availability);
    }
}
