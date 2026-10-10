package dev.axonia.installation.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MailServerSaveRequest(@NotNull @Valid MailServerValuesRequest values, @NotBlank String testToken,
        @NotNull Long baseRevision) {
}
