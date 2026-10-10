package dev.axonia.community.adapter.in.web;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import dev.axonia.community.api.CommunicationSettings;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.community.api.FocalPoint;
import dev.axonia.community.application.CommunityPolicyService;
import dev.axonia.community.application.CommunityService;
import dev.axonia.community.application.CreateCommunityRequest;
import dev.axonia.community.application.LookAndFeelService;
import dev.axonia.community.application.UpdateCommunicationSettingsRequest;
import dev.axonia.community.application.UpdateCommunityRequest;
import dev.axonia.community.application.UpdateContributionPolicyRequest;
import dev.axonia.community.application.UpdateManagementPolicyRequest;
import dev.axonia.community.domain.LookAndFeelAsset;
import dev.axonia.community.domain.ManagementPolicy;
import dev.axonia.media.api.ImageUploads;
import dev.axonia.shared.api.web.Pageables;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityPolicyService communityPolicyService;
    private final LookAndFeelService lookAndFeelService;

    @GetMapping
    Page<Community> directory(@RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {

        return communityService.directory(q, Pageables.requireSortIn(pageable, Set.of("created_at")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Community create(@Valid @RequestBody CreateCommunityRequest request) {

        return communityService.create(request);
    }

    @GetMapping("/{slug}")
    Community getBySlug(@PathVariable String slug) {

        return communityService.getBySlug(slug);
    }

    @PutMapping("/{slug}")
    Community update(@PathVariable String slug, @Valid @RequestBody UpdateCommunityRequest request) {

        return communityService.update(slug, request);
    }

    @PostMapping("/{slug}/archive")
    Community archive(@PathVariable String slug) {

        return communityService.archive(slug);
    }

    @GetMapping("/{slug}/contribution-policy")
    ContributionPolicy contributionPolicy(@PathVariable String slug) {

        return communityPolicyService.getContributionPolicy(slug);
    }

    @PutMapping("/{slug}/contribution-policy")
    ContributionPolicy updateContributionPolicy(@PathVariable String slug,
            @Valid @RequestBody UpdateContributionPolicyRequest request) {

        return communityPolicyService.updateContributionPolicy(slug, request);
    }

    @GetMapping("/{slug}/management-policy")
    ManagementPolicy managementPolicy(@PathVariable String slug) {

        return communityPolicyService.getManagementPolicy(slug);
    }

    @PutMapping("/{slug}/management-policy")
    ManagementPolicy updateManagementPolicy(@PathVariable String slug,
            @Valid @RequestBody UpdateManagementPolicyRequest request) {

        return communityPolicyService.updateManagementPolicy(slug, request);
    }

    @GetMapping("/{slug}/communication-settings")
    CommunicationSettings communicationSettings(@PathVariable String slug) {

        return communityPolicyService.getCommunicationSettings(slug);
    }

    @PutMapping("/{slug}/communication-settings")
    CommunicationSettings updateCommunicationSettings(@PathVariable String slug,
            @Valid @RequestBody UpdateCommunicationSettingsRequest request) {

        return communityPolicyService.updateCommunicationSettings(slug, request);
    }

    @PutMapping("/{slug}/look-and-feel")
    Community setLookAndFeel(@PathVariable String slug, @RequestBody SetLookAndFeelRequest request) {

        return lookAndFeelService.setAccentColor(slug, request.accentColor());
    }

    @PostMapping(path = "/{slug}/look-and-feel/{asset}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Community addLookAndFeelAsset(@PathVariable String slug, @PathVariable String asset,
            @RequestParam("file") MultipartFile file) {

        return lookAndFeelService.addLookAndFeelAsset(slug, LookAndFeelAsset.fromPath(asset), ImageUploads.from(file));
    }

    @DeleteMapping("/{slug}/look-and-feel/{asset}")
    Community removeLookAndFeelAsset(@PathVariable String slug, @PathVariable String asset) {

        return lookAndFeelService.removeLookAndFeelAsset(slug, LookAndFeelAsset.fromPath(asset));
    }

    @PutMapping("/{slug}/look-and-feel/{asset}/focus")
    Community setFocalPoint(@PathVariable String slug, @PathVariable String asset,
            @RequestBody SetFocalPointRequest request) {

        return lookAndFeelService.setFocalPoint(slug, LookAndFeelAsset.fromPath(asset),
                FocalPoint.of(request.x(), request.y()));
    }
}
