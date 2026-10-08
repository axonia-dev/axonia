package dev.axonia;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * {@link SpringBootTest} on {@link CoreTestApplication}, with the closed ports stood in. A Cloud test never extends a
 * class carrying it, because the inherited configuration cannot be reset.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = CoreTestApplication.class)
@Import(ClosedPortStandIns.class)
public @interface CoreSpringBootTest {

    @AliasFor(annotation = SpringBootTest.class)
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

    @AliasFor(annotation = SpringBootTest.class)
    String[] properties() default {};
}
