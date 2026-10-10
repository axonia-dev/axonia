package dev.axonia.expertise.domain;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.api.UserDirectory;

import static org.assertj.core.api.Assertions.assertThat;

class PersonTest {

    @Test
    void unresolved_isNamedLikeADeletedMemberEverywhereElse() {

        assertThat(Person.unresolved("ghost"))
                .isEqualTo(new Person("ghost", UserDirectory.DirectoryUser.DELETED_MEMBER_LABEL, null, null));
    }
}
