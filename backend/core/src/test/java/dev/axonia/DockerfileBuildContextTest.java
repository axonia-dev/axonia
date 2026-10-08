package dev.axonia;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import static dev.axonia.DockerfileBuildContext.parse;
import static dev.axonia.DockerfileBuildContext.reachable;
import static dev.axonia.DockerfileBuildContext.violations;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analysis behind each image's guard, against fixtures: the real Dockerfiles are read by
 * {@code CloudImageBuildContextTest} and {@code SelfHostedImageBuildContextTest}.
 */
class DockerfileBuildContextTest {

    private static final String DOCKERFILE_FIXTURE = """
            ARG JDK_IMAGE=eclipse-temurin:26-jdk
            FROM ${JDK_IMAGE} AS build
            WORKDIR /build
            COPY .mvn/ .mvn/
            COPY mvnw pom.xml ./
            COPY cloud/pom.xml cloud/
            COPY cloud/src/ cloud/src/
            """;

    /**
     * A module that runs Spring Boot's {@code repackage}, which the starter parent binds for any module declaring it.
     */
    private static final String REPACKAGING_CLOUD_POM = """
            <project><parent><version>2.0</version></parent><artifactId>app</artifactId>
            <build><plugins><plugin><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
            </project>
            """;

    private static final String RUNTIME_STAGE_FIXTURE = """
            FROM ${JDK_IMAGE} AS runtime
            COPY --from=build --chown=app:app /build/cloud/target/%s app.jar
            """;

    /** A module inheriting from the aggregator: {@code <relativePath>} defaults to {@code ../pom.xml}. */
    private static final String INHERITING_CLOUD_POM = "<project><parent/></project>";

    private static final String ROOT_POM_FIXTURE = """
            <project><modules><module>cloud</module></modules></project>
            """;

    @Test
    void violations_aModuleRootedReferenceToAnUncopiedDirectory() throws Exception {

        // given
        var poms = poms(ROOT_POM_FIXTURE, cloudPomExecuting("${project.basedir}/keycloak/extension/pom.xml", ""));

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms);

