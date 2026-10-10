package dev.axonia.events.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.application.CalendarSubscriptionService;
import dev.axonia.events.domain.CalendarSubscription;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * The member's own end of a calendar subscription, on the signed-in surface: what the dialog reads, what obtaining a
 * URL calls, and what invalidating one calls.
 *
 * <p>
 * {@code POST} is find-or-create and answers 200 rather than 201, because a member who already has a subscription gets
 * that same subscription back rather than a second one. It comes back without its URL: the token was handed over once
 * and never stored, so anyone who has lost theirs resets rather than subscribes again.
 */
@RestController
@RequestMapping("/${spring.application.name}/communities/{slug}/calendar-subscription")
@RequiredArgsConstructor
public class CalendarSubscriptionController {

    private final CalendarSubscriptionService calendarSubscriptionService;

    @GetMapping
    CalendarSubscription current(@PathVariable String slug) {

        return calendarSubscriptionService.current(slug).orElseThrow(() -> new NotFoundException("Calendar not found"));
    }

    @PostMapping
    CalendarSubscription subscribe(@PathVariable String slug) {

        return calendarSubscriptionService.subscribe(slug);
    }

    @PostMapping("/reset")
    CalendarSubscription reset(@PathVariable String slug) {

        return calendarSubscriptionService.reset(slug);
    }
}
