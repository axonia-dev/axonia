package dev.axonia;

import java.util.List;

/**
 * What personal data one bounded context holds. Every module owning tables implements this inside its {@code internal}
 * package, next to the tables it already maintains. The union of every module's declaration is the platform's record of
 * processing, derived from the schema rather than maintained beside it.
 *
 * <p>
 * The declaration is enforced, not documentary: {@code PersonalDataFootprintTest} asserts that every table in the
 * public schema is claimed by exactly one footprint and that every column of a claimed table is declared. Adding a
 * column without saying what erasure and export do with it fails the build.
 */
public interface PersonalDataFootprint {

    List<TableFootprint> tables();

    /**
     * This module's declaration for one of its own tables, so a {@link PersonalDataExporter} can build its projection
     * from the same manifest that governs erasure rather than restating the column list beside it.
     */
    default TableFootprint table(String name) {

        return tables().stream().filter(table -> table.table().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        getClass().getSimpleName() + " declares no table named '" + name + "'"));
    }
}
