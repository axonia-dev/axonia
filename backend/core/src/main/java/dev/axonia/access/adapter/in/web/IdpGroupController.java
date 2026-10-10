package dev.axonia.access.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.access.application.GroupBindingService;
import dev.axonia.access.application.IdpGroupsResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class IdpGroupController {

    private final GroupBindingService groupBindingService;

    @GetMapping("/admin/idp-groups")
    IdpGroupsResponse groups() {

        return groupBindingService.list();
    }
}
