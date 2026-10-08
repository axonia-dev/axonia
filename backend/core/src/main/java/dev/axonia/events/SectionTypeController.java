package dev.axonia.events;

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
@RequestMapping("/${spring.application.name}/section-types")
@RequiredArgsConstructor
public class SectionTypeController {

    private final SectionTypeService sectionTypeService;

    @GetMapping
    List<SectionType> list() {

        return sectionTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SectionType create(@Valid @RequestBody CreateSectionTypeRequest request) {

        return sectionTypeService.create(request);
    }

    @PutMapping("/{id}")
    SectionType update(@PathVariable Long id, @Valid @RequestBody UpdateSectionTypeRequest request) {

        return sectionTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @RequestParam(required = false) Long reassignTo) {

        sectionTypeService.delete(id, reassignTo);
    }
}
