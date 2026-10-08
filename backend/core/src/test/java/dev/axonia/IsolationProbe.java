package dev.axonia;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as proving the row-level security isolation of one table, under the runtime role rather than the
 * schema owner. The RLS ratchet reads these from the test classpath, so a probe that is deleted takes its table off the
 * probed side with it, and {@link IsolationProbes#assertEveryProbeBesideRuns} fails a probe that JUnit would not
 * discover or would skip as {@code @Disabled}. Review enforces the rest: that the method probes the table it names,
 * that it does so as the runtime role, and that no condition skips it, such as {@code @EnabledIf}, {@code @DisabledIf}
 * and their kin, an assumption, or {@code @Testcontainers(disabledWithoutDocker = true)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(IsolationProbe.List.class)
public @interface IsolationProbe {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {

        IsolationProbe[] value();
    }
}
