package dev.axonia.selfhosted;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import tools.jackson.databind.json.JsonMapper;

import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_ID;
import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.OWNER_GROUP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The community repair hatch (D-B8) as an installation meets it: its Owners are the directory group the token names,
 * never a direct binding, so an Owner repairs a community left with no organizer through a GROUP binding alone.
 */
@SelfHostedSpringBootTest
class SelfHostedOwnerRepairTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockOidcIssuer issuer;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void anOwnerThroughTheDirectoryGroupRepairsACommunityLeftWithNoOrganizer() throws SQLException {

        // given
        var ownerSubject = unique("group-owner");
        var owner = issuer.token(ownerSubject, Map.of("groups", List.of(OWNER_GROUP)));
        var memberSubject = unique("stranded-member");
        var member = issuer.token(memberSubject, Map.of("name", "Stranded Member"));
        var slug = communityCreatedBy(owner);
        assertThat(mvc.post().uri("/axonia/communities/{slug}/members", slug).with(SelfHostedRequests.signedIn(member))
                .exchange()).hasStatus(HttpStatus.CREATED);
        SelfHostedDatabase
                .asOwner("DELETE FROM memberships WHERE role = 'ORGANIZER' AND community_id = " + communityId(slug));
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM role_binding WHERE subject_type = 'USER'"
                + " AND subject_id = '" + ownerSubject + "'")).isZero();

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/members/{userId}/role", slug, memberSubject)
                .with(SelfHostedRequests.signedIn(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ORGANIZER\"}").exchange();

        // then
        assertThat(actual).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("ORGANIZER");
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM memberships WHERE role = 'ORGANIZER'"
                + " AND community_id = " + communityId(slug) + " AND user_id = '" + memberSubject + "'")).isOne();
    }

    private String communityCreatedBy(String owner) {

        var kinds = mvc.get().uri("/axonia/community-kinds").with(SelfHostedRequests.signedIn(owner)).exchange();
        assertThat(kinds).hasStatusOk();
        var kindId = jsonMapper.readTree(kinds.getResponse().getContentAsByteArray()).get(0).path("id").asLong();
        var created = mvc.post().uri("/axonia/communities").with(SelfHostedRequests.signedIn(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("name", unique("Stranded community"), "kindId", kindId)))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return jsonMapper.readTree(created.getResponse().getContentAsByteArray()).path("slug").asString();
    }

    private static String communityId(String slug) {

        return "(SELECT id FROM communities WHERE organization_id = '" + ORGANIZATION_ID + "' AND slug = '" + slug
                + "')";
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
