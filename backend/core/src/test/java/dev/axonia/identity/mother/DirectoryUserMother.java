package dev.axonia.identity.mother;

import dev.axonia.identity.DirectoryUser;
import dev.axonia.identity.UserDirectory;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DirectoryUserMother {

    public static DirectoryUser.DirectoryUserBuilder any() {

        return DirectoryUser.builder().id("11111111-1111-1111-1111-111111111111").displayName("Alice Anderson")
                .avatarUrl("https://media.example.test/avatars/default/alice.png")
                .memberStatus(UserDirectory.MemberStatus.PRESENT);
    }
}
