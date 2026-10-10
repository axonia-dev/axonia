package dev.axonia.community.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.community.api.CommunicationSettings;
import dev.axonia.community.api.CommunityMother;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.community.api.FocalPoint;
import dev.axonia.community.application.CommunityPolicyService;
import dev.axonia.community.application.CommunityService;
import dev.axonia.community.application.CreateCommunityRequest;
import dev.axonia.community.application.LookAndFeelService;
import dev.axonia.community.application.UpdateCommunicationSettingsRequest;
import dev.axonia.community.application.UpdateCommunityRequest;
import dev.axonia.community.application.UpdateContributionPolicyRequest;
import dev.axonia.community.application.UpdateManagementPolicyRequest;
import dev.axonia.community.domain.LookAndFeelAsset;
import dev.axonia.community.domain.ManagementPolicy;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@CoreWebMvcTest(controllers = CommunityController.class)
class CommunityControllerTest extends ControllerTestSupport {

    @MockitoBean
    private CommunityService communityService;

    @MockitoBean
    private CommunityPolicyService communityPolicyService;

    @MockitoBean
    private LookAndFeelService lookAndFeelService;

    @Test
    void directory() throws JacksonException {

        // given
        var community = CommunityMother.any().build();
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "created_at"));
        var page = new PageImpl<>(List.of(community), pageable, 1);
        when(communityService.directory(eq("guild"), refEq(pageable))).thenReturn(page);

        // when
        var actual = mvc.get().uri("/axonia/communities").queryParam("page", "0").queryParam("size", "20")
                .queryParam("sort", "created_at,desc").queryParam("q", "guild");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.content[0].slug").isEqualTo("backend-guild");
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void directory_unsupportedSortIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/communities").queryParam("sort", "name,desc");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void getBySlug() throws JacksonException {

        // given
        var community = CommunityMother.any().build();
        when(communityService.getBySlug("backend-guild")).thenReturn(community);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void getBySlug_notFound() {

        // given
        when(communityService.getBySlug("missing")).thenThrow(new NotFoundException("missing"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}", "missing");

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void create() throws JacksonException {

        // given
        var community = CommunityMother.any().build();
        when(communityService.create(new CreateCommunityRequest("Backend Guild", 1L, "A guild for backend engineers")))
                .thenReturn(community);

        // when
        var actual = mvc.post().uri("/axonia/communities")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                        {"name":"Backend Guild","kindId":1,"description":"A guild for backend engineers"}""");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void create_blankNameRejected() {

        // when
        var actual = mvc.post().uri("/axonia/communities")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                        {"name":"","kindId":1,"description":"x"}""");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void create_missingKindIdRejected() {

        // when
        var actual = mvc.post().uri("/axonia/communities")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                        {"name":"Backend Guild","description":"x"}""");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void update() throws JacksonException {

        // given
        var community = CommunityMother.any().name("Renamed").build();
        when(communityService.update(eq("backend-guild"), refEq(new UpdateCommunityRequest("Renamed", 1L, "x"))))
                .thenReturn(community);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                        {"name":"Renamed","kindId":1,"description":"x"}""");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void archive() throws JacksonException {

        // given
        var community = CommunityMother.any().status(CommunityStatus.ARCHIVED).build();
        when(communityService.archive("backend-guild")).thenReturn(community);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/archive", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void archive_alreadyArchivedConflict() {

        // given
        when(communityService.archive("backend-guild"))
                .thenThrow(new ConflictException("COMMUNITY_ARCHIVED", "already archived"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/archive", "backend-guild");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_ARCHIVED");
    }

    @Test
    void contributionPolicy() throws JacksonException {

        // given
        var policy = new ContributionPolicy(false, false, true, false);
        when(communityPolicyService.getContributionPolicy("backend-guild")).thenReturn(policy);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/contribution-policy", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(policy));
    }

    @Test
    void updateContributionPolicy() throws JacksonException {

        // given
        var request = new UpdateContributionPolicyRequest(true, false, true, false);
        var policy = new ContributionPolicy(true, false, true, false);
        when(communityPolicyService.updateContributionPolicy("backend-guild", request)).thenReturn(policy);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/contribution-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(policy));
    }

    @Test
    void updateContributionPolicy_missingFieldRejected() throws JacksonException {

        // given a payload without canQueueTopics
        var body = "{\"canStartSeries\":true,\"canEditStructure\":false}";

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/contribution-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(body);

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void updateContributionPolicy_nonOrganizerForbidden() throws JacksonException {

        // given
        var request = new UpdateContributionPolicyRequest(true, true, true, false);
        when(communityPolicyService.updateContributionPolicy("backend-guild", request))
                .thenThrow(new ForbiddenException("Organizer role required"));

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/contribution-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void managementPolicy() throws JacksonException {

        // given
        var policy = new ManagementPolicy(false, false, true, false, false, false, false, false, false, false);
        when(communityPolicyService.getManagementPolicy("backend-guild")).thenReturn(policy);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/management-policy", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(policy));
    }

    @Test
    void updateManagementPolicy() throws JacksonException {

        // given
        var request = new UpdateManagementPolicyRequest(true, false, true, false, true, false, false, false, false,
                false);
        var policy = new ManagementPolicy(true, false, true, false, true, false, false, false, false, false);
        when(communityPolicyService.updateManagementPolicy("backend-guild", request)).thenReturn(policy);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/management-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(policy));
    }

    @Test
    void updateManagementPolicy_missingFieldRejected() throws JacksonException {

        // given a payload missing canArchiveCommunity
        var body = "{\"canEditCommunity\":true,\"canManageLookAndFeel\":false,\"canManageMembers\":true,"
                + "\"canManageContributionPolicy\":false}";

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/management-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(body);

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void updateManagementPolicy_appliesCreditContributions() throws JacksonException {

        // given
        var body = axesOtherThanCreditContributions();
        body.put("canCreditContributions", true);
        var request = new UpdateManagementPolicyRequest(false, false, false, false, false, false, false, false, false,
                true);
        var policy = new ManagementPolicy(false, false, false, false, false, false, false, false, false, true);
        when(communityPolicyService.updateManagementPolicy("backend-guild", request)).thenReturn(policy);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/management-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.canCreditContributions").isEqualTo(true);
    }

    @Test
    void updateManagementPolicy_missingCreditContributionsRejected() throws JacksonException {

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/management-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(axesOtherThanCreditContributions()));

        // then
        actual.assertThat().hasStatus(400);
    }

    private static Map<String, Boolean> axesOtherThanCreditContributions() {

        var axes = new LinkedHashMap<String, Boolean>();
        List.of("canEditCommunity", "canManageLookAndFeel", "canManageMembers", "canManageContributionPolicy",
                "canArchiveCommunity", "canCurateTags", "canViewHealth", "canInviteMembers", "canManageCommunication")
                .forEach(axis -> axes.put(axis, false));
        return axes;
    }

    @Test
    void updateManagementPolicy_nonOrganizerForbidden() throws JacksonException {

        // given
        var request = new UpdateManagementPolicyRequest(true, true, true, true, true, false, false, false, false,
                false);
        when(communityPolicyService.updateManagementPolicy("backend-guild", request))
                .thenThrow(new ForbiddenException("Organizer role required"));

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/management-policy", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void communicationSettings() throws JacksonException {

        // given
        var settings = new CommunicationSettings(true, false, 5, 72);
        when(communityPolicyService.getCommunicationSettings("backend-guild")).thenReturn(settings);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/communication-settings", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(settings));
    }

    @Test
    void updateCommunicationSettings() throws JacksonException {

        // given
        var request = new UpdateCommunicationSettingsRequest(true, false, 5, 72);
        var settings = new CommunicationSettings(true, false, 5, 72);
        when(communityPolicyService.updateCommunicationSettings("backend-guild", request)).thenReturn(settings);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/communication-settings", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(settings));
    }

    @Test
    void updateCommunicationSettings_missingFieldRejected() throws JacksonException {

        // given a payload without wrapUpGraceHours
        var body = "{\"notifySessionUpcoming\":true,\"notifySessionWrapUp\":false,\"sessionUpcomingLeadDays\":5}";

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/communication-settings", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(body);

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void updateCommunicationSettings_leadDaysOutOfRangeRejected() throws JacksonException {

        // given
        var body = "{\"notifySessionUpcoming\":true,\"notifySessionWrapUp\":false,\"sessionUpcomingLeadDays\":31,"
                + "\"wrapUpGraceHours\":72}";

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/communication-settings", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(body);

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void updateCommunicationSettings_nonOrganizerForbidden() throws JacksonException {

        // given
        var request = new UpdateCommunicationSettingsRequest(true, false, 5, 72);
        when(communityPolicyService.updateCommunicationSettings("backend-guild", request))
                .thenThrow(new ForbiddenException("Organizer role required"));

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/communication-settings", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void honoursExplicitApiVersionHeader() throws JacksonException {

        // given
        var community = CommunityMother.any().build();
        when(communityService.getBySlug("backend-guild")).thenReturn(community);

        // when the client requests the supported version via the header
        var actual = mvc.get().uri("/axonia/communities/{slug}", "backend-guild").header("X-API-Version", "1");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void rejectsUnsupportedApiVersion() {

        // when an unsupported version is requested via the header
        var actual = mvc.get().uri("/axonia/communities/{slug}", "backend-guild").header("X-API-Version", "99");

        // then the request is rejected before reaching the controller
        actual.assertThat().hasStatus(400);
    }

    @Test
    void setLookAndFeel_setsAccent() throws JacksonException {

        // given
        var community = CommunityMother.any().accentColor("#1AA6A0").build();
        when(lookAndFeelService.setAccentColor("backend-guild", "#1AA6A0")).thenReturn(community);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/look-and-feel", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content("{\"accentColor\":\"#1AA6A0\"}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.accentColor").isEqualTo("#1AA6A0");
    }

    @Test
    void setLookAndFeel_badHexRejected() {

        // given
        when(lookAndFeelService.setAccentColor("backend-guild", "teal"))
                .thenThrow(new ValidationException("INVALID_ACCENT_COLOR", "bad"));

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/look-and-feel", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content("{\"accentColor\":\"teal\"}");

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_ACCENT_COLOR");
    }

    @Test
    void addLookAndFeelAsset_uploadsBadge() throws JacksonException {

        // given
        var community = CommunityMother.any().badgeUrl("/public/media/communities/1/badge/x.png").build();
        when(lookAndFeelService.addLookAndFeelAsset(eq("backend-guild"), eq(LookAndFeelAsset.BADGE), any()))
                .thenReturn(community);
        var file = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        // when
        var actual = mvc.perform(multipart("/axonia/communities/backend-guild/look-and-feel/badge").file(file));

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.badgeUrl")
                .isEqualTo("/public/media/communities/1/badge/x.png");
    }

    @Test
    void removeLookAndFeelAsset_removesHero() throws JacksonException {

        // given
        var community = CommunityMother.any().build();
        when(lookAndFeelService.removeLookAndFeelAsset("backend-guild", LookAndFeelAsset.HERO)).thenReturn(community);

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/look-and-feel/{asset}", "backend-guild", "hero");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void lookAndFeelAsset_nonOrganizerForbidden() {

        // given
        when(lookAndFeelService.removeLookAndFeelAsset("backend-guild", LookAndFeelAsset.BADGE))
                .thenThrow(new ForbiddenException("Organizer role required"));

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/look-and-feel/{asset}", "backend-guild", "badge");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void addLookAndFeelAsset_unknownAssetReturns404() throws Exception {

        // given - "unknown" is not a valid LookAndFeelAsset segment; fromPath throws NotFoundException
        var file = new MockMultipartFile("file", "img.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        // when
        var actual = mvc.perform(multipart("/axonia/communities/backend-guild/look-and-feel/unknown").file(file));

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void toUpload_ioExceptionYieldsValidationError() throws Exception {

        // given - a MockMultipartFile whose getBytes() throws simulates a broken upload stream
        var broken = new MockMultipartFile("file", "img.png", MediaType.IMAGE_PNG_VALUE, new byte[]{0}) {

            @Override
            public byte[] getBytes() throws java.io.IOException {
                throw new java.io.IOException("simulated disk error");
            }
        };

        // when
        var actual = mvc.perform(multipart("/axonia/communities/backend-guild/look-and-feel/badge").file(broken));

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_IMAGE");
    }

    @Test
    void setFocalPoint_positionsHero() throws JacksonException {

        // given
        var community = CommunityMother.any().build();
        when(lookAndFeelService.setFocalPoint("backend-guild", LookAndFeelAsset.HERO, new FocalPoint(50, 78)))
                .thenReturn(community);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/look-and-feel/{asset}/focus", "backend-guild", "hero")
                .contentType(MediaType.APPLICATION_JSON).content("{\"x\":50,\"y\":78}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(community));
    }

    @Test
    void setFocalPoint_outOfRangeRejected() {

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/look-and-feel/{asset}/focus", "backend-guild", "hero")
                .contentType(MediaType.APPLICATION_JSON).content("{\"x\":140,\"y\":78}");

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_FOCAL_POINT");
    }
}
