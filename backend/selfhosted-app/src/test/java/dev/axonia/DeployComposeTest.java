package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.yaml.snakeyaml.Yaml;

import dev.axonia.selfhosted.SelfHostedProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code deploy/compose.yaml} starts an installation: every setting the backend or the edge cannot start without is
 * required there by name, every optional one passes through unset so the application's own default applies, and nothing
 * points the backend at a mail server, which is configured in the application. {@code compose.test.yaml} adds only the
 * end-to-end stack's stand-ins.
 */
class DeployComposeTest {

    private static final List<String> BACKEND_PROPERTIES = List.of(
            "backend/selfhosted-app/src/main/resources/application.properties",
            "backend/selfhosted-app/src/main/resources/axonia-selfhosted.properties",
            "backend/core/src/main/resources/axonia-core.properties");

    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    private static final Pattern OPTIONAL_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*):[^}]*}");

    private static final Pattern REQUIRED_BY_COMPOSE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*):\\?");

    private static final Pattern DEFAULTED_BY_COMPOSE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*):-");

    /**
     * {@code ${NAME}}, {@code ${NAME-default}} or {@code ${NAME:-}}: an unset or empty variable would become an empty
     * setting.
     */
    private static final Pattern NEITHER_REQUIRED_NOR_DEFAULTED = Pattern
            .compile("\\$\\{[A-Z][A-Z0-9_]*+(?!:\\?|:-[^}])");

    /** The overlay variables of the 6.0 contract, each overriding and locking one field of the mail server card. */
    private static final List<String> MAIL_OVERLAY = List.of("AXONIA_MAIL_HOST", "AXONIA_MAIL_PORT",
            "AXONIA_MAIL_SECURITY", "AXONIA_MAIL_USERNAME", "AXONIA_MAIL_PASSWORD", "AXONIA_MAIL_FROM_ADDRESS",
            "AXONIA_MAIL_FROM_NAME");

    /** The image sets it, and the media volume is mounted at its value. */
    private static final String IMAGE_OWNED = "MEDIA_LOCAL_PATH";

    /**
     * Where each release publishes both images; compose and the chart run them unless an installation names a mirror.
     */
    private static final String PUBLISHED_REGISTRY = "ghcr.io/axonia-dev";

    private static final String VERSION = "${AXONIA_VERSION:?set AXONIA_VERSION to the release to run}";

    private static final String COMPOSE_TEXT = read("deploy/compose.yaml");

    private static final String TEST_COMPOSE_TEXT = read("deploy/compose.test.yaml");

    private static final Map<String, Object> COMPOSE = new Yaml().load(COMPOSE_TEXT);

    private static final Map<String, Object> TEST_COMPOSE = new Yaml().load(TEST_COMPOSE_TEXT);

    @Test
    void theBackendIsGivenEveryVariableItCannotStartWithout() {

        var environment = environment(COMPOSE, "backend");

        assertThat(requiredByTheBackend()).isNotEmpty()
                .allSatisfy(name -> assertThat(environment.get(name)).as(name).isNotNull());
    }

    @Test
    void theBackendIsPassedEveryOptionalVariableOnlyWhenItIsSet() {

        var environment = environment(COMPOSE, "backend");

        assertThat(optionalForTheBackend()).isNotEmpty().allSatisfy(name -> {
            assertThat(environment).as(name).containsKey(name);
            assertThat(environment.get(name)).as(name + " passes through, unset unless .env sets it").isNull();
        });
        assertThat(environment).doesNotContainKey(IMAGE_OWNED);
    }

    @Test
    void theEdgeIsGivenEverySettingItCannotStartWithoutButTheOneItsImageSets() {

        var environment = environment(COMPOSE, "edge");
        var required = requiredByTheEdge();

        assertThat(required).contains("STATIC_DIR");
        assertThat(required).filteredOn(name -> !"STATIC_DIR".equals(name))
                .allSatisfy(name -> assertThat(environment.get(name)).as(name).isNotNull());
        assertThat(environment).doesNotContainKey("STATIC_DIR");
        assertThat(read("selfhosted-edge/Dockerfile")).containsPattern("(?m)^ENV STATIC_DIR=/srv/app$");
    }

    @Test
    void theEdgeSecretRotatesInTwoStepsWithTheBackendAcceptingTheEdgesOneByDefault() {

        assertThat(environment(COMPOSE, "backend").get("AXONIA_EDGE_SHARED_SECRETS")).isEqualTo(
                "${AXONIA_EDGE_SHARED_SECRETS:-${EDGE_SHARED_SECRET:?set EDGE_SHARED_SECRET (openssl rand -hex 32)}}");
        assertThat(environment(COMPOSE, "edge").get("EDGE_SHARED_SECRET"))
                .isEqualTo("${EDGE_SHARED_SECRET:?set EDGE_SHARED_SECRET (openssl rand -hex 32)}");
    }

    @Test
    void postgresCreatesTheOwnerRoleTheBackendConnectsAs() {

        assertThat(environment(COMPOSE, "postgres").get("POSTGRES_USER"))
                .isEqualTo(environment(COMPOSE, "backend").get("DATABASE_OWNER_USERNAME"))
                .isEqualTo("${DATABASE_OWNER_USERNAME:-axonia_owner}");
        assertThat(environment(COMPOSE, "postgres").get("POSTGRES_PASSWORD"))
                .isEqualTo(environment(COMPOSE, "backend").get("DATABASE_OWNER_PASSWORD"));
    }

    @Test
    void theEdgeListensOnLoopbackBehindOneProxyByDefault() {

        assertThat(service(COMPOSE, "edge").get("ports")).isEqualTo(List.of("${AXONIA_LISTEN:-127.0.0.1:8080}:8080"));
        assertThat(environment(COMPOSE, "edge").get("TRUSTED_PROXY_HOPS")).isEqualTo("${TRUSTED_PROXY_HOPS:-1}");
    }

    @Test
    void theBackendWaitsForPostgresAndTheEdgeForAReadyBackend() {

        assertThat(service(COMPOSE, "backend").get("depends_on"))
                .isEqualTo(Map.of("postgres", Map.of("condition", "service_healthy")));
        assertThat(service(COMPOSE, "edge").get("depends_on"))
                .isEqualTo(Map.of("backend", Map.of("condition", "service_healthy")));
        assertThat(healthcheck("backend")).contains("GET /actuator/health/readiness HTTP/1.1").contains("' 200 '");
        assertThat(healthcheck("edge")).contains("http://127.0.0.1:8080/healthz");
        assertThat(section(service(COMPOSE, "backend"), "healthcheck")).as("the chart's startup probe allows 600 s")
                .containsEntry("start_period", "600s");
    }

    @Test
    void bothApplicationsRunOnAReadOnlyRoot() {

        assertThat(service(COMPOSE, "backend").get("read_only")).isEqualTo(true);
        assertThat(service(COMPOSE, "backend").get("tmpfs")).isEqualTo(List.of("/tmp"));
        assertThat(service(COMPOSE, "edge").get("read_only")).isEqualTo(true);
        assertThat(service(COMPOSE, "edge").get("tmpfs")).isEqualTo(List.of("/tmp"));
    }

    @Test
    void everyVariableIsRequiredDefaultedOrPassedThroughUnsetNeverEmpty() {

        assertThat(COMPOSE_TEXT.lines().filter(line -> !line.stripLeading().startsWith("#")))
                .noneMatch(line -> NEITHER_REQUIRED_NOR_DEFAULTED.matcher(line).find());
    }

    @Test
    void imagesLandOnTheNamedVolumeWhereTheImageKeepsThem() {

        var media = Pattern.compile("(?m)^ENV MEDIA_LOCAL_PATH=(\\S+)$")
                .matcher(read("backend/selfhosted-app/Dockerfile")).results().map(match -> match.group(1)).findFirst()
                .orElseThrow();

        assertThat(service(COMPOSE, "backend").get("volumes")).isEqualTo(List.of("media:" + media));
        assertThat(section(COMPOSE, "volumes")).containsKeys("media", "postgres-data");
    }

    @Test
    void postgresKeepsItsDataInTheNamedVolume() {

        var postgres = service(COMPOSE, "postgres");

        assertThat(postgres.get("image")).isEqualTo("postgres:18");
        assertThat(postgres.get("volumes")).as("postgres:18 keeps PGDATA in /var/lib/postgresql/18/docker")
                .isEqualTo(List.of("postgres-data:/var/lib/postgresql"));
    }

    @Test
    void theMailServerIsConfiguredInTheApplicationWithEachOverlayVariableAnExample() {

        assertThat(COMPOSE_TEXT + TEST_COMPOSE_TEXT).doesNotContain("SPRING_MAIL");
        assertThat(environment(COMPOSE, "backend")).doesNotContainKeys(MAIL_OVERLAY.toArray(String[]::new));
        assertThat(MAIL_OVERLAY)
                .allSatisfy(name -> assertThat(COMPOSE_TEXT).containsPattern("(?m)^ {6}# " + name + ":$"));
    }

    @Test
    void theExampleSettingsListEveryRequiredVariableAndCommentEveryOptionalOne() {

        var example = read("deploy/.env.example");
        var commented = keys(example, "(?m)^# ([A-Z][A-Z0-9_]*)=");

        assertThat(keys(example, "(?m)^([A-Z][A-Z0-9_]*)=")).isEqualTo(names(COMPOSE_TEXT, REQUIRED_BY_COMPOSE));
        assertThat(commented).containsAll(names(COMPOSE_TEXT, DEFAULTED_BY_COMPOSE));
        assertThat(passedThroughUnset()).isNotEmpty().allSatisfy(name -> assertThat(commented).contains(name));
    }

    @Test
    void theEndToEndStackSetsEveryRequiredVariableAndOnlyAddsStandIns() {

        var services = section(TEST_COMPOSE, "services");

        assertThat(keys(read("deploy/test/e2e.env"), "(?m)^([A-Z][A-Z0-9_]*)="))
                .containsAll(names(COMPOSE_TEXT, REQUIRED_BY_COMPOSE));
        assertThat(TEST_COMPOSE.get("name")).isEqualTo("axonia-e2e");
        assertThat(services.keySet()).containsExactlyInAnyOrder("backend", "edge", "backend-issuer-relay",
                "edge-issuer-relay", "issuer", "mailpit", "cimd");
        assertThat(environment(TEST_COMPOSE, "backend").keySet()).containsExactlyInAnyOrder(
                "AXONIA_COMMUNICATION_PRODUCE_INTERVAL", "AXONIA_COMMUNICATION_DRAIN_INTERVAL",
                "AXONIA_MCP_OAUTH_TRUSTED_CLIENT_DOMAINS", "JAVA_TOOL_OPTIONS");
        assertThat(service(TEST_COMPOSE, "edge")).doesNotContainKey("environment");
    }

    @Test
    void bothImagesComeFromThePublishedRegistryUnlessAnInstallationNamesItsOwn() {

        Map<String, Object> chart = new Yaml().load(read("deploy/helm/axonia/values.yaml"));
        var registry = "${AXONIA_IMAGE_REGISTRY:-" + PUBLISHED_REGISTRY + "}";

        assertThat(service(COMPOSE, "backend").get("image")).isEqualTo(registry + "/axonia-backend:" + VERSION);
        assertThat(service(COMPOSE, "edge").get("image")).isEqualTo(registry + "/axonia-edge:" + VERSION);
        assertThat(read("deploy/.env.example"))
                .containsPattern("(?m)^# AXONIA_IMAGE_REGISTRY=" + Pattern.quote(PUBLISHED_REGISTRY) + "$");
        assertThat(imageRepository(chart, "backend")).isEqualTo(PUBLISHED_REGISTRY + "/axonia-backend");
        assertThat(imageRepository(chart, "edge")).isEqualTo(PUBLISHED_REGISTRY + "/axonia-edge");
    }

    private static Set<String> passedThroughUnset() {

        return Stream.of("backend", "edge").flatMap(name -> environment(COMPOSE, name).entrySet().stream())
                .filter(entry -> entry.getValue() == null).map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> requiredByTheBackend() {

        var required = placeholders(REQUIRED_PLACEHOLDER);
        required.addAll(selfHostedVariables(true));
        return required;
    }

    private static Set<String> optionalForTheBackend() {

        var optional = placeholders(OPTIONAL_PLACEHOLDER);
        optional.remove(IMAGE_OWNED);
        optional.addAll(selfHostedVariables(false));
        return optional;
    }

    private static Set<String> placeholders(Pattern pattern) {

        return BACKEND_PROPERTIES.stream().flatMap(path -> read(path).lines())
                .filter(line -> !line.stripLeading().startsWith("#"))
                .flatMap(line -> pattern.matcher(line).results().map(match -> match.group(1)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** {@code SelfHostedProperties}' variables: required are the components with no {@code @DefaultValue}. */
    private static Set<String> selfHostedVariables(boolean required) {

        return leaves(SelfHostedProperties.class, "AXONIA_SELFHOSTED").filter(leaf -> leaf.required() == required)
                .map(Leaf::variable).collect(Collectors.toCollection(TreeSet::new));
    }

    private record Leaf(String variable, boolean required) {
    }

    private static Stream<Leaf> leaves(Class<?> type, String prefix) {

        var components = type.getRecordComponents();
        var parameters = canonicalConstructorParameters(type, components);
        return Stream.iterate(0, i -> i < components.length, i -> i + 1).flatMap(i -> {
            var variable = prefix + "_" + upperSnake(components[i].getName());
            return components[i].getType().isRecord()
                    ? leaves(components[i].getType(), variable)
                    : Stream.of(new Leaf(variable, !parameters[i].isAnnotationPresent(DefaultValue.class)));
        });
    }

    private static Parameter[] canonicalConstructorParameters(Class<?> type, RecordComponent[] components) {

        try {
            return type
                    .getDeclaredConstructor(
                            Stream.of(components).map(RecordComponent::getType).toArray(Class<?>[]::new))
                    .getParameters();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type + " has no canonical constructor", e);
        }
    }

    private static String upperSnake(String camel) {

        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private static Set<String> requiredByTheEdge() {

        var block = Pattern.compile("const REQUIRED = \\[([^\\]]*)]").matcher(read("selfhosted-edge/src/config.ts"));
        assertThat(block.find()).as("REQUIRED in selfhosted-edge/src/config.ts").isTrue();
        return names(block.group(1), Pattern.compile("\"([A-Z][A-Z0-9_]*)\""));
    }

    private static Set<String> names(String text, Pattern pattern) {

        return pattern.matcher(text).results().map(match -> match.group(1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> keys(String text, String regex) {

        return names(text, Pattern.compile(regex));
    }

    @SuppressWarnings("unchecked")
    private static String healthcheck(String name) {

        return String.join(" ", (List<String>) ((Map<?, ?>) service(COMPOSE, name).get("healthcheck")).get("test"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> environment(Map<String, Object> compose, String name) {

        return (Map<String, Object>) service(compose, name).get("environment");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(Map<String, Object> compose, String name) {

        return (Map<String, Object>) section(compose, "services").get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> compose, String name) {

        return (Map<String, Object>) compose.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Object imageRepository(Map<String, Object> values, String component) {

        return ((Map<String, Object>) section(values, component).get("image")).get("repository");
    }

    private static String read(String path) {

        try {
            return Files.readString(RepositoryRoot.file(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
