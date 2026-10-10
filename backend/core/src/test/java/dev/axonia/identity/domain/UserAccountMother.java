package dev.axonia.identity.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UserAccountMother {

    public static UserAccount.UserAccountBuilder any() {

        return UserAccount.builder().organizationId("default").userId("11111111-1111-1111-1111-111111111111")
                .displayName("Alice Anderson").email("alice@axonia.example")
                .scimId("11111111-1111-1111-1111-111111111111").avatarMediaKey("avatars/default/alice.png");
    }
}
