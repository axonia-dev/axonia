package dev.axonia.communication;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class NotificationCentreController {

    private final NotificationCentreService notificationCentreService;

    /**
     * {@code before} and {@code beforeId} are the last entry of the previous page and are absent on the first one. They
     * travel as two parameters rather than one opaque cursor because both are already public in the response, and an
     * encoded cursor would hide nothing while making a failing request harder to read.
     */
    @GetMapping("/me/notifications")
    NotificationPage notifications(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "20") int size) {
        return notificationCentreService.page(before, beforeId, size);
    }

    @GetMapping("/me/notifications/unread-count")
    UnreadCount unreadCount() {
        return new UnreadCount(notificationCentreService.unreadCount());
    }

    @PostMapping("/me/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable long id) {
        notificationCentreService.markRead(id);
    }

    @PostMapping("/me/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead() {
        notificationCentreService.markAllRead();
    }

    record UnreadCount(int count) {
    }
}
