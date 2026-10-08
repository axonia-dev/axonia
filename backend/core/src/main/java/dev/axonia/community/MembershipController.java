package dev.axonia.community;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import dev.axonia.Pageables;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/communities/{slug}/members")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    /**
     * {@code display_name} is a virtual sort key: it names no column and is resolved in the service rather than spliced
     * into SQL, the stance {@code ActionItemRepositoryCustomImpl.orderClause} already takes for its own virtual keys.
     */
    @GetMapping
    Page<Membership> listMembers(@PathVariable String slug,
            @PageableDefault(size = 20, sort = "joined_at", direction = Sort.Direction.DESC) Pageable pageable) {

        return membershipService.listMembers(slug,
                Pageables.requireSortIn(pageable, Set.of("joined_at", "display_name")));
    }

    @GetMapping("/search")
    List<CommunityMember> searchMembers(@PathVariable String slug, @RequestParam(defaultValue = "") String q) {

        return membershipService.searchMembers(slug, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Membership join(@PathVariable String slug) {

        return membershipService.join(slug);
    }

    @GetMapping("/me")
    Membership myMembership(@PathVariable String slug) {

        return membershipService.myMembership(slug);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable String slug) {

        membershipService.leave(slug);
    }

    @PutMapping("/{userId}/role")
    Membership changeRole(@PathVariable String slug, @PathVariable String userId,
            @Valid @RequestBody ChangeRoleRequest request) {

        return membershipService.changeRole(slug, userId, request.role());
    }
}
