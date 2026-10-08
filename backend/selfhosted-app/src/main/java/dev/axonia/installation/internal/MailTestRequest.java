package dev.axonia.installation.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record MailTestRequest(@NotNull @Valid MailServerValuesRequest values, @NotBlank @Size(max = 254) String recipient) {
}
