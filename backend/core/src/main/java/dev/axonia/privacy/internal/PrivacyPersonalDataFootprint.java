package dev.axonia.privacy.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.DataCategory;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * {@code erasure_receipt} holds no personal column at all: it names nobody, only the unlinkable surrogate an erasure's
 * surviving rows now carry. That is the whole point of this table, enforced rather than asserted in prose:
 * {@link PrivacyPersonalDataFootprintTest} asserts that every {@link TableFootprint} this class returns has an empty
 * {@code personal()} list, so a column added later to {@code erasure_receipt} can only ever be declared here as
 * {@code notPersonal} - declaring it personal, with a {@link dev.axonia.Disposition}, fails the build just as surely as
 * not declaring it at all. {@code PersonalDataFootprintTest} alone would not catch that: it only requires a column to
 * be declared in one bucket or the other, and declaring a name-bearing column personal passes it just as green.
 */
@Component
public class PrivacyPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(erasureReceipt());
    }

    private TableFootprint erasureReceipt() {

        return TableFootprint.builder().table("erasure_receipt").category(DataCategory.RIGHTS_YOU_EXERCISED)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "surrogate_id", "erased_at",
                        "deleted_row_count", "cleared_row_count", "anonymised_row_count", "media_residue"))
                .build();
    }
}
