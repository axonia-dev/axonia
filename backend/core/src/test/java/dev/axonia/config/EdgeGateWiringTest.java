package dev.axonia.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import dev.axonia.EdgeGateStatus;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeGateWiringTest {

    private static final String IDEOGRAPHIC_SPACE = "\u3000";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(EdgeGateConfig.class).withPropertyValues("axonia.edge.shared-secrets=a-secret");

    private final WebApplicationContextRunner enabledWithoutSecrets = new WebApplicationContextRunner()
            .withUserConfiguration(EdgeGateConfig.class).withPropertyValues("axonia.edge.gate-enabled=true");

    @Test
    void registersTheGateWhenItIsEnabled() {

        runner.withPropertyValues("axonia.edge.gate-enabled=true")
                .run(context -> assertThat(context).hasBean("edgeGateRegistration"));
    }

    @Test
    void registersTheGateAheadOfTheSecurityChainSoNothingUngatedIsEverAuthenticated() {

        runner.withPropertyValues("axonia.edge.gate-enabled=true").run(
                context -> assertThat(context.getBean("edgeGateRegistration", FilterRegistrationBean.class).getOrder())
                        .isEqualTo(Ordered.HIGHEST_PRECEDENCE));
    }

    @Test
    void leavesTheGateUnregisteredWhenItIsOff() {

        runner.withPropertyValues("axonia.edge.gate-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("edgeGateRegistration"));
    }

    @Test
    void reportsEnforcingTrueWhenTheGateIsEnabled() {

        runner.withPropertyValues("axonia.edge.gate-enabled=true")
                .run(context -> assertThat(context.getBean(EdgeGateStatus.class).enforcing()).isTrue());
    }

    @Test
    void reportsEnforcingFalseWhenTheGateIsDisabled() {

        runner.withPropertyValues("axonia.edge.gate-enabled=false")
                .run(context -> assertThat(context.getBean(EdgeGateStatus.class).enforcing()).isFalse());
    }

    @Test
    void refusesToStartWithNoSecretConfigured() {

        enabledWithoutSecrets.run(context -> assertValidationRejected(context, "sharedSecrets", "must not be empty"));
    }

    @Test
    void refusesToStartWhenTheSecretVariableIsSetButEmpty() {

        enabledWithoutSecrets.withPropertyValues("axonia.edge.shared-secrets=")
                .run(context -> assertValidationRejected(context, "sharedSecrets", "must not be empty"));
    }

    @Test
    void refusesToStartWhenAsciiWhitespaceCollapsesTheSecretToAnEmptyList() {

        enabledWithoutSecrets.withPropertyValues("axonia.edge.shared-secrets= ")
                .run(context -> assertValidationRejected(context, "sharedSecrets", "must not be empty"));
    }

    /**
     * A secret pasted out of a document can carry a Unicode space above U+0020. Spring's binder trims with
     * {@code String.trim()}, which strips only code points up to U+0020, so such a value survives binding as a
     * non-empty element and only {@code NotBlank} catches it. Under {@code NotEmpty} the gate would boot with a single
     * space character as its one accepted secret.
     */
    @Test
    void refusesToStartWhenTheSecretIsAUnicodeSpaceThatSurvivesTheBindersTrim() {

        enabledWithoutSecrets.withPropertyValues("axonia.edge.shared-secrets=" + IDEOGRAPHIC_SPACE)
                .run(context -> assertValidationRejected(context, "sharedSecrets[0]", "must not be blank"));
    }

    /**
     * The binder does not fail on an unresolved placeholder, it binds the placeholder's own text. Without this rule an
     * {@code AXONIA_EDGE_SHARED_SECRET} that was never set on the deployment would boot a live gate whose one accepted
     * secret is a string committed to this repository.
     *
     * <p>
     * The placeholder deliberately does NOT name the real variable, and the name it does use contains spaces. The
     * constraint under test is "any unresolved placeholder", so naming the real variable would only make the assertion
     * depend on that variable being absent from whatever environment runs the suite. A name no environment variable and
     * no system property can carry is unresolvable by construction rather than by convention.
     */
    @Test
    void refusesToStartWhenTheSecretPlaceholderWasNeverResolved() {

        enabledWithoutSecrets
                .withPropertyValues("axonia.edge.shared-secrets=${no environment variable can be named this}")
                .run(context -> assertValidationRejected(context, "sharedSecrets[0]",
                        "must be a resolved value, not an unresolved property placeholder"));
    }

    private static void assertValidationRejected(AssertableWebApplicationContext context, String field,
            String message) {

        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                .hasMessageContaining(field).hasMessageContaining(message);
    }
}
