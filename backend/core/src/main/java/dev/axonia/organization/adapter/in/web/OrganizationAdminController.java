package dev.axonia.organization.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.organization.application.OrganizationAdminService;
import dev.axonia.organization.domain.CurrentOrganization;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/organizations")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private final OrganizationAdminService service;

    @GetMapping("/current")
    CurrentOrganization current() {
        return service.current();
    }

    @PutMapping("/current/communications-enabled")
    CurrentOrganization setCommunicationsEnabled(@Valid @RequestBody UpdateCommunicationsEnabledRequest request) {
        return service.setCommunicationsEnabled(request.enabled());
    }
}
