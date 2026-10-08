package dev.axonia.access;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
