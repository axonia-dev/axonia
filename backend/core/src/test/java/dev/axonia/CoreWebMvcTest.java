package dev.axonia;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ContextConfiguration;

/** {@link WebMvcTest} on {@link CoreTestApplication}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@WebMvcTest
@ContextConfiguration(classes = CoreTestApplication.class)
public @interface CoreWebMvcTest {

    @AliasFor(annotation = WebMvcTest.class)
    Class<?>[] controllers() default {};
}
