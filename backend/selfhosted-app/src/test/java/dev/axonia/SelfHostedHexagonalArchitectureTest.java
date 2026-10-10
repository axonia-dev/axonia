package dev.axonia;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static org.assertj.core.api.Assertions.assertThat;

class SelfHostedHexagonalArchitectureTest {

    private static final ImportOption PRODUCTION_CODE = location -> new ImportOption.DoNotIncludeTests()
            .includes(location) && !location.contains("-tests.jar");

    @Test
    void everyModuleIsLaidOutAsAHexagon() {

        var classes = new ClassFileImporter().withImportOption(PRODUCTION_CODE).importPackages("dev.axonia");

        assertThat(HexagonalRules.violations(classes)).as("hexagonal layout violations").isEmpty();
    }
}
