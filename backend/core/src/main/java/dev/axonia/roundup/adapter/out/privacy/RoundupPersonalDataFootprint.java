package dev.axonia.roundup.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * Neither roundup table names anybody: {@code roundup_settings} is one flag per tenant and {@code roundup_thumbnail} an
 * image composed from a community asset, so each is declared with an empty {@code personal} list and every column under
 * {@code notPersonal}. Both are claimed under {@link DataCategory#WHEN_AXONIA_CONTACTS_YOU} because they exist solely
 * to drive outbound mail, not because anything in them is personal.
 */
@Component
public class RoundupPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("roundup_settings")
                .category(DataCategory.WHEN_AXONIA_CONTACTS_YOU).personal(List.of())
                .notPersonal(Set.of("organization_id", "enabled", "created_at", "updated_at", "version")).build(),
                TableFootprint.builder().table("roundup_thumbnail").category(DataCategory.WHEN_AXONIA_CONTACTS_YOU)
                        .personal(List.of()).notPersonal(Set.of("id", "organization_id", "community_id", "hero_key",
                                "slice_index", "accent_hex", "media_key", "created_at"))
                        .build());
    }
}
