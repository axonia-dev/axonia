package dev.axonia.identity.api;

/** The reconciliation keys of an organization's synced directory groups. */
public interface DirectoryGroupKeys {

    /**
     * Realigns every synced group's reconciliation key with the convention of {@code provider}, holding again the
     * groups that land on a key a role binding already waits on.
     */
    void rekeyTo(String organizationId, IdpProvider provider);
}
