package dev.axonia.events.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/** A presenter reference: a display name or an email, resolved against the community's members. */
public record AddPresenterRequest(@NotBlank String presenter) {
}
