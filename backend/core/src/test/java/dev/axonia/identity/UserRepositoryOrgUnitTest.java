package dev.axonia.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.internal.UserArrival;
import dev.axonia.identity.internal.UserEntity;
import dev.axonia.identity.internal.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryOrgUnitTest extends CoreJdbcTestSupport {

    @Autowired
    UserRepository repository;

    @Test
    void persistsAndReadsBackOrgUnitLabels() {

        // given
        repository.save(UserEntity.builder().organizationId("default").userId("sub-ou-1").scimId("scim-ou-1")
                .arrival(UserArrival.SYNCED).displayName("Alice").email("alice@acme.test").active(true)
                .businessUnit("Acme Group").department("Platform").team("Payments").build());

        // when
        var found = repository.findByOrganizationIdAndUserId("default", "sub-ou-1");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getBusinessUnit()).isEqualTo("Acme Group");
        assertThat(found.get().getDepartment()).isEqualTo("Platform");
        assertThat(found.get().getTeam()).isEqualTo("Payments");
    }

    @Test
    void selfServeUserHasNullOrgUnitLabels() {

        // given
        repository.save(UserEntity.builder().organizationId("default").userId("sub-ou-2").scimId("scim-ou-2")
                .arrival(UserArrival.SYNCED).displayName("Bob").email("bob@acme.test").active(true).build());

        // when
        var found = repository.findByOrganizationIdAndUserId("default", "sub-ou-2");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getBusinessUnit()).isNull();
        assertThat(found.get().getDepartment()).isNull();
        assertThat(found.get().getTeam()).isNull();
    }
}
