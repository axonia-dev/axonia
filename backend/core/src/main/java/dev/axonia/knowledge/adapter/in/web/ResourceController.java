package dev.axonia.knowledge.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.knowledge.api.AttachResourceRequest;
import dev.axonia.knowledge.api.Attachment;
import dev.axonia.knowledge.api.SessionResource;
import dev.axonia.knowledge.application.ResourceService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/communities/{slug}/sessions/{sessionId}/resources")
    List<SessionResource> resources(@PathVariable String slug, @PathVariable Long sessionId) {

        return resourceService.resourcesForSession(slug, sessionId);
    }

    @PostMapping("/communities/{slug}/sessions/{sessionId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    Attachment attach(@PathVariable String slug, @PathVariable Long sessionId,
            @Valid @RequestBody AttachResourceRequest request) {

        return resourceService.attach(slug, sessionId, request);
    }

    @DeleteMapping("/communities/{slug}/sessions/{sessionId}/resources/{artifactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void detach(@PathVariable String slug, @PathVariable Long sessionId, @PathVariable Long artifactId,
            @RequestParam(required = false) Long segmentId) {

        resourceService.detach(slug, sessionId, artifactId, segmentId);
    }
}
