package dev.axonia.installation.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * The mail server row describes the installation; the relay account and the sender are the company's, nobody's personal
 * data. The history's actor is a person, cleared on erasure so what changed stays recorded.
 */
@Component
class InstallationPersonalDataFootprint implements PersonalDataFootprint {

    static final String MAIL_SERVER_SETTINGS = "mail_server_settings";

    static final String SETTINGS_CHANGE = "installation_settings_change";

    static final String ACTOR = "actor_user_id";

    @Override
    public List<TableFootprint> tables() {

        return List.of(
                TableFootprint.builder().table(MAIL_SERVER_SETTINGS).category(DataCategory.ACCESS_AND_SECURITY)
                        .personal(List.of())
                        .notPersonal(Set.of("installation", "host", "port", "security", "username", "password_secret",
                                "from_address", "from_name", "tested_fingerprint", "revision", "last_failure_at",
                                "last_failure_reason", "updated_at"))
                        .build(),
                TableFootprint.builder().table(SETTINGS_CHANGE).category(DataCategory.ACCESS_AND_SECURITY)
                        .personal(List.of(new ColumnFootprint(ACTOR, Classification.SUBJECT, Disposition.CLEAR)))
                        .notPersonal(Set.of("id", "occurred_at", "integration", "changes"))
                        .exportedContext(Set.of("occurred_at", "integration", "changes")).build());
    }
}
