package dev.axonia.installation.application;

public class UntestedValuesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UntestedValuesException() {

        super("Send a test mail with these values before saving them.");
    }
}
