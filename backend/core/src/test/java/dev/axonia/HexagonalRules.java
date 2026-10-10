package dev.axonia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.Repository;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Controller;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The hexagonal layout every module follows: {@code api} is the only package another module reaches (Spring Modulith
 * holds that), and inside a module dependencies point inward. Read with the backend guide's module anatomy.
 */
public final class HexagonalRules {

    private static final String ROOT = "dev.axonia";

    private static final String[] WEB = {"org.springframework.web..", "jakarta.servlet.."};

    private static final String[] PERSISTENCE = {"org.springframework.jdbc..", "org.springframework.data.relational..",
            "org.springframework.data.jdbc..", "org.springframework.data.repository.."};

    private static final String[] STORAGE = {"java.sql..", "javax.sql..", "org.springframework.jdbc.."};

    private static final String MAPPING = "org.springframework.data.annotation..";

    private static final String PERSISTABLE = "org.springframework.data.domain.Persistable";

    private static final Set<String> JDBC = Set.of("org.springframework.jdbc.core.simple.JdbcClient",
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");

    private HexagonalRules() {
    }

    public static List<String> violations(JavaClasses classes) {

        var report = new ArrayList<String>();
        rootRules().forEach(rule -> collect(rule, classes, report));
        modulesOf(classes).forEach(module -> moduleRules(module).forEach(rule -> collect(rule, classes, report)));
        return report;
    }

    static Set<String> modulesOf(JavaClasses classes) {

        var modules = new TreeSet<String>();
        for (var type : classes) {
            var name = type.getPackageName();
            if (name.startsWith(ROOT + ".") && !name.startsWith(ROOT + ".keycloak")) {
                modules.add(name.substring(ROOT.length() + 1).split("\\.")[0]);
            }
        }
        modules.remove("db");
        return modules;
    }

    private static void collect(ArchRule rule, JavaClasses classes, List<String> report) {

        var result = rule.allowEmptyShould(true).evaluate(classes);
        if (result.hasViolation()) {
            report.addAll(result.getFailureReport().getDetails());
        }
    }

    private static List<ArchRule> rootRules() {

        return List.of(
                noClasses().that().resideInAPackage(ROOT).and(notAnApplicationClass()).should().resideInAPackage(ROOT)
                        .as("no class but the application classes sits in " + ROOT),
                noClasses().that().resideInAPackage(ROOT + "..").should().resideInAPackage("..internal..")
                        .as("no package is named internal"));
    }

    private static List<ArchRule> moduleRules(String module) {

        var x = ROOT + "." + module;
        var own = Stream.of("domain", "application", "adapter", "configuration").map(layer -> x + "." + layer + "..")
                .toArray(String[]::new);
        return List.of(
                classes().that().resideInAPackage(x + "..").and().doNotHaveSimpleName("package-info").should()
                        .resideInAnyPackage(layers(x)).as(module + ": every class sits in a layer package"),
                classes().that().resideInAPackage(x + ".api..").should(sitInANamedInterface())
                        .as(module + ": every api package is exposed, Spring Modulith reads no subpackage of one"),
                noClasses().that().resideInAPackage(x + ".api..").should().dependOnClassesThat().resideInAnyPackage(own)
                        .as(module + ": api depends on nothing else in its module"),
                noClasses().that().resideInAPackage(x + ".api..").and(outsideShared()).should()
                        .dependOnClassesThat(persistenceTypes())
                        .as(module + ": api publishes no persistence type, paging aside"),
                noClasses().that().resideInAPackage(x + ".domain..").should()
                        .dependOnClassesThat(infrastructure(new String[]{x + ".application..", x + ".adapter..",
                                x + ".configuration..", "org.springframework.security.."}))
                        .as(module + ": domain depends only inward and on no infrastructure"),
                noClasses().that().resideInAPackage(x + ".application..").should()
                        .dependOnClassesThat(infrastructure(new String[]{x + ".adapter..", x + ".configuration.."}))
                        .as(module + ": application depends only inward and on no web or persistence"),
                noClasses().that().resideInAPackage(x + ".adapter.in..").should().dependOnClassesThat()
                        .resideInAPackage(x + ".adapter.out..")
                        .as(module + ": a driving adapter never reaches a driven one"),
                noClasses().that().resideInAPackage(x + ".adapter.in..").should().dependOnClassesThat()
                        .resideInAPackage(x + ".application.port.out..")
                        .as(module + ": a driving adapter goes through the application, never to an outbound port"),
                noClasses().that().resideInAPackage(x + ".adapter.out..").should().dependOnClassesThat()
                        .resideInAPackage(x + ".adapter.in..")
                        .as(module + ": a driven adapter never reaches a driving one"),
                classes().that().resideInAPackage(x + "..").and().areAnnotatedWith(Table.class).should()
                        .resideInAPackage(x + ".adapter.out..").as(module + ": entities live in adapter.out"),
                classes().that().resideInAPackage(x + "..").and().areAssignableTo(Repository.class).should()
                        .resideInAPackage(x + ".adapter.out..").as(module + ": repositories live in adapter.out"),
                classes().that().resideInAPackage(x + "..").and(useJdbc()).should()
                        .resideInAnyPackage(x + ".adapter.out..", x + ".configuration..")
                        .as(module + ": JDBC is used only in adapter.out"),
                classes().that().resideInAPackage(x + "..").and().areMetaAnnotatedWith(Controller.class).should()
                        .resideInAPackage(x + ".adapter.in..").as(module + ": controllers live in adapter.in"),
                classes().that().resideInAPackage(x + "..").and().areAnnotatedWith(Configuration.class).should()
                        .resideInAPackage(x + ".configuration..")
                        .as(module + ": configuration classes live in configuration"),
                classes().that().resideInAPackage(x + "..").and().areAnnotatedWith(ConfigurationProperties.class)
                        .should().resideInAPackage(x + ".configuration..")
                        .as(module + ": configuration properties live in configuration"));
    }

    private static DescribedPredicate<JavaClass> infrastructure(String[] outward) {

        return resideInAnyPackage(concat(outward, WEB, PERSISTENCE, STORAGE, new String[]{MAPPING}))
                .or(name(PERSISTABLE));
    }

    private static DescribedPredicate<JavaClass> persistenceTypes() {

        return resideInAnyPackage(concat(STORAGE, new String[]{"org.springframework.data.."}))
                .and(not(resideInAPackage("org.springframework.data.domain"))).or(name(PERSISTABLE));
    }

    private static String[] layers(String x) {

        return Stream.of("api", "domain", "application", "adapter", "configuration")
                .map(layer -> x + "." + layer + "..").toArray(String[]::new);
    }

    private static DescribedPredicate<JavaClass> outsideShared() {

        return DescribedPredicate.describe("are outside shared",
                type -> !type.getPackageName().startsWith(ROOT + ".shared."));
    }

    private static DescribedPredicate<JavaClass> notAnApplicationClass() {

        return DescribedPredicate.describe("are not an application class",
                type -> !type.isAnnotatedWith(SpringBootApplication.class)
                        && !"package-info".equals(type.getSimpleName()));
    }

    private static ArchCondition<JavaClass> sitInANamedInterface() {

        return ArchCondition
                .from(DescribedPredicate.describe("sit in a package annotated @NamedInterface(\"api\")",
                        type -> type.getPackage().tryGetAnnotationOfType(NamedInterface.class).map(named -> Stream
                                .concat(Stream.of(named.value()), Stream.of(named.name())).anyMatch("api"::equals))
                                .orElse(false)));
    }

    private static DescribedPredicate<JavaClass> useJdbc() {

        return DescribedPredicate.describe("use JDBC", type -> type.getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> JDBC.contains(dependency.getTargetClass().getName())));
    }

    private static String[] concat(String[]... groups) {

        return Stream.of(groups).flatMap(Stream::of).toArray(String[]::new);
    }
}
