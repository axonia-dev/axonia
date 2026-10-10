package dev.axonia.learning.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.TagUsage;
import dev.axonia.community.api.TagUsageKind;
import dev.axonia.learning.application.port.out.LearningIntentRepository;
import dev.axonia.learning.application.port.out.OrganizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class LearningTagWriterTest {

    private static final String ORG = "acme";

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private LearningIntentRepository repository;

    @InjectMocks
    private LearningTagWriter sut;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
    }

    @Test
    void usageIsTheMembersHoldingTheTag() {

        when(repository.countIn(ORG, 7L, "kafka")).thenReturn(3);

        assertThat(sut.usage(7L, "kafka")).containsExactly(new TagUsage(TagUsageKind.LEARNING_INTENT, 3));
    }

    @Test
    void noUsageWhenNobodyHoldsIt() {

        assertThat(sut.usage(7L, "kafka")).isEmpty();
    }

    @Test
    void renameRewritesTheTagInTheCommunity() {

        sut.rename(7L, "k8s", "kubernetes");

        verify(repository).renameTag(ORG, 7L, "k8s", "kubernetes");
    }

    @Test
    void removeDeletesTheTagInTheCommunity() {

        sut.remove(7L, "kafka");

        verify(repository).removeTag(ORG, 7L, "kafka");
    }

    @Test
    void tagsInIsEveryTagHeld() {

        when(repository.tagsIn(ORG, 7L)).thenReturn(List.of("helm", "kafka"));

        assertThat(sut.tagsIn(7L)).containsExactly("helm", "kafka");
    }
}
