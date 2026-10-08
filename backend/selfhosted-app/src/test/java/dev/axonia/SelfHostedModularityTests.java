package dev.axonia;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.JavaPackage;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;

import static org.assertj.core.api.Assertions.assertThat;

class SelfHostedModularityTests {

    /** Production classes only: core's test-jar is on this classpath, and its classes are not in any test-classes. */
    private static final ImportOption PRODUCTION_CODE = location -> new ImportOption.DoNotIncludeTests()
            .includes(location) && !location.contains("-tests.jar");

    private static final ApplicationModules MODULES = ApplicationModules.of(AxoniaSelfHostedApplication.class,
            PRODUCTION_CODE);

    @Test
    void verifiesModularStructure() {

        MODULES.verify();
    }

    @Test
    void theSelfHostedModuleIsInTheModel() {

        assertThat(MODULES.getModuleByName("selfhosted")).isPresent();
    }

    @Test
    void theInstallationModuleIsInTheModel() {

        assertThat(MODULES.getModuleByName("installation")).isPresent();
    }

    @Test
    void theModelHoldsNoTestClass() {

        var types = Stream
                .concat(MODULES.getRootPackages().stream(), MODULES.stream().map(ApplicationModule::getBasePackage))
                .flatMap(JavaPackage::stream).distinct().toList();
        var loadedFromTestOutput = types.stream().filter(SelfHostedModularityTests::loadedFromTestOutput)
                .map(JavaClass::getName).toList();

        assertThat(types).as("the model, its module packages included")
                .anyMatch(type -> !"dev.axonia".equals(type.getPackageName()));
        assertThat(loadedFromTestOutput).as("types of the model loaded from test output").isEmpty();
    }

    private static boolean loadedFromTestOutput(JavaClass type) {

        return type.getSource().map(source -> source.getUri().toString())
                .filter(location -> location.contains("test-classes") || location.contains("-tests.jar")).isPresent();
    }
}
