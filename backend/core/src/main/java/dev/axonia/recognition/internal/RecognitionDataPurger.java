package dev.axonia.recognition.internal;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.axonia.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every recognition row for one organization — hub tenant deletion's recognition slice.
 * {@code contribution_love} cascades from {@code recognized_contribution}, but is also deleted directly as a defensive
 * measure. {@code contribution_reaction} has an {@code ON DELETE CASCADE} into it from {@code reaction_definition}, but
 * is likewise deleted directly first, ahead of its parent, rather than relying on the cascade - the same defensive
 * choice the rest of this purger already makes.
 * <p>
 * A definition's image blob is deleted only after the whole purge transaction commits, not inline with the row: object
 * storage has no rollback, so a delete issued before commit would outlive a commit that a later purger in the same
 * hub-deletion transaction then fails. The image keys are read before their rows are deleted, and the actual blob
 * deletes are deferred with {@link TransactionSynchronizationManager} to run once the connection they were read on has
 * actually committed - the same ordering {@code ReactionController.remove} already establishes between its own DB write
 * and its blob delete, applied here across the whole purge rather than one request.
 */
@Component
@RequiredArgsConstructor
public class RecognitionDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;
    private final ReactionImages images;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM recognized_contribution WHERE organization_id = :org", params); // love cascades
        jdbc.update("DELETE FROM contribution_love WHERE organization_id = :org", params);

        var imageKeys = jdbc.queryForList("SELECT image_key FROM reaction_definition WHERE organization_id = :org",
                params, String.class);
        jdbc.update("DELETE FROM contribution_reaction WHERE organization_id = :org", params); // definition cascades
        jdbc.update("DELETE FROM reaction_definition WHERE organization_id = :org", params);
        deleteBlobsAfterCommit(imageKeys);
    }

    private void deleteBlobsAfterCommit(List<String> imageKeys) {

        if (imageKeys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                imageKeys.forEach(images::deleteBlob);
            }
        });
    }
}
