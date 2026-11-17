package dev.axonia.identity.adapter.out.persistence;

import java.util.Map;

import dev.axonia.identity.domain.ReadName;

/** Repository fragment for the recompute of a save: one batched statement over every row whose name changed. */
interface UserNameRepository {

    /**
     * Writes what the Organization's name patterns read out of each member's received name, keyed by row id. A row that
     * predates {@code received_name} keeps its current display name as the received one, before that is overwritten.
     */
    void updateNames(String organizationId, Map<Long, ReadName> names);
}
