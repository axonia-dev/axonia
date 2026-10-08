package dev.axonia.access;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final RoleBindingService bindingService;

    @GetMapping("/admin/users")
    Page<AdminUser> users(@RequestParam(required = false, defaultValue = "") String q,
            @PageableDefault(size = 20) Pageable pageable) {

        return adminUserService.list(q, pageable);
    }

    @GetMapping("/admin/users/{userId}")
    AdminUser user(@PathVariable String userId) {

        return adminUserService.byId(userId);
    }

    @GetMapping("/roles/{id}/assignees")
    List<RoleBinding> assignees(@PathVariable Long id) {

        return bindingService.assigneesOf(id);
    }
}
