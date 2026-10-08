package dev.axonia.onboarding.internal;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Every read here is already narrowed to one person by V99's policy, so there is no subject predicate to write: a
 * findById for somebody else's subject returns empty because the database says so.
 */
@Repository
public interface OnboardingPathRepository extends CrudRepository<OnboardingPathEntity, String> {
}
