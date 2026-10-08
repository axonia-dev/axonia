package dev.axonia;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import dev.axonia.access.internal.AccessMemberDataEraser;
import dev.axonia.access.internal.AccessPersonalDataExporter;
import dev.axonia.access.internal.AccessPersonalDataFootprint;
import dev.axonia.agent.internal.AgentMemberDataEraser;
import dev.axonia.agent.internal.AgentPersonalDataExporter;
import dev.axonia.agent.internal.AgentPersonalDataFootprint;
import dev.axonia.bookmarks.internal.BookmarksMemberDataEraser;
import dev.axonia.bookmarks.internal.BookmarksPersonalDataExporter;
import dev.axonia.bookmarks.internal.BookmarksPersonalDataFootprint;
import dev.axonia.communication.internal.CommunicationMemberDataEraser;
import dev.axonia.communication.internal.CommunicationPersonalDataExporter;
import dev.axonia.communication.internal.CommunicationPersonalDataFootprint;
import dev.axonia.community.internal.CommunityPersonalDataExporter;
import dev.axonia.community.internal.CommunityPersonalDataFootprint;
import dev.axonia.config.TimeConfig;
import dev.axonia.events.internal.EventsMemberDataEraser;
import dev.axonia.events.internal.EventsPersonalDataExporter;
import dev.axonia.events.internal.EventsPersonalDataFootprint;
import dev.axonia.expertise.internal.ExpertiseMemberDataEraser;
import dev.axonia.expertise.internal.ExpertisePersonalDataExporter;
import dev.axonia.expertise.internal.ExpertisePersonalDataFootprint;
import dev.axonia.identity.internal.IdentityMemberDataEraser;
import dev.axonia.identity.internal.IdentityPersonalDataExporter;
import dev.axonia.identity.internal.IdentityPersonalDataFootprint;
import dev.axonia.knowledge.internal.KnowledgeMemberDataEraser;
import dev.axonia.knowledge.internal.KnowledgePersonalDataExporter;
import dev.axonia.knowledge.internal.KnowledgePersonalDataFootprint;
import dev.axonia.learning.internal.LearningMemberDataEraser;
import dev.axonia.learning.internal.LearningPersonalDataExporter;
import dev.axonia.learning.internal.LearningPersonalDataFootprint;
import dev.axonia.media.MediaStorage;
import dev.axonia.onboarding.internal.OnboardingPersonDataEraser;
import dev.axonia.onboarding.internal.OnboardingPersonalDataExporter;
import dev.axonia.onboarding.internal.OnboardingPersonalDataFootprint;
import dev.axonia.organization.internal.OrganizationPersonalDataFootprint;
import dev.axonia.privacy.internal.PrivacyPersonalDataFootprint;
import dev.axonia.recognition.internal.RecognitionMemberDataEraser;
import dev.axonia.recognition.internal.RecognitionPersonalDataExporter;
import dev.axonia.recognition.internal.RecognitionPersonalDataFootprint;
import dev.axonia.roundup.internal.RoundupPersonalDataFootprint;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.mock;

