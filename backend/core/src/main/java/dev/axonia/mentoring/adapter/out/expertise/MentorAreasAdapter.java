package dev.axonia.mentoring.adapter.out.expertise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.api.OpenDoors;
import dev.axonia.mentoring.application.port.out.MentorAreas;
import lombok.RequiredArgsConstructor;

@Component("mentoringMentorAreasAdapter")
@RequiredArgsConstructor
class MentorAreasAdapter implements MentorAreas {

    private final OpenDoors openDoors;

    @Override
    public Map<String, List<String>> declaredIn(String slug) {

        var byMember = new LinkedHashMap<String, List<String>>();
        for (var area : openDoors.declaredIn(slug)) {
            for (var person : area.people()) {
                byMember.computeIfAbsent(person.userId(), id -> new ArrayList<>()).add(area.canonicalTag());
            }
        }
        return byMember;
    }
}
