package dev.axonia;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Every file an image's build reads must be inside the context that build is given: the analysis each image's guard
 * runs ({@code CloudImageBuildContextTest}, {@code SelfHostedImageBuildContextTest}).
 *
 * <p>
 * {@code backend/Dockerfile} copies a handful of paths and runs {@code package -DskipTests}. Nothing but this test
 * checks that those paths are enough before the image is built, and for a long time nothing in CI built the image at
 * all: a platform that builds from the directory does the build itself. A pom referencing a path outside the context
 * therefore passed the whole quality gate twice over and failed in production.
 *
 * <p>
 * That is not hypothetical. A change once added an execution building {@code keycloak/extension/} - a directory the app
 * image has no reason to carry - bound to {@code process-test-resources}, which runs whether or not tests do. Both
 * suite orders were green, the extension's own gates were green, and the deploy failed on {@code POM file
 * /build/keycloak/extension/pom.xml ... does not exist}.
 *
 * <p>
 * The build is read as a reactor: {@code backend/pom.xml} and every module it lists, at top level or in a profile. Each
 * module runs the plugins of its own build and profiles, those it inherits from the reactor poms above it (with
 * {@code ${project.basedir}} then naming the inheriting module), and the {@code <pluginManagement>} entries of any
 * plugin it runs; it reads its resource directories, and a module that is not {@code pom}-packaged reads its whole
 * {@code src/}. A path counts as copied only when a {@code COPY} puts it at that same path in the build stage, from a
 * source that is the path or a directory above it: copying a module's pom does not stand in for the rest of that
 * module, and copying a module's sources anywhere but their own path compiles nothing.
 *
 * <p>
 * The runtime stage copies exactly one jar out of the build stage, and it must be the one the module running Spring
 * Boot's {@code repackage} writes: its directory, {@code artifactId} and version, or its {@code finalName}.
 */
public final class DockerfileBuildContext {

    private static final String BACKEND = "backend";

    private static final String ROOT_MODULE = "";

    /** A {@code COPY} of the repository into the image. Lines carrying {@code --from} copy between stages instead. */
    private static final Pattern CONTEXT_COPY = Pattern.compile("^COPY\\s+(?!--from)(.*)$", Pattern.MULTILINE);

    /** A {@code COPY} out of another stage, whatever flags come before the {@code --from}. */
    private static final Pattern STAGE_COPY = Pattern.compile("^COPY\\s+(.*--from=(\\S+).*)$", Pattern.MULTILINE);

