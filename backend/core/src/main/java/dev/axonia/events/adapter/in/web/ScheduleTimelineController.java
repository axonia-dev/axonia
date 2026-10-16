package dev.axonia.events.adapter.in.web;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.api.MemoryCursor;
import dev.axonia.events.api.MemoryPage;
import dev.axonia.events.api.Ribbon;
import dev.axonia.events.application.MemoryService;
import dev.axonia.events.application.ScheduleRibbonService;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * No class-level {@code @Validated}: MVC's own method validation answers 400 on {@code size}, the AOP proxy would not.
 */
@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
class ScheduleTimelineController {

    private final MemoryService memoryService;
    private final ScheduleRibbonService scheduleRibbonService;

    /** {@code before} and {@code beforeId} are the previous page's {@code next}: both, or neither on the first page. */
    @GetMapping("/communities/{slug}/schedule/memories")
    MemoryPage memories(@PathVariable String slug,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {

        if (before == null != (beforeId == null)) {
            throw new ValidationException("MEMORY_CURSOR_INCOMPLETE",
                    "A memories cursor needs both before and beforeId");
        }
        return memoryService.memories(slug, before == null ? null : new MemoryCursor(before, beforeId), size);
    }

    @GetMapping("/communities/{slug}/schedule/ribbon")
    Ribbon ribbon(@PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return scheduleRibbonService.ribbon(slug, from, to);
    }
}