/**
 * One Spring context for every data-subject-rights test. Distinct @Import sets each add a context, and enough of them
 * exhaust the shared Testcontainers Postgres (HikariPool timeout) only in the full gate, which is a miserable failure
 * to diagnose. Add new footprints, erasers and exporters here rather than annotating individual test classes.
 *
 * <p>
 * A type-filtered {@code @ComponentScan} was tried here instead of this hand-maintained list, to remove the risk of a
 * silently missed entry, as once left a purger out of every purger test until a design review caught it. It does not
 * work: a bare {@code @ComponentScan} on a {@code @DataJdbcTest} support class is not processed at all - only
 * {@code @Import} gets special-cased onto the test class hierarchy by Spring Boot Test, so the scan silently found zero
 * components, even unfiltered, against a package with known {@code @Component} classes in it. Promoting the class to
 * {@code @Configuration} so the scan would run through the normal configuration-class path is rejected outright by
 * {@code @DataJdbcTest} ("cannot be used in combination with the @Component annotation @Configuration"). The explicit
 * list stays; each new footprint, eraser and exporter must be added here by hand.
 *
 * <p>
 * {@code OnboardingPersonDataEraser} is in this list but is a {@code PersonDataEraser}, not a {@code MemberDataEraser}:
 * it erases rows rooted at a person rather than at an Organization, and {@code PersonPurge} runs it once inside
 * {@code SubjectScope.runAs} rather than once per hub inside {@code MemberPurge}. It is imported here for the same
 * reason as everything else - one shared context - and its own class javadoc explains why the port differs.
 *
 * <p>
 * Open modules only; {@code CloudFootprintTestSupport} adds the closed ones.
 *
 * <p>
 * There is deliberately no {@code CommunityMemberDataEraser} here: {@code memberships} is community's only
 * personal-data table, and {@code MemberPurge} already deletes that row itself via
 * {@code MembershipService#leaveForErasure} before any eraser runs, folding the count directly into the receipt. A
 * separate eraser would only ever find zero rows.
 */
@Import({AccessPersonalDataFootprint.class, AgentPersonalDataFootprint.class, BookmarksPersonalDataFootprint.class,
        CommunicationPersonalDataFootprint.class, CommunityPersonalDataFootprint.class,
        EventsPersonalDataFootprint.class, ExpertisePersonalDataFootprint.class, IdentityPersonalDataFootprint.class,
        KnowledgePersonalDataFootprint.class, LearningPersonalDataFootprint.class,
        OnboardingPersonalDataFootprint.class, OrganizationPersonalDataFootprint.class,
        PrivacyPersonalDataFootprint.class, RecognitionPersonalDataFootprint.class, RoundupPersonalDataFootprint.class,
        AccessMemberDataEraser.class, AgentMemberDataEraser.class, BookmarksMemberDataEraser.class,
        CommunicationMemberDataEraser.class, EventsMemberDataEraser.class, ExpertiseMemberDataEraser.class,
        IdentityMemberDataEraser.class, KnowledgeMemberDataEraser.class, LearningMemberDataEraser.class,
        OnboardingPersonDataEraser.class, RecognitionMemberDataEraser.class, AccessPersonalDataExporter.class,
        AgentPersonalDataExporter.class, BookmarksPersonalDataExporter.class, CommunicationPersonalDataExporter.class,
        CommunityPersonalDataExporter.class, EventsPersonalDataExporter.class, ExpertisePersonalDataExporter.class,
        IdentityPersonalDataExporter.class, KnowledgePersonalDataExporter.class, LearningPersonalDataExporter.class,
        OnboardingPersonalDataExporter.class, RecognitionPersonalDataExporter.class, TimeConfig.class,
        FootprintTestSupport.MediaStorageTestConfig.class})
public abstract class FootprintTestSupport extends CoreJdbcTestSupport {

    private static final JsonMapper EXPORT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    /** Renders an export fragment's payload as JSON, for tests that assert on what an exporter did or did not emit. */
    protected static String json(ExportFragment fragment) {

        return EXPORT_MAPPER.writeValueAsString(fragment.payload());
    }

    /**
     * The rows an exporter put under one table key, for tests that assert on row identity or count rather than on a
     * human-readable marker string. Every {@link PersonalDataExporter} builds its payload from
     * {@code NamedParameterJdbcTemplate#queryForList}, which is exactly this shape.
     */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> rowsOf(ExportFragment fragment, String table) {

        return (List<Map<String, Object>>) fragment.payload().get(table);
    }

    @TestConfiguration
    static class MediaStorageTestConfig {

        @Bean
        @Primary
        MediaStorage mediaStorage() {
            return mock(MediaStorage.class);
        }
    }
}
