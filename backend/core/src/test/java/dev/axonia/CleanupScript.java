package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * The tables a post-class cleanup script empties, and the tables the running schema holds, so each edition's test holds
 * its script to its schema: every table named, none named twice.
 */
public final class CleanupScript {

    /** Flyway's own record, which a cleanup must never empty. */
    public static final String FLYWAY_HISTORY = "flyway_schema_history";

    private static final Pattern SQL_COMMENT = Pattern.compile("--[^\\n]*");

    private static final Pattern TRUNCATE = Pattern.compile("(?is)\\bTRUNCATE\\s+(?:TABLE\\s+)?(.*?)"
            + "(?:\\s+(?:RESTART|CONTINUE)\\s+IDENTITY)?(?:\\s+(?:CASCADE|RESTRICT))?\\s*;");

    private static final Pattern DELETE = Pattern.compile("(?i)\\bDELETE\\s+FROM\\s+([\\w.]+)\\s*;");

    private CleanupScript() {
    }

    /**
     * Every table the script truncates or deletes every row from, once per mention, so a table named twice shows twice.
     * A {@code DELETE} with a {@code WHERE} empties nothing, so it names no table.
     */
    public static List<String> tablesNamedBy(String resource) {

        var script = SQL_COMMENT.matcher(read(resource)).replaceAll(" ");
        var named = new ArrayList<String>();
        TRUNCATE.matcher(script).results().forEach(truncate -> named.addAll(List.of(truncate.group(1).split(","))));
        DELETE.matcher(script).results().forEach(delete -> named.add(delete.group(1)));
        return named.stream().map(CleanupScript::bareName).sorted().toList();
    }

    /** The schema's tables, all but {@link #FLYWAY_HISTORY}. */
    public static List<String> liveTables(NamedParameterJdbcTemplate jdbc) {

        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_type = 'BASE TABLE' AND table_name <> :history ORDER BY table_name",
                Map.of("history", FLYWAY_HISTORY), String.class);
    }

    private static String bareName(String table) {

        return table.strip().replaceFirst("(?i)^public\\.", "").toLowerCase(Locale.ROOT);
    }

    private static String read(String resource) {

        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
