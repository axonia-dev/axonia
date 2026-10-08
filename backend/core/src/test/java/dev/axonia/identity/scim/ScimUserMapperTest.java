package dev.axonia.identity.scim;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.internal.UserEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ScimUserMapperTest {

    private final ScimUserMapper mapper = new ScimUserMapper();

    private static UserEntity sampleUser() {

        return UserEntity.builder().scimId("scim-abc-123").email("alice@example.com").externalId("ext-001")
                .displayName("Alice Smith").active(true).build();
    }

    @Test
    void toScim_mapsSchemaUrn() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(scimUser.schemas()).containsExactly("urn:ietf:params:scim:schemas:core:2.0:User");
    }

    @Test
    void toScim_mapsScimIdToId() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(scimUser.id()).isEqualTo("scim-abc-123");
    }

    @Test
    void toScim_mapsEmailToEmailsAndUserName() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(scimUser.emails()).hasSize(1);
        assertThat(scimUser.emails().get(0).value()).isEqualTo("alice@example.com");
        assertThat(scimUser.userName()).isEqualTo("alice@example.com");
    }

    @Test
    void toScim_mapsActive() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(scimUser.active()).isTrue();
    }

    @Test
    void toScim_defaultsActiveToTrueWhenNull() {

        // given
        UserEntity user = UserEntity.builder().scimId("scim-xyz").email("bob@example.com").active(null).build();

        // when
        ScimUser scimUser = mapper.toScim(user);

        // then
        assertThat(scimUser.active()).isTrue();
    }

    @Test
    void toScim_setsMetaResourceType() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(scimUser.meta().resourceType()).isEqualTo("User");
    }

    @Test
    void scimListResponse_serializesResourcesCapitalized() throws Exception {

        // given
        ScimListResponse<ScimUser> listResponse = new ScimListResponse<>(
                List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"), 1, 1, 1,
                List.of(mapper.toScim(sampleUser())));

        // when
        String json = JsonMapper.builder().build().writeValueAsString(listResponse);

        // then
        assertThat(json).contains("\"Resources\"");
    }

    @Test
    void toScim_emailIsNull_usesExternalIdAsUserNameAndEmptyEmails() {

        // given
        UserEntity user = UserEntity.builder().scimId("scim-no-email").externalId("ext-no-email")
                .displayName("No Email").active(true).build();

        // when
        ScimUser scimUser = mapper.toScim(user);

        // then
        assertThat(scimUser.userName()).isEqualTo("ext-no-email");
        assertThat(scimUser.emails()).isEmpty();
    }

    @Test
    void getUserName_returnsScimUserUserName() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(mapper.getUserName(scimUser)).isEqualTo("alice@example.com");
    }

    @Test
    void getDisplayName_returnsScimUserDisplayName() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(mapper.getDisplayName(scimUser)).isEqualTo("Alice Smith");
    }

    @Test
    void getActive_returnsScimUserActive() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(mapper.getActive(scimUser)).isTrue();
    }

    @Test
    void getExternalId_returnsScimUserExternalId() {

        // when
        ScimUser scimUser = mapper.toScim(sampleUser());

        // then
        assertThat(mapper.getExternalId(scimUser)).isEqualTo("ext-001");
    }

    @Test
    void scimName_recordComponentsAreAccessible() {

        // given
        ScimName name = new ScimName("Alice Smith", "Smith", "Alice");

        // then
        assertThat(name.formatted()).isEqualTo("Alice Smith");
        assertThat(name.familyName()).isEqualTo("Smith");
        assertThat(name.givenName()).isEqualTo("Alice");
    }

    @Test
    void scimPatchRequest_serializesOperationsCapitalized() throws Exception {

        // given
        ScimPatchRequest patchRequest = new ScimPatchRequest(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                List.of(new ScimPatchOperation("replace", "active", false)));

        // when
        String json = JsonMapper.builder().build().writeValueAsString(patchRequest);

        // then
        assertThat(json).contains("\"Operations\"");
    }

    @Test
    void getters_translateScimEnterpriseFieldsToAxoniaVocabulary() {

        // given — SCIM organization→business_unit, division→department, department→team
        ScimEnterpriseUser ent = new ScimEnterpriseUser("Acme Group", "Platform", "Payments");
        ScimUser in = ScimUser.builder().id("id").externalId("ext").userName("alice@acme.test").displayName("Alice")
                .emails(List.of()).active(true).meta(new ScimMeta("User", null, null, null)).enterprise(ent).build();

        // then
        assertThat(mapper.getBusinessUnit(in)).isEqualTo("Acme Group");
        assertThat(mapper.getDepartment(in)).isEqualTo("Platform");
        assertThat(mapper.getTeam(in)).isEqualTo("Payments");
    }

    @Test
    void getters_returnNullWhenNoEnterpriseExtension() {

        // given
        ScimUser in = ScimUser.builder().id("id").externalId("ext").userName("bob@acme.test").displayName("Bob")
                .emails(List.of()).active(true).meta(new ScimMeta("User", null, null, null)).build();

        // then
        assertThat(mapper.getBusinessUnit(in)).isNull();
        assertThat(mapper.getDepartment(in)).isNull();
        assertThat(mapper.getTeam(in)).isNull();
    }

    @Test
    void toScim_emitsEnterpriseExtensionAndUrn_whenEntityHasUnit() {

        // given
        UserEntity user = UserEntity.builder().scimId("scim-1").email("alice@acme.test").displayName("Alice")
                .active(true).businessUnit("Acme Group").department("Platform").team("Payments").build();

        // when
        ScimUser scimUser = mapper.toScim(user);

        // then
        assertThat(scimUser.schemas()).contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        assertThat(scimUser.enterprise().organization()).isEqualTo("Acme Group");
        assertThat(scimUser.enterprise().division()).isEqualTo("Platform");
        assertThat(scimUser.enterprise().department()).isEqualTo("Payments");
    }

    @Test
    void toScim_omitsEnterpriseExtension_whenEntityHasNoUnit() {

        // given
        UserEntity user = UserEntity.builder().scimId("scim-2").email("bob@acme.test").displayName("Bob").active(true)
                .build();

        // when
        ScimUser scimUser = mapper.toScim(user);

        // then
        assertThat(scimUser.enterprise()).isNull();
        assertThat(scimUser.schemas()).containsExactly("urn:ietf:params:scim:schemas:core:2.0:User");
    }
}
