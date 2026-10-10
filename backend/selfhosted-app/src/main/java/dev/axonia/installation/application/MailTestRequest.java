package dev.axonia.installation.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MailTestRequest(@NotNull @Valid MailServerValuesRequest values,
        @NotBlank @Size(max = 254) String recipient) {
}
