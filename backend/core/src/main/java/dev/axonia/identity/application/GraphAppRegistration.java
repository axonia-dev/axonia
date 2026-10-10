package dev.axonia.identity.application;

/** Whether axonia's own Microsoft Graph app registration is configured, so Graph can be read at all. */
public interface GraphAppRegistration {

    boolean isConfigured();
}
