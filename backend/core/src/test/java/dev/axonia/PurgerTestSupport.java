package dev.axonia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import dev.axonia.access.adapter.out.privacy.AccessDataPurger;
import dev.axonia.agent.adapter.out.privacy.AgentDataPurger;
import dev.axonia.bookmarks.adapter.out.privacy.BookmarksDataPurger;
import dev.axonia.communication.adapter.out.privacy.CommunicationDataPurger;
import dev.axonia.community.adapter.out.privacy.CommunityDataPurger;
import dev.axonia.events.adapter.out.privacy.EventsDataPurger;
import dev.axonia.expertise.adapter.out.privacy.ExpertiseDataPurger;
import dev.axonia.identity.adapter.out.privacy.IdentityDataPurger;
import dev.axonia.knowledge.adapter.out.privacy.KnowledgeDataPurger;
import dev.axonia.learning.adapter.out.privacy.LearningDataPurger;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.recognition.adapter.out.privacy.RecognitionDataPurger;
import dev.axonia.recognition.application.ReactionImages;
import dev.axonia.roundup.adapter.out.privacy.RoundupDataPurger;
import dev.axonia.shared.api.personaldata.OrganizationDataPurger;

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
