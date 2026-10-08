package dev.axonia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import dev.axonia.access.internal.AccessDataPurger;
import dev.axonia.agent.internal.AgentDataPurger;
import dev.axonia.bookmarks.internal.BookmarksDataPurger;
import dev.axonia.communication.internal.CommunicationDataPurger;
import dev.axonia.community.internal.CommunityDataPurger;
import dev.axonia.events.internal.EventsDataPurger;
import dev.axonia.expertise.internal.ExpertiseDataPurger;
import dev.axonia.identity.internal.IdentityDataPurger;
import dev.axonia.knowledge.internal.KnowledgeDataPurger;
import dev.axonia.learning.internal.LearningDataPurger;
import dev.axonia.media.MediaStorage;
import dev.axonia.recognition.internal.ReactionImages;
import dev.axonia.recognition.internal.RecognitionDataPurger;
import dev.axonia.roundup.internal.RoundupDataPurger;

import static org.mockito.Mockito.mock;

/**
 * Shared context for {@link OrganizationDataPurger} tests. All purger tests extend this one class so the suite adds a
 * single Spring context (many distinct {@code @DataJdbcTest} import sets exhaust the shared Testcontainers Postgres).
 * Tests seed rows for the DEV default organization ("default") via plain SQL and assert the purge empties them;
 * cross-tenant safety is the database's RLS guarantee, proven separately by RlsIsolationTest.
 *
 * <p>
 * {@link MediaStorage} is not part of the JDBC slice, so a recording Mockito stub is registered here - as a
 * {@code @Primary} bean inside a nested {@code @TestConfiguration} - rather than via {@code @MockitoBean} on an
 * individual test class, which would fork a second Spring context. Tests that stub or verify interactions with it
 * should {@code reset()} it in a {@code @BeforeEach} since the bean is a singleton shared by the whole suite.
 *
 * <p>
 * Open modules only; {@code CloudPurgerTestSupport} adds the closed ones.
 */
@Import({EventsDataPurger.class, CommunityDataPurger.class, KnowledgeDataPurger.class, RecognitionDataPurger.class,
        BookmarksDataPurger.class, ExpertiseDataPurger.class, AccessDataPurger.class, IdentityDataPurger.class,
        AgentDataPurger.class, CommunicationDataPurger.class, RoundupDataPurger.class, LearningDataPurger.class,
        ReactionImages.class, PurgerTestSupport.MediaStorageTestConfig.class})
public abstract class PurgerTestSupport extends CoreJdbcTestSupport {

    @TestConfiguration
    static class MediaStorageTestConfig {

        @Bean
        @Primary
        MediaStorage mediaStorage() {
            return mock(MediaStorage.class);
        }
    }
}
