package dev.axonia.shared.api.personaldata;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The half of the personal-data layers that is actually a structural, build-enforced fact rather than a hand-maintained
 * convention: every module that purges a member's rows on Organization Close also declares what is personal in them and
 * can export it. This scans the classpath directly for {@link OrganizationDataPurger}, {@link PersonalDataFootprint}
 * and {@link PersonalDataExporter} implementers rather than reading either of the two hand-maintained {@code @Import}
 * lists ({@code PurgerTestSupport}, {@code FootprintTestSupport}) that wire the per-module beans into their test
 * contexts - those two lists could each drift independently and still happen to agree with each other, which would
 * defeat the point of a structural check.
 *
 * <p>
 * The export half is asked of the modules that purge <em>personal</em> rows, which is what a data-subject request can
 * actually be answered with. A module may own a table that is purely tenant-scoped bookkeeping, naming nobody -
 * {@code roundup} and its one flag per organization is the first - and must still purge it on close so it does not
 * outlive the tenant. Demanding a {@link PersonalDataExporter} of such a module would produce one that returns nothing:
 * an empty JSON file in every member's export package, implying axonia holds something about them under that module
 * when it holds nothing, and a class whose only reason to exist is this assertion. Which modules are exempt is not a
 * list kept here; it is read from each footprint's own declaration, so a module that later gains a personal column is
 * required to export it from that moment, without anybody remembering to come back. The defect this half was ever going
 * to catch - purges personal data, forgets to export it - is caught harder by
 * {@code PersonalDataFootprintTest.everyPersonalColumnAppearsInItsModulesExportFragment}, which asserts the fragment
 * emits a key per personal column rather than merely that an exporter class exists, and which has always gated on this
 * same condition.
 *
 * <p>
 * {@link MemberDataEraser} is deliberately not asserted here. {@code community} implements
 * {@link OrganizationDataPurger} and {@link PersonalDataFootprint} - {@code memberships} is a real personal-data table
 * - but has no {@code CommunityMemberDataEraser}, because {@code MemberPurge} already deletes that row via
 * {@code MembershipService#leaveForErasure} before any eraser runs, folding the count into the receipt directly (see
 * {@link FootprintTestSupport}'s javadoc). A blanket "every purger has an eraser" assertion would be false today, not
 * merely untested, so asserting it here would either fail permanently or have to special-case community - which is
 * exactly the "hand-maintained, can drift" shape this test exists to avoid. The exception is recorded in prose, in
 * {@code FootprintTestSupport}'s javadoc instead.
 *
 * <p>
 * It reads the classpath of the build unit it runs in: core's open modules here, and in
 * {@code CloudOrganizationDataPurgerCouplingTest} the whole Cloud application, open and closed modules together.
 */
public class OrganizationDataPurgerCouplingTest {

    private static final String BASE_PACKAGE = "dev.axonia";

    @Test
    void everyPurgerModuleDeclaresAFootprint() {

        assertThat(purgerPackages()).describedAs("packages with an OrganizationDataPurger but no PersonalDataFootprint")
                .isSubsetOf(implementerPackagesOf(PersonalDataFootprint.class));
    }

    @Test
    void everyPurgerModuleCanExportWhatItPurges() {

        var purgersOfPersonalData = purgerPackages().stream().filter(packagesDeclaringExportableColumns()::contains)
                .collect(Collectors.toSet());

        assertThat(purgersOfPersonalData)
                .describedAs("the condition below has gone vacuous: no purger module declares an exportable personal "
                        + "column, so the subset assertion would hold no matter what any module exports")
                .isNotEmpty();
        assertThat(purgersOfPersonalData)
                .describedAs("packages that purge personal columns but have no PersonalDataExporter")
                .isSubsetOf(implementerPackagesOf(PersonalDataExporter.class));
    }

    private Set<String> purgerPackages() {

        return implementerPackagesOf(OrganizationDataPurger.class);
    }

    /**
     * The modules whose footprint declares at least one personal column an export can actually carry. It reads
     * {@link ColumnFootprint#reachesAnExport()}, the same predicate
     * {@code PersonalDataFootprintTest.everyPersonalColumnAppearsInItsModulesExportFragment} uses to decide whether a
     * module needs an exporter at all and {@link TableFootprint#exportedColumns()} uses to build every projection, so
     * the three cannot drift apart. A {@link Disposition#RETAIN} column is personal but not the member's -
     * {@code organization.closed_by} names the axonia operator who closed a tenant - and an
     * {@link ExportTreatment#WITHHELD} one is the member's but never printed. A module whose only personal columns were
     * either would otherwise be required to ship an exporter that can emit nothing.
     *
     * <p>
     * Each footprint is instantiated directly rather than autowired, so this stays independent of the {@code @Import}
     * lists; they are pure declarations with no collaborators, which is what makes that possible and is itself worth
     * holding them to.
     */
    private Set<String> packagesDeclaringExportableColumns() {

        return implementersOf(PersonalDataFootprint.class).stream()
                .filter(footprint -> instantiate(footprint).tables().stream()
                        .flatMap(table -> table.personal().stream()).anyMatch(ColumnFootprint::reachesAnExport))
                .map(Class::getPackageName).collect(Collectors.toSet());
    }

    private static PersonalDataFootprint instantiate(Class<?> footprint) {

        try {
            return (PersonalDataFootprint) footprint.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "A PersonalDataFootprint is a declaration, not a collaborator, and must be constructible with no "
                            + "arguments: " + footprint.getName(),
                    e);
        }
    }

    private Set<String> implementerPackagesOf(Class<?> type) {

        return implementersOf(type).stream().map(Class::getPackageName).collect(Collectors.toSet());
    }

    private Set<Class<?>> implementersOf(Class<?> type) {

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(type));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(beanDefinition -> ClassUtils.resolveClassName(beanDefinition.getBeanClassName(), null))
                .collect(Collectors.toSet());
    }
}
