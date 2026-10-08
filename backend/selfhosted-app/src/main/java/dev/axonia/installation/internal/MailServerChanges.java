package dev.axonia.installation.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.experimental.UtilityClass;

/** What a save or a clear changed, field by field in the form's order; a secret is replaced or cleared, never shown. */
@UtilityClass
class MailServerChanges {

    List<MailFieldChange> between(MailServerRow before, MailServerRow after) {

        var was = before.values(before.passwordSecret());
        var now = after.values(after.passwordSecret());
        return Arrays.stream(MailField.values()).map(field -> change(field, was.value(field), now.value(field)))
                .flatMap(Optional::stream).toList();
    }

    private Optional<MailFieldChange> change(MailField field, Object from, Object to) {

        if (Objects.equals(from, to)) {
            return Optional.empty();
        }
        if (to == null) {
            return Optional.of(new MailFieldChange(field.apiName(), "cleared", shown(field, from), null));
        }
        if (field == MailField.PASSWORD) {
            return Optional.of(new MailFieldChange(field.apiName(), "replaced", null, null));
        }
        return Optional.of(new MailFieldChange(field.apiName(), "set", shown(field, from), to.toString()));
    }

    private String shown(MailField field, Object value) {

        return field == MailField.PASSWORD || value == null ? null : value.toString();
    }
}
