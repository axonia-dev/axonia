package dev.axonia.access.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.ExportTreatment;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * A role is a named bundle of permissions, not a person, so {@code role} and {@code role_permission} carry nothing
 * personal. {@code role_binding} is where a role meets a subject: {@code subject_id} names the person or group bound,
 * and {@code subject_label} is a denormalised display copy of that same subject, so both are erased together with the
 * binding.
 *
 * <p>
 * {@code subject_label} is {@link ExportTreatment#WITHHELD} rather than exported as itself, and the reason is that it
 * is not reliably a label at all for a {@code USER} binding. It is whatever {@code JwtClaimsReader} resolved as a
 * display name when the person joined, and that chain ends {@code name} then the joined name parts then
 * {@code preferred_username} then <em>the raw subject</em>. An identity provider that asserts no name claim therefore
 * writes the subject itself into this column, permanently, and an export printing it would hand back the one identifier
 * this whole projection exists to withhold; a {@code USER} row is named instead by resolving {@code subject_id} to a
 * current display name and address, which is both fresher than this copy and never a key. A {@code GROUP} binding
 * carries no such risk - {@code subject_label} there is the admin-chosen IdP group name, never fed by
 * {@code JwtClaimsReader} - so {@link AccessPersonalDataExporter} derives it into the {@code group} export label
 * instead of withholding it.
 */
@Component
public class AccessPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(roleBinding(), role(), rolePermission());
    }

    private TableFootprint roleBinding() {

        return TableFootprint.builder().table("role_binding").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("subject_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                        new ColumnFootprint("subject_label", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW,
                                ExportTreatment.WITHHELD)))
                .notPersonal(Set.of("id", "organization_id", "subject_type", "role_id", "created_at", "updated_at",
                        "version"))
                .exportedContext(Set.of("subject_type", "created_at", "updated_at"))
                .exportedLabels(Set.of("role", "group")).build();
    }

    private TableFootprint role() {

        return TableFootprint.builder().table("role").category(DataCategory.ACCESS_AND_SECURITY).personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "name", "created_at", "updated_at", "version",
                        "description", "system", "slug"))
                .build();
    }

    private TableFootprint rolePermission() {

        return TableFootprint.builder().table("role_permission").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of()).notPersonal(Set.of("role_id", "permission")).build();
    }
}
