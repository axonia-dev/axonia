package dev.axonia.shared.api.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {

        super(message);
    }
}
