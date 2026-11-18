package dev.axonia.roundup.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * {@code roundup_settings} is one flag per tenant and names nobody, so it is declared with an empty {@code personal}
 * list and every column under {@code notPersonal}. It is claimed under {@link DataCategory#WHEN_AXONIA_CONTACTS_YOU}
 * because it exists solely to drive outbound mail, not because anything in it is personal.
 */
@Component
public class RoundupPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("roundup_settings")
                .category(DataCategory.WHEN_AXONIA_CONTACTS_YOU).personal(List.of())
                .notPersonal(Set.of("organization_id", "enabled", "created_at", "updated_at", "version")).build());
    }
}
