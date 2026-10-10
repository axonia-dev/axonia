package dev.axonia.events.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.api.SectionTemplate;
import dev.axonia.events.api.Session;
import dev.axonia.events.application.ReorderSectionsRequest;
import dev.axonia.events.application.SectionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/sessions/{sessionId}/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Session add(@PathVariable Long sessionId, @Valid @RequestBody SectionTemplate request) {

        return sectionService.addSection(sessionId, request);
    }

    @PutMapping("/{sectionId}")
    Session edit(@PathVariable Long sessionId, @PathVariable Long sectionId,
            @Valid @RequestBody SectionTemplate request) {

        return sectionService.updateSection(sessionId, sectionId, request);
    }

    @DeleteMapping("/{sectionId}")
    Session remove(@PathVariable Long sessionId, @PathVariable Long sectionId) {

        return sectionService.removeSection(sessionId, sectionId);
    }

    @PutMapping("/order")
    Session reorder(@PathVariable Long sessionId, @Valid @RequestBody ReorderSectionsRequest request) {

        return sectionService.reorderSections(sessionId, request);
    }
}
