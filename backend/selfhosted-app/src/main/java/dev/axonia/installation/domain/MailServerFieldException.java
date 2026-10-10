package dev.axonia.installation.domain;

import lombok.Getter;

/** A request value that is malformed or missing, named as the {@code field} property of a 400. */
@Getter
public class MailServerFieldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public MailServerFieldException(String field, String rule) {

        super(field + ": " + rule);
        this.field = field;
    }
}
