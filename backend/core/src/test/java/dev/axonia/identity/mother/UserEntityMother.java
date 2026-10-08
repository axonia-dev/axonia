package dev.axonia.identity.mother;

import dev.axonia.identity.internal.UserEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserEntityMother {

    public static UserEntity.UserEntityBuilder any() {

        return UserEntity.builder().organizationId("default").userId("11111111-1111-1111-1111-111111111111")
                .displayName("Alice Anderson").email("alice@axonia.example")
                .scimId("11111111-1111-1111-1111-111111111111").avatarMediaKey("avatars/default/alice.png");
    }
}
