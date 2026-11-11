package dev.axonia.shared.api.error;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String message) {

        this(null, message);
    }

    public ForbiddenException(String code, String message) {

        super(message);
        this.code = code;
    }
}
