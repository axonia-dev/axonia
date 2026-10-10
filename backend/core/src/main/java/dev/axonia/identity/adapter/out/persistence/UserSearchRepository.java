package dev.axonia.identity.adapter.out.persistence;

import java.util.List;

import dev.axonia.identity.domain.UserSearch;

/**
 * Repository fragment for the one query Spring Data cannot derive: a token match whose SQL shape depends on how many
 * tokens the query has.
 */
interface UserSearchRepository {

    /**
     * Users of the organization whose display name or email contains every token of the query, ordered by display name
     * then user id so the limit is deterministic. A query with no usable tokens matches everyone in the organization
     * when the search is unrestricted, and nobody when it is restricted to a set of user ids.
     */
    List<UserEntity> search(UserSearch search);
}
