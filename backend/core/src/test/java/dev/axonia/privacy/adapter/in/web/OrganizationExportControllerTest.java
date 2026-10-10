package dev.axonia.privacy.adapter.in.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.privacy.application.DataExport;
import dev.axonia.privacy.application.ExportLabels;
import dev.axonia.privacy.application.ExportPackageNames;
import dev.axonia.privacy.application.MemberDataRights;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.ratelimit.RateLimiters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A {@code @WebMvcTest} slice for {@link OrganizationExportController}. {@link ExportDownload} is imported for real
 * (with only its {@link DataExport} dependency mocked and its {@link Clock} fixed) rather than mocked outright, because
 * the header assertions below only mean something if the real filename and content-type logic actually ran.
 *
 * <p>
 * Every test starts refused - no permission, and an Organization kind that would pass the permission check - so a test
 * that forgets to grant something fails loudly instead of quietly passing.
 */
@CoreWebMvcTest(controllers = OrganizationExportController.class)
@Import({ExportDownload.class, ExportPackageNames.class, OrganizationExportControllerTest.FixedClockConfig.class})
class OrganizationExportControllerTest extends ControllerTestSupport {

    private static final String SUBJECT = "operator-1";
    private static final String ORGANIZATION_ID = "01m31gb7j47v9e3kf91j7pvr9h";

    private static final String PUBLIC_ADDRESS = "acme";

    @MockitoBean
    private DataExport dataExport;

    @MockitoBean
    private AuthorizationGate gate;

    @MockitoBean
    private OrganizationRegistry organizations;

    @MockitoBean
    private MemberDataRights memberDataRights;

    @MockitoBean
    private ExportLabels exportLabels;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void refusesEverythingByDefault() {

        doThrow(new ForbiddenException("Missing permission: " + Permission.ORGANIZATION_EXPORT.code())).when(gate)
                .requirePermission(Permission.ORGANIZATION_EXPORT);
        givenTheOrganization();
    }

    @Test
    void refusesACallerWithoutTheExportPermission() throws Exception {

        mockMvc.perform(get("/axonia/admin/data-export").with(jwtFor(SUBJECT))).andExpect(status().isForbidden());
    }

    /**
     * The name comes from the Organization's label, never its identifier, which is why the two differ in this fixture:
     * a filename is printed on the outside of a file that leaves the building by design, and a tenant identifier there
     * tells the reader nothing while being a live key into axonia's own systems.
     */
    @Test
    void streamsAZipNamedForTheOrganizationAndTheDate() throws Exception {

        givenPermission(Permission.ORGANIZATION_EXPORT);

        mockMvc.perform(get("/axonia/admin/data-export").with(jwtFor(SUBJECT))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("axonia-export-acme-2026-07-27.zip")))
                .andExpect(header().string("Content-Disposition", not(containsString(ORGANIZATION_ID))));
    }

    @Test
    void refusesWhereMembersExerciseTheirOwnDataRights() throws Exception {

        givenPermission(Permission.ORGANIZATION_EXPORT);
        when(memberDataRights.exercisedByMembers(ORGANIZATION_ID)).thenReturn(true);

        mockMvc.perform(get("/axonia/admin/data-export").with(jwtFor(SUBJECT))).andExpect(status().isForbidden());
    }

    /**
     * {@link ExportDownload} throttles per caller, keyed by subject, so a distinct subject from the other tests keeps
     * this budget from being spent by them regardless of test execution order.
     */
    @Test
    void refusesAFurtherExportOnceTheCallersBudgetIsSpent() throws Exception {

        givenPermission(Permission.ORGANIZATION_EXPORT);
        var throttledCaller = "budget-spender";

        for (var i = 0; i < 5; i++) {
            mockMvc.perform(get("/axonia/admin/data-export").with(jwtFor(throttledCaller))).andExpect(status().isOk());
        }

        // a capacity-5 bucket refilling once per minute needs 12s for its next token on a clock that never advances
        mockMvc.perform(get("/axonia/admin/data-export").with(jwtFor(throttledCaller))).andExpect(status().is(429))
                .andExpect(jsonPath("$.code").value("EXPORT_RATE_LIMITED"))
                .andExpect(header().string("Retry-After", "12"));
    }

    private void givenPermission(Permission permission) {

        doNothing().when(gate).requirePermission(permission);
    }

    private void givenTheOrganization() {

        when(organizations.organization(ORGANIZATION_ID))
                .thenReturn(Optional.of(Organization.builder().id(ORGANIZATION_ID).name("Acme Engineering").build()));
        when(exportLabels.of(ORGANIZATION_ID)).thenReturn(Optional.of(PUBLIC_ADDRESS));
    }

    private static RequestPostProcessor jwtFor(String subject) {

        return jwt().jwt(token -> token.subject(subject).claim("org", ORGANIZATION_ID));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {

            return Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        RateLimiters rateLimiters() {

            return new RateLimiters(new FakeRateLimitStore(), new SimpleMeterRegistry());
        }
    }
}
