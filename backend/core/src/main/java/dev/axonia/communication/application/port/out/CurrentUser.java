package dev.axonia.communication.application.port.out;

public interface CurrentUser {
    String userId();

    /** Whether somebody other than the member acts through this request. */
    boolean delegated();
}
