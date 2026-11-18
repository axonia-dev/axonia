package dev.axonia.mentoring.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.mentoring.api.Availability;
import dev.axonia.mentoring.api.AvailabilityRequest;
import dev.axonia.mentoring.api.DirectoryPerson;
import dev.axonia.mentoring.api.MentoringPage;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.api.ProposeRequest;
import dev.axonia.mentoring.api.ShownPairing;
import dev.axonia.mentoring.api.ShownPairing.DirectoryFace;
import dev.axonia.mentoring.application.AvailabilityService;
import dev.axonia.mentoring.application.MentoringReads;
import dev.axonia.mentoring.application.ProposalService;
import dev.axonia.shared.api.error.ConflictException;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = GuildMentoringController.class)
class GuildMentoringControllerTest extends ControllerTestSupport {

    @MockitoBean
    private MentoringReads mentoringReads;
    @MockitoBean
    private AvailabilityService availabilityService;
    @MockitoBean
    private ProposalService proposalService;

    @Test
    void thePageHasTheShapeTheFrontendParses() {

        // given
        when(mentoringReads.page("platform")).thenReturn(MentoringPage.builder().enabled(true).member(true)
                .archived(false).mine(new Availability(true, 3, false))
                .mentors(List.of(new DirectoryPerson("u1", "Lea", null, List.of("kafka"), true, null, null)))
                .mentees(List.of(new DirectoryPerson("u2", "Ana", "a.png", List.of(), false, "DECLINED_RECENTLY",
                        LocalDate.parse("2026-12-16"))))
                .canMentorOn(List.of("kafka", "helm")).wantsToGrowIn(List.of()).guidedStart(false)
                .pairings(List.of(new ShownPairing(9L, new DirectoryFace("u1", "Lea", null),
                        new DirectoryFace("u3", "Max", "m.png"), LocalDate.parse("2026-11-01"))))
                .build());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/mentoring", "platform").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"enabled": true, "member": true, "archived": false,
                 "mine": {"takingMentees": true, "menteeLimit": 3, "lookingForMentor": false},
                 "mentors": [{"userId": "u1", "displayName": "Lea", "avatarUrl": null, "areas": ["kafka"],
                              "proposable": true, "proposalState": null, "proposalsOpenOn": null}],
                 "mentees": [{"userId": "u2", "displayName": "Ana", "avatarUrl": "a.png", "areas": [],
                              "proposable": false, "proposalState": "DECLINED_RECENTLY",
                              "proposalsOpenOn": "2026-12-16"}],
                 "canMentorOn": ["kafka", "helm"], "wantsToGrowIn": [], "guidedStart": false,
                 "pairings": [{"id": 9, "mentor": {"userId": "u1", "displayName": "Lea", "avatarUrl": null},
                               "mentee": {"userId": "u3", "displayName": "Max", "avatarUrl": "m.png"},
                               "startedOn": "2026-11-01"}]}
                """);
    }

    @Test
    void settingAvailabilityAnswersTheStoredValue() {

        // given
        when(availabilityService.set("platform", new AvailabilityRequest(true, null, true)))
                .thenReturn(new Availability(true, null, true));

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/mentoring/availability", "platform")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"takingMentees\": true, \"menteeLimit\": null, \"lookingForMentor\": true}").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"takingMentees": true, "menteeLimit": null, "lookingForMentor": true}
                """);
    }

    @Test
    void anAvailabilityMissingASwitchIsRefusedRatherThanReadAsOff() {

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/mentoring/availability", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{\"takingMentees\": true}").exchange();

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(availabilityService);
    }

    @Test
    void aSwitchedOffGuildIsAConflictWithItsCode() {

        // given
        when(mentoringReads.page("platform"))
                .thenThrow(new ConflictException("MENTORING_OFF", "Mentoring is switched off"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/mentoring", "platform").exchange();

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("MENTORING_OFF");
    }

    @Test
    void aProposalIsCreatedWithTheShapeTheFrontendParses() {

        // given
        var request = new ProposeRequest("u1", "MENTEE", List.of("Kafka"), "Hello");
        when(proposalService.propose("platform", request)).thenReturn(ProposalView.builder().id(40L)
                .communitySlug("platform").communityName("Platform").proposedBy("MENTEE").partnerId("u1")
                .partnerName("Lea").topics(List.of("Kafka")).message("Hello").status("OPEN")
                .createdAt(Instant.parse("2026-11-16T10:00:00Z")).lapsesOn(LocalDate.parse("2026-12-16")).build());

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/mentoring/proposals", "platform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"toUserId\": \"u1\", \"asRole\": \"MENTEE\", \"topics\": [\"Kafka\"], \"message\": \"Hello\"}")
                .exchange();

        // then
        actual.assertThat().hasStatus(201).bodyJson().isStrictlyEqualTo("""
                {"id": 40, "communitySlug": "platform", "communityName": "Platform", "proposedBy": "MENTEE",
                 "partnerId": "u1", "partnerName": "Lea", "partnerAvatarUrl": null, "topics": ["Kafka"],
                 "message": "Hello", "status": "OPEN", "createdAt": "2026-11-16T10:00:00Z",
                 "reopenableUntil": null, "lapsesOn": "2026-12-16"}
                """);
    }
}
