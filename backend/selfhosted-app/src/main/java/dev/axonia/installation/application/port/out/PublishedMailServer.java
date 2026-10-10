package dev.axonia.installation.application.port.out;

/** The mail server delivery sends through, as last read; told to read again once a save or a clear has committed. */
public interface PublishedMailServer {

    void reload();
}
