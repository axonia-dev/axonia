package dev.axonia.events.application.port.out;

public interface CurrentUser {

    String userId();

    /** Whether somebody other than the person acts through this request, on their behalf. */
    boolean delegated();
}
