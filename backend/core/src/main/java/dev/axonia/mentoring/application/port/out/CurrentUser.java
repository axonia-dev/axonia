package dev.axonia.mentoring.application.port.out;

public interface CurrentUser {

    String userId();

    /** Whether somebody other than the person acts through this request. */
    boolean delegated();
}
