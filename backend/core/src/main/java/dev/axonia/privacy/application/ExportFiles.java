package dev.axonia.privacy.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import dev.axonia.shared.api.personaldata.ExportFragment;
import lombok.experimental.UtilityClass;

/**
 * One file per file name, however many modules contribute to it. A person's identity is held by two modules - identity
 * and another module - and still reads as one {@code identity.json}: the file a person downloads is named for what it
 * holds, never for how the code happens to be divided.
 *
 * <p>
 * Files come out in file-name order, the archive's own, so {@code index.html} reads the same whichever order the
 * exporter beans are wired in. A merged file's tables sit in a {@link HashMap}, the same order one exporter building
 * the whole file produced. Two contributors naming the same table is refused rather than resolved: either answer would
 * silently drop somebody's rows from their own export.
 */
@UtilityClass
class ExportFiles {

    List<ExportFragment> mergedByFileName(List<ExportFragment> fragments) {

        var byFileName = new TreeMap<String, ExportFragment>();
        fragments.forEach(fragment -> byFileName.merge(fragment.fileName(), fragment, ExportFiles::merged));
        return List.copyOf(byFileName.values());
    }

    private ExportFragment merged(ExportFragment first, ExportFragment second) {

        var payload = new HashMap<>(first.payload());
        second.payload().forEach((table, rows) -> {
            if (payload.putIfAbsent(table, rows) != null) {
                throw new IllegalStateException(
                        "Two exporters both wrote table '%s' to '%s'".formatted(table, first.fileName()));
            }
        });
        var mediaKeys = new ArrayList<>(first.mediaKeys());
        mediaKeys.addAll(second.mediaKeys());
        return new ExportFragment(first.fileName(), payload, mediaKeys);
    }
}
