package dev.axonia;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.JavaPackage;

import com.tngtech.archunit.core.domain.JavaClass;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core verifies its own module model. It is read from the package, with the default of leaving tests out, because
 * {@link CoreTestApplication} carries none of the annotations a model read from a type requires.
 */
class CoreModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of("dev.axonia");

    @Test
    void verifiesModularStructure() {

        MODULES.verify();
    }

    @Test
    void theModelHoldsNoTestClass() {

        var types = Stream
                .concat(MODULES.getRootPackages().stream(), MODULES.stream().map(ApplicationModule::getBasePackage))
                .flatMap(JavaPackage::stream).distinct().toList();
        var loadedFromTestOutput = types.stream().filter(CoreModularityTests::loadedFromTestOutput)
                .map(JavaClass::getName).toList();

        assertThat(types).as("the model, its module packages included")
                .anyMatch(type -> !"dev.axonia".equals(type.getPackageName()));
        assertThat(loadedFromTestOutput).as("types of the model loaded from test output").isEmpty();
    }

    private static boolean loadedFromTestOutput(JavaClass type) {

        return type.getSource().map(source -> source.getUri().toString()).filter(uri -> uri.contains("test-classes"))
                .isPresent();
    }
}
