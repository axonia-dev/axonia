package dev.axonia.knowledge;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/artifact-types")
@RequiredArgsConstructor
public class ArtifactTypeController {

    private final ArtifactTypeService artifactTypeService;

    @GetMapping
    List<ArtifactType> list() {

        return artifactTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ArtifactType create(@Valid @RequestBody CreateArtifactTypeRequest request) {

        return artifactTypeService.create(request);
    }

    @PutMapping("/{id}")
    ArtifactType update(@PathVariable Long id, @Valid @RequestBody UpdateArtifactTypeRequest request) {

        return artifactTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @RequestParam(required = false) Long reassignTo) {

        artifactTypeService.delete(id, reassignTo);
    }
}
