package dev.axonia.events.adapter.out.persistence;

import dev.axonia.events.application.PastSessionsQuery;

/**
 * The one criteria-built session query left on the repository: how many sessions a retrospective window holds.
 *
 * <p>
 * {@code findUpcoming}, {@code findPast} and {@code findBetween} used to sit beside it and are gone. Each returned
 * {@link SessionEntity} aggregates, and Spring Data JDBC hydrates a four-level aggregate with one query per parent row
 * per collection - 587 statements for a page of 20, measured. Every one of their callers now reads through
 * {@link SessionListReader} instead, which reads one query per level, so keeping them would have left three unreachable
 * N+1 reads in the tree for a later caller to find and use.
 */
interface SessionRepositoryCustom {

    long countPast(PastSessionsQuery query);
}
