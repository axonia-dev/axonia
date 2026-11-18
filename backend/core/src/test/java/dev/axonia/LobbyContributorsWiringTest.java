package dev.axonia;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import dev.axonia.community.application.LobbyService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby treats a missing contributor as a community with nothing to show, so only the wiring itself can prove the
 * events and knowledge contributors reach it.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class LobbyContributorsWiringTest {

    @Autowired
    private LobbyService lobbyService;

    @Test
    void theEventsAndKnowledgeContributorsReachTheLobby() {

        // given
        var target = AopTestUtils.getUltimateTargetObject(lobbyService);

        // when
        var sessions = (List<?>) ReflectionTestUtils.getField(target, "sessionShowcases");
        var libraries = (List<?>) ReflectionTestUtils.getField(target, "libraryShowcases");

        // then
        assertThat(sessions).singleElement().extracting(bean -> AopProxyUtils.ultimateTargetClass(bean).getName())
                .isEqualTo("dev.axonia.events.adapter.out.community.SessionShowcaseAdapter");
        assertThat(libraries).singleElement().extracting(bean -> AopProxyUtils.ultimateTargetClass(bean).getName())
                .isEqualTo("dev.axonia.knowledge.adapter.out.community.LibraryShowcaseAdapter");
    }
}
