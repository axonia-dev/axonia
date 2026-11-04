package dev.axonia.community.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.community.api.CreditablePerson;
import dev.axonia.community.application.MembershipService;
import lombok.RequiredArgsConstructor;

/** Who a member of this community may credit: anybody in the organization, by name, members first. */
@RestController
@RequestMapping("/${spring.application.name}/communities/{slug}/people")
@RequiredArgsConstructor
public class PeopleController {

    private final MembershipService membershipService;

    @GetMapping
    List<CreditablePerson> searchPeople(@PathVariable String slug, @RequestParam(defaultValue = "") String q) {

        return membershipService.searchPeople(slug, q);
    }
}
