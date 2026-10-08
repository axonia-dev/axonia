package dev.axonia.installation.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record MailServerSaveRequest(@NotNull @Valid MailServerValuesRequest values, @NotBlank String testToken,
        @NotNull Long baseRevision) {
}
