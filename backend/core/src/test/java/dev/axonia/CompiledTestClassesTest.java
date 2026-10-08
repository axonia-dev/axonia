package dev.axonia;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class CompiledTestClassesTest {

    @ParameterizedTest
    @ValueSource(classes = {OnTheCoreApplication.class, ExtendingATestOnTheCoreApplication.class, OnTheStandIns.class,
            OnTheCoreApplication.Nested.class})
    void inheritsCoreConfiguration_aTestOnCoresConfiguration(Class<?> type) {

        // when
        var actual = CompiledTestClasses.inheritsCoreConfiguration(type);

        // then
        assertThat(actual).isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {OnNothing.class, ImportingSomethingElse.class})
    void inheritsCoreConfiguration_anyOtherTest(Class<?> type) {

        // when
        var actual = CompiledTestClasses.inheritsCoreConfiguration(type);

        // then
        assertThat(actual).isFalse();
    }

    @CoreSpringBootTest
    static class OnTheCoreApplication {

        static class Nested {
        }
    }

    static class ExtendingATestOnTheCoreApplication extends OnTheCoreApplication {
    }

    @Import(ClosedPortStandIns.class)
    static class OnTheStandIns {
    }

    static class OnNothing {
    }

    @Import(OnNothing.class)
    static class ImportingSomethingElse {
    }
}
