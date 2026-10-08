package dev.axonia.expertise.internal;

import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;

/**
 * Shared @DataJdbcTest base for the expertise persistence tests. Importing the same bean set in one place keeps all
 * expertise repository tests on a SINGLE cached Spring context (one Hikari pool) instead of one context per @Import set
 * — which otherwise exhausts the shared Testcontainers Postgres connection limit during the full suite.
 */
@Import({SignalWriter.class, TagPreferenceWriter.class, ExpertiseQueryRepositoryImpl.class,
        PreferenceRepositoryImpl.class, TagAliasRepositoryImpl.class, TagDismissalRepositoryImpl.class,
        DeclarationRepositoryImpl.class, NamedAreaRepositoryImpl.class, VocabularyLockImpl.class})
abstract class ExpertiseJdbcTestSupport extends CoreJdbcTestSupport {
}
