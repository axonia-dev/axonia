package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** The versioned migrations one classpath folder holds, SQL and Java alike, anywhere under it, as Flyway scans it. */
public final class MigrationFiles {

    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.(sql|class)");

    private MigrationFiles() {
    }

    /** The versions anywhere under {@code folder}, ascending; none when the folder is absent. */
    public static List<Integer> versionsIn(String folder) {

        var versions = new ArrayList<Integer>();
        try {
            for (var resource : new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + folder + "/**/*")) {
                var name = VERSIONED.matcher(Objects.requireNonNull(resource.getFilename()));
                if (name.matches()) {
                    versions.add(Integer.valueOf(name.group(1)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Collections.sort(versions);
        return versions;
    }
}