    /** A named stage, whatever flags such as {@code --platform} come before its image. */
    private static final Pattern STAGE = Pattern.compile("^FROM\\s+(?:--\\S+\\s+)*\\S+\\s+AS\\s+(\\S+)\\s*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern WORKDIR = Pattern.compile("^WORKDIR\\s+(\\S+)\\s*$", Pattern.MULTILINE);

    /** A {@code RUN} of the build stage that packages one module with {@code -pl}. */
    private static final Pattern PACKAGED_MODULE = Pattern.compile("^RUN\\s.*\\s-pl\\s+(\\S+)\\s.*\\bpackage\\b",
            Pattern.MULTILINE);

    /** A path rooted at the directory of the pom that names it. */
    private static final Pattern MODULE_REFERENCE = Pattern.compile("\\$\\{project\\.basedir}/([^<\\s\"]+)");

    /** A path rooted at {@code backend/}, the directory holding {@code .mvn/}, whichever module names it. */
    private static final Pattern REACTOR_REFERENCE = Pattern
            .compile("\\$\\{maven\\.multiModuleProjectDirectory}/([^<\\s\"]+)");

    /**
     * Maven's default lifecycle up to and including {@code package}, which is where the Dockerfile stops. An execution
     * bound past this point cannot break the image build, which is why the quality plugins may go on pointing at
     * {@code config/}: every one of them declares {@code verify}.
     */
    private static final Set<String> PHASES_PACKAGE_REACHES = Set.of("validate", "initialize", "generate-sources",
            "process-sources", "generate-resources", "process-resources", "compile", "process-classes",
            "generate-test-sources", "process-test-sources", "generate-test-resources", "process-test-resources",
            "test-compile", "process-test-classes", "test", "prepare-package", "package");

    private DockerfileBuildContext() {
    }

    /** Every violation of the Dockerfile at this repository-relative path, against the reactor its build sees. */
    public static List<String> violations(String dockerfilePath)
            throws IOException, ParserConfigurationException, SAXException {

        return violations(Files.readString(RepositoryRoot.file(dockerfilePath)), reactor());
    }

    /** The jars the build of the Dockerfile at this repository-relative path writes, relative to {@code backend/}. */
    public static List<String> jarsTheBuildWrites(String dockerfilePath)
            throws IOException, ParserConfigurationException, SAXException {

        var dockerfile = Files.readString(RepositoryRoot.file(dockerfilePath));
        var copied = pathsCopiedIntoTheImage(dockerfile);
        return jarsTheBuildWrites(dockerfile, reachable(reactor(), path -> isCopied(path, copied)));
    }

    /**
     * Every path a packaged build of the reactor reads that the Dockerfile does not copy, every module pom and module
     * source tree it does not copy, and a runtime jar the build does not package.
     *
     * @param pomsByModuleDirectory
     *            each pom of the reactor, keyed by its directory relative to {@code backend/}, the aggregator under the
     *            empty string
     */
    static List<String> violations(String dockerfile, Map<String, Document> pomsByModuleDirectory) {

        var copied = pathsCopiedIntoTheImage(dockerfile);
        Predicate<String> present = path -> isCopied(path, copied);
        var reactor = reachable(pomsByModuleDirectory, present);
        var violations = new ArrayList<String>();
        reactor.forEach((module, pom) -> {
            var pomPath = under(module, "pom.xml");
            for (var listed : modulesListedBy(pom.getDocumentElement(), module, present)
                    .map(name -> under(module, name)).toList()) {
                var listedPom = under(listed, "pom.xml");
                if (!isCopied(listedPom, copied)) {
                    violations.add(pomPath + " lists " + listedPom);
                }
                var listedSources = under(listed, "src");
                if (readsItsSources(reactor.get(listed)) && !isCopied(listedSources, copied)) {
                    violations.add(pomPath + " lists " + listed + ", whose " + listedSources + " is not copied");
                }
            }
        });
        reads(reactor).stream().filter(read -> !read.skipped() && !isCopied(read.path(), copied)).map(Read::toString)
                .forEach(violations::add);
        var packaged = jarsTheBuildWrites(dockerfile, reactor);
        var runtimeJars = jarsCopiedOutOfTheBuild(dockerfile);
        if (!packaged.isEmpty() && runtimeJars.size() != 1) {
            violations.add("the runtime stage copies " + runtimeJars.size()
                    + " jars out of the build stage, where it must copy exactly one of " + packaged);
        }
        runtimeJars.stream().filter(jar -> !packaged.contains(jar.relativePath()))
                .map(jar -> "the runtime stage copies " + jar.source() + ", but the reactor packages " + packaged)
                .forEach(violations::add);
        return violations;
    }

    /**
     * The jars of the module the build's {@code package} names with {@code -pl}, or of every repackaging module when it
     * names none: with two applications in the reactor, the runtime stage must copy the one its build selected.
     */
    static List<String> jarsTheBuildWrites(String dockerfile, Map<String, Document> pomsByModuleDirectory) {

        var packaged = jarsPackagedBy(pomsByModuleDirectory);
        return PACKAGED_MODULE.matcher(buildStage(dockerfile)).results().map(match -> match.group(1) + "/").findFirst()
                .map(module -> packaged.stream().filter(jar -> jar.startsWith(module)).toList()).orElse(packaged);
    }

    /** The poms the build reaches from the aggregator, through each module list that is on in the image. */
    static Map<String, Document> reachable(Map<String, Document> pomsByModuleDirectory, Predicate<String> present) {

        var reached = new LinkedHashMap<String, Document>();
        var pending = new ArrayDeque<String>();
        pending.add(ROOT_MODULE);
        while (!pending.isEmpty()) {
            var module = pending.pop();
            var pom = pomsByModuleDirectory.get(module);
            if (pom != null && !reached.containsKey(module)) {
                reached.put(module, pom);
                modulesListedBy(pom.getDocumentElement(), module, present).map(listed -> under(module, listed))
                        .forEach(pending::add);
            }
        }
        return reached;
    }

    /**
     * The jar each module running Spring Boot's {@code repackage} writes, relative to {@code backend/}: a module that
     * is not {@code pom}-packaged and runs {@code spring-boot-maven-plugin}, whose executions the starter parent binds.
     */
    public static List<String> jarsPackagedBy(Map<String, Document> pomsByModuleDirectory) {

        return pomsByModuleDirectory.entrySet().stream().filter(module -> readsItsSources(module.getValue()))
                .filter(module -> pluginsRunBy(lineageOf(module.getKey(), pomsByModuleDirectory)).stream()
                        .anyMatch(plugin -> "spring-boot-maven-plugin".equals(text(child(plugin, "artifactId")))))
                .map(module -> under(module.getKey(),
                        "target/" + finalNameOf(module.getValue().getDocumentElement()) + ".jar"))
                .toList();
    }

    private static String finalNameOf(Element project) {

        var finalName = child(child(project, "build"), "finalName");
        if (finalName != null) {
            return text(finalName);
        }
        var version = child(project, "version");
        return text(child(project, "artifactId")) + "-"
                + text(version == null ? child(child(project, "parent"), "version") : version);
    }

    /**
     * A jar one stage copies out of another.
     *
     * @param relativePath
     *            the source relative to the working directory of the stage it comes from, which is where the reactor
     *            ran
     */
    record StageJar(String source, String relativePath) {
    }

    private static List<StageJar> jarsCopiedOutOfTheBuild(String dockerfile) {

        return STAGE_COPY.matcher(dockerfile).results().flatMap(line -> {
            var workdir = workdirOf(dockerfile, line.group(2));
            var arguments = arguments(line.group(1));
            return arguments.subList(0, arguments.size() - 1).stream().filter(source -> source.endsWith(".jar"))
                    .map(source -> new StageJar(source,
                            !workdir.isEmpty() && source.startsWith(workdir + "/")
                                    ? source.substring(workdir.length() + 1)
                                    : source));
        }).toList();
    }

    /** The {@code WORKDIR} a stage sets. */
    private static String workdirOf(String dockerfile, String stage) {

        var workdir = WORKDIR.matcher(stageNamed(dockerfile, stage));
        return workdir.find() ? workdir.group(1) : "";
    }

    /**
     * The stage the reactor is built in: the one the runtime stage copies out of, else the first named stage, else the
     * whole file when it names no stage.
     */
    private static String buildStage(String dockerfile) {

        var copiedFrom = STAGE_COPY.matcher(dockerfile).results().map(match -> match.group(2)).findFirst();
        var firstNamed = STAGE.matcher(dockerfile).results().map(match -> match.group(1)).findFirst();
        return copiedFrom.or(() -> firstNamed).map(stage -> stageNamed(dockerfile, stage)).orElse(dockerfile);
    }

    /** The instructions of one stage: from its {@code FROM} to the next one. */
    private static String stageNamed(String dockerfile, String stage) {

        var from = STAGE.matcher(dockerfile).results().filter(match -> stage.equals(match.group(1))).findFirst()
                .map(MatchResult::end).orElse(dockerfile.length());
        var next = STAGE.matcher(dockerfile).results().map(MatchResult::start).filter(start -> start > from).findFirst()
                .orElse(dockerfile.length());
        return dockerfile.substring(from, next);
    }

    /** Every path each module of the reactor reads in a packaged build, those skipped when tests are included. */
    public static List<Read> reads(Map<String, Document> pomsByModuleDirectory) {

        var reads = new ArrayList<Read>();
        for (var module : pomsByModuleDirectory.keySet()) {
            var lineage = lineageOf(module, pomsByModuleDirectory);
            var managed = lineage.stream().flatMap(project -> managedPluginsOf(project).stream()).toList();
            for (var plugin : pluginsRunBy(lineage)) {
                var artifactId = text(child(plugin, "artifactId"));
                Stream.concat(Stream.of(plugin),
                        managed.stream().filter(entry -> artifactId.equals(text(child(entry, "artifactId")))))
                        .flatMap(declaration -> readsOf(module, declaration)).forEach(reads::add);
            }
            lineage.stream().flatMap(project -> resourceDirectoriesOf(module, project)).forEach(reads::add);
        }
        return reads;
    }

    /**
     * One path one module reads.
     *
     * @param skipped
     *            whether the execution sits out a build that skips tests, as the Dockerfile's does
     */
    public record Read(String pom, String reader, String path, boolean skipped) {

        @Override
        public String toString() {

            return pom + ": " + reader + " reads " + path;
        }
    }

    /** A module reads its own {@code src/} unless it only aggregates, which {@code pom} packaging says. */
    private static boolean readsItsSources(Document pom) {

        return pom == null || !"pom".equals(text(child(pom.getDocumentElement(), "packaging")));
    }

    /**
     * The module's own pom first, then each reactor pom it inherits from, found through {@code <parent>}'s
     * {@code <relativePath>}: {@code ../pom.xml} when absent, no reactor parent when empty.
     */
    private static List<Element> lineageOf(String module, Map<String, Document> pomsByModuleDirectory) {

        var lineage = new ArrayList<Element>();
        var current = module;
        while (current != null && pomsByModuleDirectory.containsKey(current)) {
            var project = pomsByModuleDirectory.get(current).getDocumentElement();
            lineage.add(project);
            current = parentDirectoryOf(current, project);
        }
        return lineage;
    }

    private static String parentDirectoryOf(String module, Element project) {

        var parent = child(project, "parent");
        var relativePath = child(parent, "relativePath");
        if (parent == null || relativePath != null && text(relativePath).isEmpty()) {
            return null;
        }
        var target = Path.of(under(module, relativePath == null ? "../pom.xml" : text(relativePath)));
        var directory = target.toString().endsWith(".xml") ? target.getParent() : target;
        return directory == null ? ROOT_MODULE : normalised(directory.toString());
    }

    /** The plugins a module runs: its own, and those of the poms above it not marked {@code inherited=false}. */
    private static List<Element> pluginsRunBy(List<Element> lineage) {

        var plugins = new ArrayList<Element>();
        for (var i = 0; i < lineage.size(); i++) {
            var inherited = i > 0;
            pluginSections(lineage.get(i)).stream().flatMap(section -> childElements(section, "plugin").stream())
                    .filter(plugin -> !inherited || !"false".equals(text(child(plugin, "inherited"))))
                    .forEach(plugins::add);
        }
        return plugins;
    }

    private static List<Element> managedPluginsOf(Element project) {

        return buildSections(project).stream()
                .flatMap(build -> childElements(child(child(build, "pluginManagement"), "plugins"), "plugin").stream())
                .toList();
    }

    /**
     * Resource directories are processed even with {@code -DskipTests}, test resources included. A relative directory
     * is under the module, as is one rooted at {@code ${project.basedir}}.
     */
    private static Stream<Read> resourceDirectoriesOf(String module, Element project) {

        return buildSections(project).stream()
                .flatMap(build -> Stream.concat(childElements(child(build, "resources"), "resource").stream(),
                        childElements(child(build, "testResources"), "testResource").stream()))
                .map(resource -> child(resource, "directory")).filter(Objects::nonNull)
                .map(directory -> new Read(under(module, "pom.xml"), "resource directory",
                        directoryOf(module, text(directory)), false));
    }

    private static String directoryOf(String module, String directory) {

        var reactorRooted = REACTOR_REFERENCE.matcher(directory);
        if (reactorRooted.matches()) {
            return normalised(reactorRooted.group(1));
        }
        return under(module, directory.replace("${project.basedir}/", ""));
    }

    /**
     * The paths one declaration of a plugin makes a module read. With executions of its own, each execution a packaged
     * build reaches reads its configuration and the plugin's; without any, the plugin runs only through the lifecycle's
     * default bindings, which this test cannot see, so its configuration counts as read.
     */
    private static Stream<Read> readsOf(String module, Element plugin) {

        var pom = under(module, "pom.xml");
        var artifactId = text(child(plugin, "artifactId"));
        var sharedByEveryExecution = referencesIn(module, child(plugin, "configuration"));
        var executions = childElements(child(plugin, "executions"), "execution");
        if (executions.isEmpty()) {
            return sharedByEveryExecution.stream()
                    .map(path -> new Read(pom, artifactId + " configuration", path, false));
        }
        return executions.stream().filter(DockerfileBuildContext::reachedByPackage)
                .flatMap(execution -> Stream
                        .concat(sharedByEveryExecution.stream(),
                                referencesIn(module, child(execution, "configuration")).stream())
                        .map(path -> new Read(pom, artifactId + " execution " + text(child(execution, "id")), path,
                                skippedWhenTestsAre(execution))));
    }

    private static List<Element> pluginSections(Element project) {

        return buildSections(project).stream().map(build -> child(build, "plugins")).toList();
    }

    private static List<Element> buildSections(Element project) {

        var sections = new ArrayList<Element>();
        sections.add(child(project, "build"));
        for (var profile : childElements(child(project, "profiles"), "profile")) {
            sections.add(child(profile, "build"));
        }
        return sections;
    }

    /**
     * The modules a pom lists, at top level and in each profile that is on where {@code present} says which files
     * exist. A profile activated by a file is on only where that file is present; any other profile counts as on.
     */
    static Stream<String> modulesListedBy(Element project, String module, Predicate<String> present) {

        var modules = new ArrayList<>(childElements(child(project, "modules"), "module"));
        for (var profile : childElements(child(project, "profiles"), "profile")) {
            if (isActive(profile, module, present)) {
                modules.addAll(childElements(child(profile, "modules"), "module"));
            }
        }
        return modules.stream().map(DockerfileBuildContext::text);
    }

    private static boolean isActive(Element profile, String module, Predicate<String> present) {

        var exists = child(child(child(profile, "activation"), "file"), "exists");
        if (exists == null) {
            return true;
        }
        return present.test(under(module, text(exists).replace("${project.basedir}/", "").replace("${basedir}/", "")));
    }

    /**
     * One source of a {@code COPY} into the build stage.
     *
     * @param directory
     *            whether the source is written as a directory, with a trailing slash, as every one in the Dockerfile
     *            is; Docker then copies its content, so the directory itself lands on the destination
     * @param intoDirectory
     *            whether the destination is a directory a file source lands in under its own name: written with a
     *            trailing slash, or the destination of several sources
     */
    record Copy(String source, boolean directory, String destination, boolean intoDirectory) {

        /** Whether this copy puts {@code path} at that same path, relative to the build stage's working directory. */
        boolean placesAtItsOwnPath(String path) {

            if (source.isEmpty()) {
                return path.equals(under(destination, path));
            }
            if (path.equals(source)) {
                var landing = directory || !intoDirectory
                        ? destination
                        : under(destination, Path.of(source).getFileName().toString());
                return path.equals(normalised(landing));
            }
            return path.startsWith(source + "/")
                    && path.equals(under(destination, path.substring(source.length() + 1)));
        }
    }

    private static List<Copy> pathsCopiedIntoTheImage(String dockerfile) {

        return CONTEXT_COPY.matcher(buildStage(dockerfile)).results().flatMap(line -> {
            var arguments = arguments(line.group(1));
            var destination = arguments.getLast();
            var sources = arguments.subList(0, arguments.size() - 1);
            var intoDirectory = destination.endsWith("/") || sources.size() > 1;
            return sources.stream().map(source -> new Copy(normalised(source), source.endsWith("/"),
                    normalised(destination), intoDirectory));
        }).toList();
    }

    private static List<String> arguments(String instruction) {

        return Stream.of(instruction.trim().split("\\s+")).filter(argument -> !argument.startsWith("--")).toList();
    }

    private static boolean isCopied(String path, List<Copy> copied) {

        return copied.stream().anyMatch(copy -> copy.placesAtItsOwnPath(path));
    }

    private static boolean reachedByPackage(Element execution) {

        var phase = child(execution, "phase");
        return phase == null || PHASES_PACKAGE_REACHES.contains(text(phase));
    }

    /**
     * An execution nothing but the test suite needs may sit out the image build, and says so the only way the build can
     * read: the same flag the Dockerfile passes.
     */
    private static boolean skippedWhenTestsAre(Element execution) {

        var skip = child(child(execution, "configuration"), "skip");
        return skip != null && "${skipTests}".equals(text(skip));
    }

    /** The paths a configuration names, relative to {@code backend/}. */
    private static List<String> referencesIn(String module, Element configuration) {

        if (configuration == null) {
            return List.of();
        }
        var content = configuration.getTextContent();
        return Stream.concat(MODULE_REFERENCE.matcher(content).results().map(match -> under(module, match.group(1))),
                REACTOR_REFERENCE.matcher(content).results().map(match -> normalised(match.group(1)))).toList();
    }

    private static String under(String module, String path) {

        return normalised(module.isEmpty() ? path : module + "/" + path);
    }

    private static String normalised(String path) {

        return Path.of(path).normalize().toString();
    }

    /** The reactor as the repository holds it: a module of a file-activated profile is read when that file exists. */
    public static Map<String, Document> reactor() throws IOException, ParserConfigurationException, SAXException {

        var poms = new LinkedHashMap<String, Document>();
        addWithModules(ROOT_MODULE, poms);
        return poms;
    }

    private static void addWithModules(String module, Map<String, Document> poms)
            throws IOException, ParserConfigurationException, SAXException {

        var pomPath = BACKEND + "/" + under(module, "pom.xml");
        try (var in = Files.newInputStream(RepositoryRoot.file(pomPath))) {
            poms.put(module, parse(in));
        }
        for (var listed : modulesListedBy(poms.get(module).getDocumentElement(), module,
                DockerfileBuildContext::existsInTheBackend).toList()) {
            addWithModules(under(module, listed), poms);
        }
    }

    private static boolean existsInTheBackend(String path) {

        return Files.exists(RepositoryRoot.resolve().resolve(BACKEND).resolve(path));
    }

    static Document parse(String xml) throws IOException, ParserConfigurationException, SAXException {

        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Document parse(InputStream xml) throws IOException, ParserConfigurationException, SAXException {

        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(xml);
    }

    private static Element child(Element parent, String name) {

        return childElements(parent, name).stream().findFirst().orElse(null);
    }

    private static List<Element> childElements(Element parent, String name) {

        if (parent == null) {
            return List.of();
        }
        var found = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static String text(Element element) {

        return element == null ? "?" : element.getTextContent().trim();
    }
}
