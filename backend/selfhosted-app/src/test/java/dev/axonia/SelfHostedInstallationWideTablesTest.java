package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every table the Self-hosted chain creates either has row-level security or is named here as describing the
 * installation rather than a tenant's data, with the reason. The open chain's tables are held by core's tests.
 */
class SelfHostedInstallationWideTablesTest {

    static final Map<String, String> INSTALLATION_WIDE = Map.of("selfhosted_settings",
            "the Organization the installation founded, read before any Organization is known", "mail_server_settings",
            "the one mail server every Organization's mail leaves through", "installation_settings_change",
            "the history of the installation's own settings");

    private static final Path CHAIN = Path.of("src/main/resources/db/selfhosted");

    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE (?:public\\.)?(\\w+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ROW_LEVEL_SECURITY = Pattern
            .compile("ALTER TABLE (?:ONLY )?(?:public\\.)?(\\w+) ENABLE ROW LEVEL SECURITY", Pattern.CASE_INSENSITIVE);

    @Test
    void everyTableOfTheSelfHostedChainHasRowLevelSecurityOrIsInstallationWide() {

        // given
        var sql = chain();
        var created = names(CREATE_TABLE, sql);
        var secured = names(ROW_LEVEL_SECURITY, sql);

        // then
        assertThat(created).isNotEmpty()
                .allSatisfy(table -> assertThat(secured.contains(table) || INSTALLATION_WIDE.containsKey(table))
                        .as("%s has no row-level security; enable it, or name it in INSTALLATION_WIDE with the reason",
                                table)
                        .isTrue());
        assertThat(INSTALLATION_WIDE.keySet()).as("installation-wide tables the chain no longer creates")
                .allSatisfy(table -> assertThat(created).contains(table))
                .allSatisfy(table -> assertThat(secured).doesNotContain(table));
    }

    private static Set<String> names(Pattern pattern, String sql) {

        return pattern.matcher(sql).results().map(match -> match.group(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static String chain() {

        try (var files = Files.list(CHAIN)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".sql")).sorted().map(file -> {
                try {
                    return Files.readString(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
