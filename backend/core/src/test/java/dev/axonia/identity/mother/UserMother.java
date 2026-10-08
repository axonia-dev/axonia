package dev.axonia.identity.mother;

import dev.axonia.identity.User;
import dev.axonia.identity.UserDirectory;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserMother {

    public static User.UserBuilder any() {

        return User.builder().id("11111111-1111-1111-1111-111111111111").displayName("Alice Anderson")
                .email("alice@axonia.example").avatarUrl("https://media.example.test/avatars/default/alice.png")
                .memberStatus(UserDirectory.MemberStatus.PRESENT);
    }
}
