package dev.axonia.organization.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * {@code organization} is the tenant record itself, not a member of it: name, kind, its lifecycle tombstone
 * ({@code closed_at}, {@code closed_by}), and its communications kill switch. {@code closed_by} holds the identity
 * provider's subject of the axonia operator who closed the tenant - an operator is a person too, so the column is
 * personal data. It is not, however, a member's personal data: no member erasure reaches an organization tombstone, and
 * the row outlives every member of it, so nothing here is ever exported on their behalf either. {@code closed_by} is
 * therefore {@code RETAIN}: a fact about who this platform holds accountable for closing a tenant, kept outside the
 * member data-subject regime entirely. {@code communications_enabled} is a tenant-wide setting, nobody's personal data.
 */
@Component
public class OrganizationPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("organization").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("closed_by", Classification.SUBJECT, Disposition.RETAIN)))
                .notPersonal(Set.of("id", "name", "kind", "lifecycle_state", "closed_at", "created_at", "updated_at",
                        "communications_enabled"))
                .build());
    }
}
