package dev.axonia.mentoring.adapter.out.identity;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.MemberProfiles;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import lombok.RequiredArgsConstructor;

@Component("mentoringMemberClocksAdapter")
@RequiredArgsConstructor
class MemberClocksAdapter implements MemberClocks {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private final MemberProfiles memberProfiles;

    @Override
    public ZoneId zoneOf(String org, String userId) {

        return zonesOf(org, List.of(userId)).get(userId);
    }

    @Override
    public Map<String, ZoneId> zonesOf(String org, Collection<String> userIds) {

        var known = memberProfiles.timeZones(org, userIds);
        return userIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> zone(known.get(id))));
    }

    private static ZoneId zone(String id) {

        if (id == null) {
            return UTC;
        }
        try {
            return ZoneId.of(id);
        } catch (DateTimeException unknown) {
            return UTC;
        }
    }
}
