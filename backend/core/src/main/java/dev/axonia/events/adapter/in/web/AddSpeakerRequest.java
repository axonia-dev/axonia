package dev.axonia.events.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * A speaker reference: a user id, a display name or an email, resolved against anybody in the organization, members of
 * the community first, as for a talk's presenters.
 */
public record AddSpeakerRequest(@NotBlank String speaker) {
}
