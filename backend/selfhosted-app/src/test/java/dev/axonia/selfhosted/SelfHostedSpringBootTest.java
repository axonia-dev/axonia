package dev.axonia.selfhosted;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import dev.axonia.AxoniaSelfHostedApplication;

/**
 * {@link SpringBootTest} on {@link AxoniaSelfHostedApplication} itself, with no stand-in for any port: a bean the
 * Self-hosted edition lacks fails the context here as it would fail an installation. The database is
 * {@link SelfHostedDatabase}, connected to as the application's own roles, and the identity provider is
 * {@link MockOidcIssuer}. The {@code DEV} profile is the test one in {@code src/test/resources}, which shadows the
 * application's own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = AxoniaSelfHostedApplication.class)
@ContextConfiguration(initializers = SelfHostedTestInfrastructure.class)
@ActiveProfiles("DEV")
@AutoConfigureMockMvc
public @interface SelfHostedSpringBootTest {

    @AliasFor(annotation = SpringBootTest.class)
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

    @AliasFor(annotation = SpringBootTest.class)
    String[] properties() default {};
}
