package dev.axonia.identity.api;

/**
 * The file a data export carries a person's identity in. Identity writes it, and a module that holds another part of
 * the same record - another module's succession links and platform account - contributes its tables to it, so a person
 * reads one identity file whatever the modules behind it.
 */
public interface IdentityExportFile {

    String NAME = "identity.json";
}
