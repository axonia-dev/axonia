package dev.axonia.selfhosted.internal;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.selfhosted.SelfHostedProperties;

import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_ID;
import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_NAME;
import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.OWNER_GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SelfHostedOrganizationTest {

    private static final SelfHostedProperties PROPERTIES = SelfHostedPropertiesMother.matchingAccountsOn("oid");

    @Mock
    private SelfHostedFounding founding;

    @Test
    void establishesTheConfiguredInstallationInsideItsOrganizationsScope() {

        // given
        var scopes = new ArrayList<Authentication>();
        doAnswer(invocation -> scopes.add(SecurityContextHolder.getContext().getAuthentication())).when(founding)
                .establish(any());

        // when
        new SelfHostedOrganization(PROPERTIES, founding).establishOnStart();

        // then
        verify(founding).establish(new Installation(ORGANIZATION_ID, ORGANIZATION_NAME, OWNER_GROUP, "oid"));
        assertThat(scopes).singleElement().isInstanceOfSatisfying(OrganizationBoundAuthentication.class,
                scope -> assertThat(scope.organizationId()).isEqualTo(ORGANIZATION_ID));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aRefusalStopsTheStart() {

        // given
        doThrow(new IllegalStateException("refused")).when(founding).establish(any());

        // when / then
        assertThatThrownBy(() -> new SelfHostedOrganization(PROPERTIES, founding).establishOnStart())
                .isInstanceOf(IllegalStateException.class).hasMessage("refused");
    }

    @Test
    void establishesOnceTheApplicationIsReady() throws NoSuchMethodException {

        // when
        var listener = AnnotatedElementUtils.findMergedAnnotation(
                SelfHostedOrganization.class.getDeclaredMethod("establishOnStart"), EventListener.class);

        // then
        assertThat(listener).isNotNull();
        assertThat(listener.classes()).containsExactly(ApplicationReadyEvent.class);
    }
}
