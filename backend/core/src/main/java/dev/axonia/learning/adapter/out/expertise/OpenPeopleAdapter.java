package dev.axonia.learning.adapter.out.expertise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.api.OpenArea;
import dev.axonia.expertise.api.OpenDoors;
import dev.axonia.learning.api.OpenFace;
import dev.axonia.learning.application.port.out.OpenPeople;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenPeopleAdapter implements OpenPeople {

    private final OpenDoors openDoors;

    @Override
    public Map<String, List<OpenFace>> byArea(String slug) {

        var byArea = new LinkedHashMap<String, List<OpenFace>>();
        for (OpenArea area : openDoors.openIn(slug)) {
            byArea.put(area.canonicalTag(), area.people().stream()
                    .map(person -> new OpenFace(person.userId(), person.displayName(), person.avatarUrl())).toList());
        }
        return byArea;
    }
}
