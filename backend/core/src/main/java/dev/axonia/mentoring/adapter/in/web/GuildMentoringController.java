package dev.axonia.mentoring.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.mentoring.api.Availability;
import dev.axonia.mentoring.api.AvailabilityRequest;
import dev.axonia.mentoring.api.MentoringPage;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.api.ProposeRequest;
import dev.axonia.mentoring.application.AvailabilityService;
import dev.axonia.mentoring.application.MentoringReads;
import dev.axonia.mentoring.application.ProposalService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/communities/{slug}/mentoring")
@RequiredArgsConstructor
class GuildMentoringController {

    private final MentoringReads mentoringReads;
    private final AvailabilityService availabilityService;
    private final ProposalService proposalService;

    @GetMapping
    MentoringPage page(@PathVariable String slug) {

        return mentoringReads.page(slug);
    }

    @PutMapping("/availability")
    Availability setAvailability(@PathVariable String slug, @Valid @RequestBody AvailabilityRequest request) {

        return availabilityService.set(slug, request);
    }

    @PostMapping("/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    ProposalView propose(@PathVariable String slug, @RequestBody ProposeRequest request) {

        return proposalService.propose(slug, request);
    }
}
