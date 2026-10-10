package dev.axonia.communication.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.NotificationPreferenceService;
import dev.axonia.communication.domain.NotificationPreference;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping("/me/notification-preferences")
    List<NotificationPreference> preferences() {
        return notificationPreferenceService.preferences();
    }

    @PutMapping("/me/notification-preferences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void set(@Valid @RequestBody SetPreferenceRequest request) {
        notificationPreferenceService.set(request.type(), request.enabled());
    }

    record SetPreferenceRequest(@NotNull MessageType type, boolean enabled) {
    }
}
