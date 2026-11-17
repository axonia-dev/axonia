package dev.axonia.identity.application;

import dev.axonia.shared.api.error.ValidationException;
import lombok.Getter;

/** A refused name pattern list, naming the request field at fault: {@code patterns[i]} or {@code patterns}. */
@Getter
public class MemberNamesRefusedException extends ValidationException {

    private final String field;

    MemberNamesRefusedException(ValidationException refusal, String field) {

        super(refusal.getCode(), refusal.getMessage());
        this.field = field;
    }
}
