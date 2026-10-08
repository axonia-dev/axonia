package dev.axonia.health;

import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/communities/{slug}/health")
    HealthSnapshot snapshot(@PathVariable String slug,
            @RequestParam(defaultValue = "90d") @Pattern(regexp = "[1-9]\\d{0,2}d") String window) {

        return healthService.snapshot(slug, Integer.parseInt(window.substring(0, window.length() - 1)));
    }
}
