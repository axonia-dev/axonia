package dev.axonia;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.PackageMatchers;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import dev.axonia.media.AbsoluteMediaLinks;
import dev.axonia.media.MediaLinks;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * An image address has two forms: a path for the application's own pages, and an absolute address for whatever is read
 * outside them. Which one a class may take follows from where its output goes, so it is decided here, by package, and
 * not by whoever writes the next mail or tool.
 *
 * <p>
 * What is read outside the pages also reads no address another module minted for its pages, the path form arriving
 * through a {@code DirectoryUser} or a {@code Community} instead of through {@code MediaLinks}.
 *
 * <p>
 * A package is the unit here, so a class read outside the pages that lives in a page package is not covered:
 * {@code community.ChatDestinationService}, whose probe posts to a chat, is one.
 */
class MediaAddressFormTest {

    private static final String[] READ_OUTSIDE_THE_PAGES = {"dev.axonia.roundup..", "dev.axonia.communication..",
            "dev.axonia.agent..", "dev.axonia.privacy..", "dev.axonia.events.."};

    private static final Set<String> ADDRESS_ACCESSORS = Set.of("avatarUrl", "badgeUrl", "heroUrl", "imageUrl");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.axonia");

    @Test
    void whatIsReadOutsideTheApplicationsPagesNeverTakesThePathForm() {

        // then
        noClasses().that().resideInAnyPackage(READ_OUTSIDE_THE_PAGES).should().dependOnClassesThat()
                .areAssignableTo(MediaLinks.class).check(PRODUCTION);
    }

    @Test
    void onlyWhatIsReadOutsideThePagesTakesTheAbsoluteForm() {

        // then
        noClasses().that().resideOutsideOfPackages(READ_OUTSIDE_THE_PAGES).and()
                .resideOutsideOfPackage("dev.axonia.media..").should().dependOnClassesThat()
                .areAssignableTo(AbsoluteMediaLinks.class).check(PRODUCTION);
    }

    @Test
    void whatIsReadOutsideTheApplicationsPagesNeverReadsAnAddressMintedForThePages() {

        // then
        noClasses().that().resideInAnyPackage(READ_OUTSIDE_THE_PAGES).should()
                .callMethodWhere(readsAnAddressMintedForThePages()).check(PRODUCTION);
    }

    private static DescribedPredicate<JavaMethodCall> readsAnAddressMintedForThePages() {

        var outsideThePages = PackageMatchers.of(READ_OUTSIDE_THE_PAGES);
        return DescribedPredicate.describe("an image address of a class the application's pages read",
                call -> ADDRESS_ACCESSORS.contains(call.getName())
                        && !outsideThePages.test(call.getTargetOwner().getPackageName()));
    }
}
