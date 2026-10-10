package dev.axonia.identity.application.scim;

import org.springframework.http.HttpStatus;

public class ScimException extends RuntimeException {

    private final HttpStatus status;
    private final String scimType;

    private ScimException(HttpStatus status, String scimType, String detail) {

        super(detail);
        this.status = status;
        this.scimType = scimType;
    }

    public HttpStatus getStatus() {

        return status;
    }

    public String getScimType() {

        return scimType;
    }

    public static ScimException notFound(String detail) {

        return new ScimException(HttpStatus.NOT_FOUND, null, detail);
    }

    /** RFC 7644 3.3: a duplicate unique attribute MUST be a 409 carrying scimType "uniqueness". */
    public static ScimException uniquenessConflict(String detail) {

        return new ScimException(HttpStatus.CONFLICT, "uniqueness", detail);
    }

    public static ScimException badRequest(String detail) {

        return new ScimException(HttpStatus.BAD_REQUEST, null, detail);
    }

    public static ScimException unauthorized(String detail) {

        return new ScimException(HttpStatus.UNAUTHORIZED, null, detail);
    }
}
