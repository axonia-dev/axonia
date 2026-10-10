package dev.axonia.selfhosted.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * {@code selfhosted_settings} records which Organization the installation founded, the name of the claim accounts are
 * matched on, and when: facts about the installation, nobody's personal data.
 */
@Component
class SelfHostedPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("selfhosted_settings").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of())
                .notPersonal(Set.of("installation", "organization_id", "subject_claim", "founded_at")).build());
    }
}
