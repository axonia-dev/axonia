package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code deploy/dev/compose.yaml}, the services the Self-hosted DEV run connects to, held to the DEV profile that
 * connects to them and to the images an installation and the end-to-end run use. Nothing starts it in a build, so a
 * port, an issuer or an audience that drifted from the profile would show only as a developer's run that cannot sign
 * in.
 */
class SelfHostedDevComposeTest {

    /** {@code 127.0.0.1:${VARIABLE:-default}:container}: published on this machine alone, moved by a variable. */
    private static final Pattern PUBLISHED = Pattern.compile("127\\.0\\.0\\.1:\\$\\{[A-Z_]+:-(\\d+)}:(\\d+)");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Map<String, Object> DEV = services("deploy/dev/compose.yaml");

    private static final Properties PROFILE = properties(
            "backend/selfhosted-app/src/main/resources/application-DEV.properties");

    @Test
    void runsThePostgresMajorAnInstallationRuns() {

        assertThat(image(DEV, "postgres")).isEqualTo(image(services("deploy/compose.yaml"), "postgres"));
    }

    @Test
    void runsTheStandInsTheEndToEndRunPins() {

        var endToEnd = services("deploy/compose.test.yaml");

        assertThat(image(DEV, "issuer")).isEqualTo(image(endToEnd, "issuer"));
        assertThat(image(DEV, "mailpit")).isEqualTo(image(endToEnd, "mailpit"));
    }

    @Test
    void publishesEveryPortOnThisMachineAloneUnderAVariable() {

        var ports = DEV.keySet().stream().flatMap(name -> ports(name).stream()).toList();

        assertThat(ports).isNotEmpty().allMatch(port -> PUBLISHED.matcher(port).matches());
    }

    @Test
    void servesTheDatabaseTheDevProfileMigrates() {

        var environment = section(service(DEV, "postgres"), "environment");

        assertThat(PROFILE.getProperty("spring.flyway.url")).isEqualTo(
                "jdbc:postgresql://localhost:" + hostPort("postgres", "5432") + "/" + environment.get("POSTGRES_DB"));
        assertThat(PROFILE.getProperty("spring.flyway.user")).isEqualTo(environment.get("POSTGRES_USER"));
        assertThat(PROFILE.getProperty("spring.flyway.password")).isEqualTo(environment.get("POSTGRES_PASSWORD"));
    }

    /**
     * Boot's Compose support would otherwise connect the run to the image it recognises as the owner the container was
     * created with, bypassing every row-level policy the app role is held to.
     */
    @Test
    void keepsTheDatabaseConnectionToTheDevProfile() {

        assertThat(section(service(DEV, "postgres"), "labels")).containsEntry("org.springframework.boot.ignore",
                "true");
    }

    @Test
    void issuesTokensAtTheIssuerAndForTheAudienceTheDevProfileTrusts() {

        var callback = issuerConfiguration().path("tokenCallbacks").path(0);

        assertThat(PROFILE.getProperty("axonia.selfhosted.oidc.issuer-uri")).isEqualTo(
                "http://localhost:" + hostPort("issuer", "8080") + "/" + callback.path("issuerId").asString());
        assertThat(callback.path("requestMappings").path(0).path("claims").path("aud").path(0).asString())
                .isEqualTo(PROFILE.getProperty("axonia.selfhosted.oidc.audience"));
    }

    @Test
    void signsInWhoeverFillsItsFormWithTheClaimsTypedThere() {

        assertThat(issuerConfiguration().path("interactiveLogin").asBoolean()).isTrue();
    }

    private static String hostPort(String service, String containerPort) {

        return ports(service).stream().map(PUBLISHED::matcher).filter(matcher -> matcher.matches())
                .filter(matcher -> matcher.group(2).equals(containerPort)).map(matcher -> matcher.group(1)).findFirst()
                .orElseThrow();
    }

    private static JsonNode issuerConfiguration() {

        return JSON.readTree(String.valueOf(section(service(DEV, "issuer"), "environment").get("JSON_CONFIG")));
    }

    @SuppressWarnings("unchecked")
    private static List<String> ports(String service) {

        return (List<String>) service(DEV, service).getOrDefault("ports", List.of());
    }

    private static String image(Map<String, Object> services, String name) {

        return String.valueOf(service(services, name).get("image"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(Map<String, Object> services, String name) {

        return (Map<String, Object>) services.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> map, String key) {

        return (Map<String, Object>) map.get(key);
    }

    private static Map<String, Object> services(String path) {

        Map<String, Object> compose = new Yaml().load(read(path));
        return section(compose, "services");
    }

    private static Properties properties(String path) {

        var properties = new Properties();
        try (var in = Files.newInputStream(RepositoryRoot.file(path))) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    private static String read(String path) {

        try {
            return Files.readString(RepositoryRoot.file(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
