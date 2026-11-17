package dev.axonia.mentoring.adapter.out.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.MemberProfiles;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.MentoringPerson;
import lombok.RequiredArgsConstructor;

@Component("mentoringPersonDirectoryAdapter")
@RequiredArgsConstructor
class PersonDirectoryAdapter implements PersonDirectory {

    private final MemberProfiles memberProfiles;
    private final UserDirectory userDirectory;

    @Override
    public Map<String, MentoringPerson> resolve(Collection<String> userIds) {

        var found = memberProfiles.resolve(userIds).stream().collect(
                Collectors.toMap(u -> u.id(), u -> new MentoringPerson(u.id(), u.displayName(), u.avatarUrl())));
        return userIds.stream().distinct().collect(
                Collectors.toMap(Function.identity(), id -> found.getOrDefault(id, MentoringPerson.deleted(id))));
    }

    @Override
    public String displayName(String org, String userId) {

        return memberProfiles.displayName(org, userId).orElse(MentoringPerson.DELETED_MEMBER);
    }

    @Override
    public boolean isPresent(String org, String userId) {

        return userDirectory.presentOf(org, List.of(userId)).contains(userId);
    }
}
