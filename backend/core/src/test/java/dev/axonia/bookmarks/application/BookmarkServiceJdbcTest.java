package dev.axonia.bookmarks.application;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.bookmarks.adapter.out.persistence.JdbcBookmarkStore;
import dev.axonia.bookmarks.application.port.out.ArtifactAccess;
import dev.axonia.bookmarks.application.port.out.CommunityAccess;
import dev.axonia.bookmarks.application.port.out.CurrentUser;
import dev.axonia.bookmarks.application.port.out.OrganizationContext;
import dev.axonia.bookmarks.application.port.out.SessionAccess;
import dev.axonia.bookmarks.domain.SaveState;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.bookmarks.domain.SavedSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The service in its own real transaction against Postgres, as a request runs it. Rows are committed, so the test is
 * not transactional and deletes what it wrote.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BookmarkService.class, JdbcBookmarkStore.class})
class BookmarkServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final String MEMBER = "bookmark-double-saver";
    private static final long COMMUNITY = 870_001L;
    private static final SaveableRef SESSION = SaveableRef.session(870_002L);

    @Autowired
    private BookmarkService sut;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private ArtifactAccess artifactAccess;

    @MockitoBean
    private SessionAccess sessionAccess;

    @MockitoBean
    private CommunityAccess communityAccess;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoBean
    private OrganizationContext organizationContext;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(MEMBER);
        when(communityAccess.isMember(COMMUNITY, MEMBER)).thenReturn(true);
        when(sessionAccess.get(SESSION.id()))
                .thenReturn(SavedSession.builder().id(SESSION.id()).communityId(COMMUNITY).build());
    }

    @AfterEach
    void deleteBookmarks() {

        jdbc.update("DELETE FROM bookmark WHERE organization_id = :org AND member_user_id = :member",
                Map.of("org", ORG, "member", MEMBER));
    }

    @Test
    void savingAnItemTheMemberAlreadySavedAnswersItsSaveStateAndKeepsOneRow() {

        // given
        sut.save(SESSION);

        // when
        var actual = sut.save(SESSION);

        // then
        assertThat(actual).isEqualTo(SaveState.builder().viewerHasSaved(true).viewerHelped(null).build());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookmark WHERE member_user_id = :member",
                Map.of("member", MEMBER), Integer.class)).isEqualTo(1);
    }
}
