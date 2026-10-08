package dev.axonia.roundup.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.DataCategory;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * One flag per tenant, keyed on an organization and naming nobody: {@code roundup_settings} holds no personal data at
 * all, so it is declared with an empty {@code personal} list and every column under {@code notPersonal}, the same shape
 * {@code CommunicationPersonalDataFootprint.communicationProductionWatermark()} uses for the same reason. It is claimed
 * under {@link DataCategory#WHEN_AXONIA_CONTACTS_YOU} because the row exists solely to drive outbound mail, not because
 * anything in it is personal.
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