        // then
        assertThat(actual).containsExactly(
                "cloud/pom.xml: exec-maven-plugin execution build reads cloud/keycloak/extension/pom.xml");
    }

    @Test
    void violations_aReactorRootedReferenceToAnUncopiedDirectory() throws Exception {

        // given
        var poms = poms(ROOT_POM_FIXTURE,
                cloudPomExecuting("${maven.multiModuleProjectDirectory}/keycloak/extension/pom.xml", ""));

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms);

        // then
        assertThat(actual)
                .containsExactly("cloud/pom.xml: exec-maven-plugin execution build reads keycloak/extension/pom.xml");
    }

    @Test
    void violations_aReferenceUnderADirectoryOfWhichOnlyThePomIsCopied() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/src/ cloud/src/\n", "");
        var poms = poms(ROOT_POM_FIXTURE, cloudPomExecuting("${project.basedir}/src/main/extra.txt", ""));

        // when
        var actual = violations(dockerfile, poms);

        // then
        assertThat(actual).containsExactlyInAnyOrder("pom.xml lists cloud, whose cloud/src is not copied",
                "cloud/pom.xml: exec-maven-plugin execution build reads cloud/src/main/extra.txt");
    }

    @Test
    void violations_noneForAReferenceTheImageCopies() throws Exception {

        // given
        var poms = poms(ROOT_POM_FIXTURE, cloudPomExecuting("${maven.multiModuleProjectDirectory}/mvnw", ""));

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_noneForAnExecutionSkippedWhenTestsAre() throws Exception {

        // given
        var poms = poms(ROOT_POM_FIXTURE,
                cloudPomExecuting("${project.basedir}/keycloak/extension/pom.xml", "<skip>${skipTests}</skip>"));

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_noneForAnExecutionPastPackage() throws Exception {

        // given
        var poms = poms(ROOT_POM_FIXTURE, cloudPomExecuting("${project.basedir}/keycloak/extension/pom.xml", "")
                .replace("<phase>process-test-resources</phase>", "<phase>verify</phase>"));

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_aModulePomTheImageDoesNotCopy() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/pom.xml cloud/\n", "");
        var poms = poms(ROOT_POM_FIXTURE, "<project/>");

        // when
        var actual = violations(dockerfile, poms);

        // then
        assertThat(actual).containsExactly("pom.xml lists cloud/pom.xml");
    }

    @Test
    void violations_aModuleListedByAProfileCounts() throws Exception {

        // given
        var rootPom = """
                <project><profiles><profile><id>cloud</id>
                <modules><module>cloud</module></modules>
                </profile></profiles></project>
                """;

        // when
        var actual = violations("COPY pom.xml ./\n", poms(rootPom, "<project/>"));

        // then
        assertThat(actual).containsExactlyInAnyOrder("pom.xml lists cloud/pom.xml",
                "pom.xml lists cloud, whose cloud/src is not copied");
    }

    @Test
    void violations_aPluginDeclaredInAProfileCounts() throws Exception {

        // given
        var cloudPom = """
                <project><profiles><profile><build><plugins>%s</plugins></build></profile></profiles></project>
                """.formatted(execPlugin("${project.basedir}/keycloak/extension/pom.xml", ""));

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms(ROOT_POM_FIXTURE, cloudPom));

        // then
        assertThat(actual).containsExactly(
                "cloud/pom.xml: exec-maven-plugin execution build reads cloud/keycloak/extension/pom.xml");
    }

    @Test
    void violations_aParentPluginReadsUnderEachModuleInheritingIt() throws Exception {

        // given
        var rootPom = ROOT_POM_FIXTURE.replace("</project>",
                "<build><plugins>" + execPlugin("${project.basedir}/mvnw", "") + "</plugins></build></project>");

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms(rootPom, INHERITING_CLOUD_POM));

        // then
        assertThat(actual).containsExactly("cloud/pom.xml: exec-maven-plugin execution build reads cloud/mvnw");
    }

    @Test
    void violations_noneFromAParentPluginThatIsNotInherited() throws Exception {

        // given
        var rootPom = ROOT_POM_FIXTURE.replace("</project>",
                "<build><plugins>" + execPlugin("${project.basedir}/mvnw", "").replace("<executions>",
                        "<inherited>false</inherited><executions>") + "</plugins></build></project>");

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms(rootPom, INHERITING_CLOUD_POM));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_aManagedExecutionReadsWhereverThePluginRuns() throws Exception {

        // given
        var rootPom = ROOT_POM_FIXTURE.replace("</project>",
                "<build><pluginManagement><plugins>"
                        + execPlugin("${maven.multiModuleProjectDirectory}/keycloak/extension/pom.xml", "")
                        + "</plugins></pluginManagement></build></project>");
        var cloudPom = "<project><parent/><build><plugins><plugin><artifactId>exec-maven-plugin</artifactId></plugin>"
                + "</plugins></build></project>";

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms(rootPom, cloudPom));

        // then
        assertThat(actual)
                .containsExactly("cloud/pom.xml: exec-maven-plugin execution build reads keycloak/extension/pom.xml");
    }

    @Test
    void violations_theConfigurationOfAPluginWithoutExecutions() throws Exception {

        // given
        var cloudPom = """
                <project><build><plugins><plugin><artifactId>maven-resources-plugin</artifactId>
                <configuration><extra>${project.basedir}/keycloak/themes</extra></configuration>
                </plugin></plugins></build></project>
                """;

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms(ROOT_POM_FIXTURE, cloudPom));

        // then
        assertThat(actual)
                .containsExactly("cloud/pom.xml: maven-resources-plugin configuration reads cloud/keycloak/themes");
    }

    @Test
    void violations_resourceDirectoriesTheImageDoesNotCopy() throws Exception {

        // given
        var cloudPom = """
                <project><build>
                <resources><resource><directory>${maven.multiModuleProjectDirectory}/keycloak/themes</directory>
                </resource></resources>
                <testResources><testResource><directory>../config</directory></testResource></testResources>
                </build></project>
                """;

        // when
        var actual = violations(DOCKERFILE_FIXTURE, poms(ROOT_POM_FIXTURE, cloudPom));

        // then
        assertThat(actual).containsExactlyInAnyOrder("cloud/pom.xml: resource directory reads keycloak/themes",
                "cloud/pom.xml: resource directory reads config");
    }

    @Test
    void violations_aModuleWhoseSourcesTheImageDoesNotCopy() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/src/ cloud/src/\n", "");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, "<project/>"));

        // then
        assertThat(actual).containsExactly("pom.xml lists cloud, whose cloud/src is not copied");
    }

    @Test
    void violations_noneForTheSourcesOfAnAggregatorModule() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/src/ cloud/src/\n", "");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, "<project><packaging>pom</packaging></project>"));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_aModuleWhoseSourcesAreCopiedToAnotherPath() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/src/ cloud/src/", "COPY cloud/src/ src/");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, "<project/>"));

        // then
        assertThat(actual).containsExactly("pom.xml lists cloud, whose cloud/src is not copied");
    }

    @Test
    void violations_aFileCopiedIntoAnotherDirectory() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/pom.xml cloud/", "COPY cloud/pom.xml ./");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, "<project/>"));

        // then
        assertThat(actual).containsExactly("pom.xml lists cloud/pom.xml");
    }

    @Test
    void violations_noneForAFileCopiedUnderAnotherNameAtItsOwnPath() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/pom.xml cloud/", "COPY cloud/pom.xml cloud/pom.xml");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, "<project/>"));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_theRuntimeStageCopiesAJarNoModulePackages() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE + RUNTIME_STAGE_FIXTURE.formatted("app-1.0.jar");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, REPACKAGING_CLOUD_POM));

        // then
        assertThat(actual).containsExactly(
                "the runtime stage copies /build/cloud/target/app-1.0.jar, but the reactor packages [cloud/target/app-2.0.jar]");
    }

    @Test
    void violations_noneForTheJarTheRepackagingModuleWrites() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE + RUNTIME_STAGE_FIXTURE.formatted("app-2.0.jar");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, REPACKAGING_CLOUD_POM));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_noneForTheJarNamedByTheFinalName() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE + RUNTIME_STAGE_FIXTURE.formatted("server.jar");
        var cloudPom = REPACKAGING_CLOUD_POM.replace("<build>", "<build><finalName>server</finalName>");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, cloudPom));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_noneForABuildStageRunOnTheBuildersPlatform() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("FROM ${JDK_IMAGE} AS build",
                "FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS build")
                + RUNTIME_STAGE_FIXTURE.formatted("app-2.0.jar");

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, REPACKAGING_CLOUD_POM));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_theRuntimeStageCopiesNoJar() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE + """
                FROM ${JDK_IMAGE} AS runtime
                COPY --from=build /build/cloud/target/ app/
                """;

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, REPACKAGING_CLOUD_POM));

        // then
        assertThat(actual).containsExactly(
                "the runtime stage copies 0 jars out of the build stage, where it must copy exactly one of [cloud/target/app-2.0.jar]");
    }

    @Test
    void violations_theRuntimeStageCopiesTwoJars() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE + RUNTIME_STAGE_FIXTURE.formatted("app-2.0.jar")
                + "COPY --from=build /build/cloud/target/app-2.0.jar again.jar\n";

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, REPACKAGING_CLOUD_POM));

        // then
        assertThat(actual).containsExactly(
                "the runtime stage copies 2 jars out of the build stage, where it must copy exactly one of [cloud/target/app-2.0.jar]");
    }

    @Test
    void violations_aContextCopyInAnotherStageDoesNotReachTheBuild() throws Exception {

        // given
        var dockerfile = DOCKERFILE_FIXTURE.replace("COPY cloud/src/ cloud/src/\n", "") + """
                FROM ${JDK_IMAGE} AS runtime
                COPY cloud/src/ cloud/src/
                """;

        // when
        var actual = violations(dockerfile, poms(ROOT_POM_FIXTURE, "<project/>"));

        // then
        assertThat(actual).containsExactly("pom.xml lists cloud, whose cloud/src is not copied");
    }

    private static final String FILE_ACTIVATED_ROOT_POM = """
            <project><modules><module>core</module></modules>
            <profiles><profile><id>cloud</id>
            <activation><file><exists>${basedir}/cloud/pom.xml</exists></file></activation>
            <modules><module>cloud</module></modules>
            </profile></profiles></project>
            """;

    @Test
    void violations_noneForTheModuleOfAProfileWhoseFileTheImageDoesNotCopy() throws Exception {

        // given
        var poms = new LinkedHashMap<String, Document>();
        poms.put("", parse(FILE_ACTIVATED_ROOT_POM));
        poms.put("core", parse("<project/>"));
        poms.put("cloud", parse(REPACKAGING_CLOUD_POM));
        var dockerfile = "FROM x AS build\nWORKDIR /build\nCOPY pom.xml ./\nCOPY core/pom.xml core/\nCOPY core/src/ core/src/\n";

        // when
        var actual = violations(dockerfile, poms);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void violations_theModuleOfAProfileWhoseFileTheImageCopies() throws Exception {

        // given
        var poms = new LinkedHashMap<String, Document>();
        poms.put("", parse(FILE_ACTIVATED_ROOT_POM));
        poms.put("core", parse("<project/>"));
        poms.put("cloud", parse("<project/>"));
        var dockerfile = "FROM x AS build\nWORKDIR /build\nCOPY pom.xml ./\nCOPY core/pom.xml core/\nCOPY core/src/ core/src/\nCOPY cloud/pom.xml cloud/\n";

        // when
        var actual = violations(dockerfile, poms);

        // then
        assertThat(actual).containsExactly("pom.xml lists cloud, whose cloud/src is not copied");
    }

    @Test
    void violations_theRuntimeStageCopiesTheJarOfAModuleTheBuildDidNotSelect() throws Exception {

        // given
        var poms = new LinkedHashMap<String, Document>();
        poms.put("", parse("<project><modules><module>cloud</module><module>other</module></modules></project>"));
        poms.put("cloud", parse(REPACKAGING_CLOUD_POM));
        poms.put("other",
                parse(REPACKAGING_CLOUD_POM.replace("<artifactId>app</artifactId>", "<artifactId>other</artifactId>")));
        var dockerfile = """
                FROM x AS build
                WORKDIR /build
                COPY . ./
                RUN ./mvnw -B -pl cloud -am clean package -DskipTests
                FROM y AS runtime
                COPY --from=build /build/other/target/other-2.0.jar app.jar
                """;

        // when
        var actual = violations(dockerfile, poms);

        // then
        assertThat(actual).containsExactly(
                "the runtime stage copies /build/other/target/other-2.0.jar, but the reactor packages [cloud/target/app-2.0.jar]");
    }

    @Test
    void reachable_skipsTheModuleOfAProfileWhoseFileIsAbsent() throws Exception {

        // given
        var poms = new LinkedHashMap<String, Document>();
        poms.put("", parse(FILE_ACTIVATED_ROOT_POM));
        poms.put("core", parse("<project/>"));
        poms.put("cloud", parse("<project/>"));

        // when
        var actual = reachable(poms, path -> false);

        // then
        assertThat(actual).containsOnlyKeys("", "core");
    }

    private static Map<String, Document> poms(String rootPom, String cloudPom)
            throws IOException, ParserConfigurationException, SAXException {

        var poms = new LinkedHashMap<String, Document>();
        poms.put("", parse(rootPom));
        poms.put("cloud", parse(cloudPom));
        return poms;
    }

    private static String cloudPomExecuting(String reference, String skip) {

        return "<project><build><plugins>" + execPlugin(reference, skip) + "</plugins></build></project>";
    }

    private static String execPlugin(String reference, String skip) {

        return """
                <plugin><artifactId>exec-maven-plugin</artifactId><executions><execution>
                <id>build</id><phase>process-test-resources</phase>
                <configuration>%s<arguments><argument>%s</argument></arguments></configuration>
                </execution></executions></plugin>
                """.formatted(skip, reference);
    }
}
