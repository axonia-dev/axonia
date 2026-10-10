package dev.axonia.events.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.application.MemberZoneService;
import dev.axonia.events.domain.MemberZone;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class MemberZoneController {

    private final MemberZoneService memberZoneService;

    /** A bare array, matching the other list routes: no envelope to version, and no record holding a List. */
    @GetMapping("/communities/{slug}/member-zones")
    List<MemberZone> memberZones(@PathVariable String slug) {

        return memberZoneService.forCommunity(slug);
    }
}
